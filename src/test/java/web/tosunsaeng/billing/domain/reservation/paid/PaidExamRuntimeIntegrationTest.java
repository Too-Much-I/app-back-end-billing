package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class PaidExamRuntimeIntegrationTest extends PaidReservationTestSupport {
    PaidAttemptEventService events() { return new PaidAttemptEventService(tx, mongo, payment, properties, "test", clock); }
    PaidExamRouter router() { return new PaidExamRouter(mongo, runtime(), "SANDBOX", "test"); }
    PaidExamQueryReader query() { return new PaidExamQueryReader(mongo, "test", "SANDBOX", ISSUER); }
    AttemptGroupStatusEvent event(String group, String session, AttemptGroupEventTarget status) {
        return new AttemptGroupStatusEvent(UUID.randomUUID().toString(), "AttemptGroupStatusChanged", 1, "learning-core", NOW,
                USER, group, session, status, null, null, "a".repeat(64));
    }
    String confirmed() {
        purchase(); var result = runtime().reserve(command()); runtime().confirm(confirm(command(), result));
        return result.responseSnapshot().getString("attemptGroupId");
    }
    @Test void paidStatusEventCommitsUsageGuardAndInboxAndDuplicateDoesNotAdvance() {
        String group = confirmed(); var e = event(group, "session", AttemptGroupEventTarget.COMPLETED);
        assertThat(events().process(e)).isEqualTo(AttemptGroupEventOutcome.APPLIED);
        assertThat(events().process(e)).isEqualTo(AttemptGroupEventOutcome.DUPLICATE);
        assertThat(db.getCollection("attempt_groups").find().first().getString("status")).isEqualTo("COMPLETED");
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().getString("currentGroupId")).isNull();
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("sourceKind", "EXAM_EVENT"))).isOne();
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isOne();
        var conflict = new AttemptGroupStatusEvent(e.eventId(), e.eventType(), 1, e.producer(), NOW, USER, group, "session", e.targetStatus(), null, null, "b".repeat(64));
        error(() -> events().process(conflict), "EVENT_ID_CONFLICT");
    }
    @Test void lateOldTrialCompletionDoesNotReleaseNewPaidGuard() {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd); runtime().confirm(confirm(cmd, result));
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        assertThat(events().process(event(old, "old", AttemptGroupEventTarget.COMPLETED))).isEqualTo(AttemptGroupEventOutcome.APPLIED);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isOne();
    }
    @Test void completionDuringNewReserveKeepsPendingOperationAndConfirmStillSucceeds() {
        String group = confirmed();
        var context = new RestartContext(group, "session", RestartContext.StartReason.NEW_AFTER_SUBMISSION);
        var req = new ReserveRequest(USER, "new-session", "next-mock", null, null, null, context);
        var cmd = new ReserveCommand(UUID.randomUUID().toString(), USER, "new-session", "next-mock", null, null, null, new ReservePayloadHasher().hash(req), context);
        var next = runtime().reserve(cmd); Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        events().process(event(group, "session", AttemptGroupEventTarget.COMPLETED));
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        runtime().confirm(confirm(cmd, next));
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().getString("currentSessionId")).isEqualTo("new-session");
    }
    @Test void wrongOwnerAndWithdrawalDenyWithoutInboxOrUsage() {
        String group = confirmed(); var e = event(group, "session", AttemptGroupEventTarget.COMPLETED);
        var wrong = new AttemptGroupStatusEvent(e.eventId(), e.eventType(), 1, e.producer(), NOW, UUID.randomUUID().toString(), group, "session", e.targetStatus(), null, null, e.payloadDigest());
        error(() -> events().process(wrong), "EVENT_TARGET_CONFLICT");
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$set", new Document("state", "WITHDRAWN")));
        error(() -> events().process(e), "EVENT_TARGET_CONFLICT");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("sourceKind", "EXAM_EVENT"))).isZero();
    }
    @Test void usageWriteFailureRollsBackInboxAndCompletion() {
        String group = confirmed();
        db.runCommand(new Document("collMod", "payment_ledger").append("validator", new Document("type", new Document("$ne", "EXAM_STATUS"))));
        error(() -> events().process(event(group, "session", AttemptGroupEventTarget.COMPLETED)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
        assertThat(db.getCollection("attempt_groups").find().first().getString("status")).isEqualTo("OPEN");
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().getString("currentGroupId")).isEqualTo(group);
    }
    @Test void concurrentDuplicateAndUnknownCommitHaveOneUsageEffect() throws Exception {
        String group = confirmed(); var e = event(group, "session", AttemptGroupEventTarget.COMPLETED);
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> events().process(e)); var b = pool.submit(() -> events().process(e));
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).contains(AttemptGroupEventOutcome.APPLIED);
        }
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("sourceKind", "EXAM_EVENT"))).isOne();
    }
    @Test void refundAfterConfirmStillAllowsExactCompletion() {
        String group = confirmed();
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")));
        assertThat(events().process(event(group, "session", AttemptGroupEventTarget.COMPLETED))).isEqualTo(AttemptGroupEventOutcome.APPLIED);
    }
    @Test void routerPreservesLegacyCommandAndSourceReservationRouting() {
        purchase(); assertThat(router().handles(USER, OP)).isTrue();
        db.getCollection("idempotency_commands").insertOne(new Document("callerService", "LEARNING_CORE").append("userId", USER).append("operationId", OP));
        assertThat(router().handles(USER, OP)).isFalse();
        assertThat(router().handles(UUID.randomUUID().toString(), OP)).isFalse();
    }
    @Test void expiryRoutesPaidAndNeverRevertsConfirmed() {
        purchase(); var result = runtime().reserve(command()); String id = result.responseSnapshot().getString("reservationId");
        assertThat(router().handlesReservation(id)).isTrue();
        assertThat(router().expire(id)).contains(false);
        var later = new PaidExamRouter(mongo, runtime(Clock.fixed(NOW.plusSeconds(300), ZoneOffset.UTC)), "SANDBOX", "test");
        assertThat(later.expire(id)).contains(true);
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus().name()).isEqualTo("EXPIRED");
        assertThat(later.expire("missing")).isEmpty();
    }
    @Test void pendingAdmissionAndMemberRefreshAreReadOnly() {
        purchase(); db.getCollection("payment_accounts").updateOne(new Document(), new Document("$unset", new Document("memberEvidence", "")));
        error(() -> runtime().reserve(command()), "MEMBER_EVIDENCE_PENDING");
        var before = db.getCollection(PaidExamStore.RECEIPTS).find().first();
        var available = snapshots.execute(() -> query().read(USER, NOW, PaidQueryResponse.empty(false), List.of()));
        assertThat(available.action()).isEqualTo(Action.PENDING);
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).find().first()).isEqualTo(before);
        accounts.getOrCreate(evidence()); var result = router().reserve(command());
        assertThat(result.entitlementSource().name()).isEqualTo("SUBSCRIPTION");
        assertThat(snapshots.execute(() -> query().read(USER, NOW, PaidQueryResponse.empty(false), List.of())).action()).isEqualTo(Action.PENDING);
    }
    @Test void integratedReaderPaidFirstAndRestoredQuantityWithoutWrites() {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd); runtime().confirm(confirm(cmd, result));
        var benefit = new Benefit("FREE_EXAM_ONCE", "test", "EXAM", 0, Eligibility.VERIFIED, Usage.INCOMPLETE, Action.BLOCKED, true,
                Action.ALLOWED, List.of(), List.of(new Group(old, web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup.Status.OPEN, true, Action.ALLOWED, List.of())));
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("schemaVersion", 4L)));
        snapshots.execute(() -> {
            var benefits = query().augment(USER, List.of(benefit));
            assertThat(benefits.getFirst().availableQuantity()).isEqualTo(1);
            assertThat(benefits.getFirst().retake()).isEqualTo(Action.BLOCKED);
            var paid = new PaidEntitlementReader(mongo, properties).read(USER, NOW);
            assertThat(query().read(USER, NOW, paid, benefits).preferredSource()).isEqualTo(ExamAvailability.Source.PAID);
            return true;
        });
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
    }


    @Test void legacyPhoneSourceUsesExactBoundedFenceAndTargetCannotReportOldSession() {
        purchase(); String group = oldTrial(); String target = UUID.randomUUID().toString(); accounts.getOrCreate(target, NOW.plusSeconds(60));
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set", new Document("userId", target).append("sessionOwnerEpoch", 2L)));
        var event = event(group, "old", AttemptGroupEventTarget.COMPLETED);
        error(() -> events().process(event), "EVENT_TARGET_CONFLICT");
        var targetEvent = new AttemptGroupStatusEvent(event.eventId(), event.eventType(), 1, event.producer(), NOW, target, group, "old", event.targetStatus(), null, null, event.payloadDigest());
        error(() -> events().process(targetEvent), "EVENT_TARGET_CONFLICT");
        db.getCollection("subject_owner_rebinds").insertOne(new Document("subjectRefId", "subject").append("sourceUserId", USER)
                .append("attemptGroupId", group).append("sessionId", "old").append("cleanupState", "WAITING_TERMINAL")
                .append("appliedAt", Date.from(NOW.plusMillis(1))).append("legacyFenceExpiresAt", Date.from(NOW.plusSeconds(60))));
        assertThat(events().process(event)).isEqualTo(AttemptGroupEventOutcome.APPLIED);
        assertThat(events().process(event)).isEqualTo(AttemptGroupEventOutcome.DUPLICATE);
        assertThat(db.getCollection("subject_owner_rebinds").find().first().getString("cleanupState")).isEqualTo("DUE");
    }
    @Test void currentTerminalNeverReopensAndUnconfirmedEventIsRetryable() {
        purchase(); var reserved = runtime().reserve(command());
        error(() -> events().process(event(reserved.responseSnapshot().getString("attemptGroupId"), "session", AttemptGroupEventTarget.COMPLETED)), "ATTEMPT_PROJECTION_NOT_READY");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
        runtime().confirm(confirm(command(), reserved));
        String group = reserved.responseSnapshot().getString("attemptGroupId");
        events().process(event(group, "session", AttemptGroupEventTarget.COMPLETED));
        assertThat(events().process(event(group, "session", AttemptGroupEventTarget.RETAKE_AVAILABLE))).isEqualTo(AttemptGroupEventOutcome.STALE);
        assertThat(db.getCollection("attempt_groups").find().first().getString("status")).isEqualTo("COMPLETED");
    }
    @Test void sourceAwareLifecycleCannotFallBackWhenRuntimeOff() {
        purchase(); var reserved = runtime().reserve(command()); var legacy = lifecycle(); legacy.setPaidRouter(null);
        error(() -> legacy.confirm(confirm(command(), reserved)), "BILLING_TEMPORARILY_UNAVAILABLE");
        error(() -> legacy.expire(reserved.responseSnapshot().getString("reservationId"), NOW.plusSeconds(301)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection("reservations").find().first().getString("status")).isEqualTo("RESERVED");
    }
    @Test void queryWithdrawalBlocksAndMalformedEvidenceFailsClosed() {
        purchase(); var paid = new PaidQueryResponse(PaidQueryResponse.Status.READY, true, "PREMIUM_1D", NOW.plusSeconds(1), NOW.plusSeconds(1), null, false);
        db.getCollection("payment_accounts").updateOne(new Document(), new Document("$set", new Document("memberEvidence.issuer", "https://wrong.test")));
        assertThatThrownBy(() -> snapshots.execute(() -> query().read(USER, NOW, paid, List.of())))
                .isInstanceOf(web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.class);
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$set", new Document("state", "WITHDRAWN")));
        assertThat(snapshots.execute(() -> query().read(USER, NOW, paid, List.of())).action()).isEqualTo(Action.BLOCKED);
    }

    @Test void consumedRestorationIsVisibleAsFreeRetakeWithoutRemainingUnits() {
        accounts.getOrCreate(evidence());
        tx.execute(c -> { c.grants().insert(web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant.restoredTrial(
                "restored", "test", USER, "original", 1L, NOW)); return true; });
        var reserved = runtime().reserve(command()); runtime().confirm(confirm(command(), reserved));
        var benefit = new Benefit("FREE_EXAM_ONCE", "test", "EXAM", 0, Eligibility.NOT_ELIGIBLE, Usage.NOT_STARTED,
                Action.BLOCKED, false, Action.BLOCKED, List.of(), List.of());
        snapshots.execute(() -> {
            var benefits = query().augment(USER, List.of(benefit));
            assertThat(benefits.getFirst().availableQuantity()).isZero();
            assertThat(benefits.getFirst().retake()).isEqualTo(Action.ALLOWED);
            assertThat(benefits.getFirst().hasInProgress()).isTrue();
            assertThat(benefits.getFirst().attemptGroups()).hasSize(1);
            assertThat(query().read(USER, NOW, PaidQueryResponse.empty(false), benefits).preferredSource()).isEqualTo(ExamAvailability.Source.FREE_RETAKE);
            return true;
        });
    }
    ReservationLifecycleService lifecycle() {
        var result = new ReservationLifecycleService(
                new web.tosunsaeng.billing.domain.reservation.repository.ReservationRepository(mongo),
                new web.tosunsaeng.billing.domain.reservation.repository.ReservationAllocationRepository(mongo),
                new web.tosunsaeng.billing.domain.entitlement.repository.EntitlementGrantRepository(mongo),
                new web.tosunsaeng.billing.domain.entitlement.repository.EntitlementLedgerRepository(mongo),
                new web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository(mongo),
                new web.tosunsaeng.billing.domain.attempt.repository.AttemptSessionRepository(mongo),
                new web.tosunsaeng.billing.domain.reservation.repository.IdempotencyCommandRepository(mongo),
                org.mockito.Mockito.mock(web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor.class),
                new web.tosunsaeng.billing.domain.reservation.config.ReservationProperties(),
                org.mockito.Mockito.mock(ReservationLifecycleMetrics.class), clock,
                new web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository(mongo));
        result.setPaidRouter(router()); result.setMemberRecovery(service); return result;
    }
    org.springframework.test.web.servlet.MockMvc http() {
        var controller = new web.tosunsaeng.billing.domain.reservation.api.ReservationController(
                new web.tosunsaeng.billing.domain.reservation.api.support.ReserveRequestDecoder(),
                new web.tosunsaeng.billing.domain.reservation.api.support.IdempotencyKeyParser(),
                org.mockito.Mockito.mock(ReserveService.class),
                new web.tosunsaeng.billing.domain.reservation.api.support.LifecycleRequestDecoder(),
                new web.tosunsaeng.billing.domain.reservation.api.support.ReservationIdParser(), lifecycle(),
                org.mockito.Mockito.mock(PhoneContinuationService.class),
                new web.tosunsaeng.billing.domain.reservation.converter.ReservationConverter(new ReservePayloadHasher(), new LifecyclePayloadHasher()));
        controller.setPaidRouter(router());
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var advice = new web.tosunsaeng.billing.global.exception.InternalApiExceptionHandler(
                beans.getBeanProvider(web.tosunsaeng.billing.domain.eligibility.trial.application.TrialEligibilityMetrics.class),
                beans.getBeanProvider(ReserveMetrics.class),
                beans.getBeanProvider(web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventMetrics.class),
                beans.getBeanProvider(web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindMetrics.class));
        return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice)
                .addFilters(web.tosunsaeng.billing.global.config.security.PaidRuntimeHttpSupport.filters(clock)).build();
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path, String body) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content(body)
                .header("Authorization", web.tosunsaeng.billing.global.config.security.PaidRuntimeHttpSupport.bearer(clock, "reservations"))
                .with(r -> { r.setLocalPort(8444); return r; });
    }
    @Test void signedHttpReserveConfirmBodyStatusAndReplayUseRealTransactions() throws Exception {
        purchase(); var mvc = http(); var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String body = new Document("userId", USER).append("sessionId", "session").append("mockExamId", "mock").toJson();
        var response = mvc.perform(post("/internal/v1/reservations", body).header("Idempotency-Key", OP))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.entitlementSource").value("SUBSCRIPTION"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("reservationId").asText();
        String confirm = new Document("userId", USER).append("sessionId", "session").append("sessionCommittedAt", "2026-10-14T00:00:00.000Z").toJson();
        mvc.perform(post("/internal/v1/reservations/" + id + "/confirm", confirm).header("Idempotency-Key", OP))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(post("/internal/v1/reservations/status", new Document("userId", USER).append("operationId", OP).toJson()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reservationStatus").value("CONFIRMED"));
        mvc.perform(post("/internal/v1/reservations", body).header("Idempotency-Key", OP))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json(response));
        assertThat(db.getCollection("attempt_groups").countDocuments()).isOne();
    }
    @Test void httpMemberWaitPreserves503AndAuthenticationFailuresDoNotTouchReceipts() throws Exception {
        purchase(); db.getCollection("payment_accounts").updateOne(new Document(), new Document("$unset", new Document("memberEvidence", "")));
        var mvc = http(); String body = new Document("userId", USER).append("sessionId", "session").append("mockExamId", "mock").toJson();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/reservations")
                        .contentType("application/json").content(body).header("Idempotency-Key", OP).with(r -> { r.setLocalPort(8444); return r; }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(post("/internal/v1/reservations?", body).header("Idempotency-Key", OP).with(r -> { r.setQueryString(""); return r; }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isZero();
        mvc.perform(post("/internal/v1/reservations", body).header("Idempotency-Key", OP))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("MEMBER_EVIDENCE_PENDING"));
        accounts.getOrCreate(evidence());
        mvc.perform(post("/internal/v1/reservations", body).header("Idempotency-Key", OP))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test void httpPrecreationFailureHasDurableStatusAndDoesNotBecomeNotFound() throws Exception {
        purchase(); oldTrial(); var mvc = http();
        String body = new Document("userId", USER).append("sessionId", "session").append("mockExamId", "mock").toJson();
        mvc.perform(post("/internal/v1/reservations", body).header("Idempotency-Key", OP))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("TRANSITION_CONTEXT_CONFLICT"));
        mvc.perform(post("/internal/v1/reservations/status", new Document("userId", USER).append("operationId", OP).toJson()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("RESERVE_REJECTED_BEFORE_CREATION"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retryable").value(false));
        mvc.perform(post("/internal/v1/reservations/status", new Document("userId", USER).append("operationId", UUID.randomUUID().toString()).toJson()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("OPERATION_NOT_FOUND"));
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
    }
    @Test void runtimeBeansRequireDependenciesAndConstructWithVerifiedSchema() {
        purchase(); properties.setPurchaseAccountEnabled(true); properties.setEntitlementQueryEnabled(true);
        var workload = new web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties();
        workload.setEnvironment("test"); workload.setLearningCoreReceiverEnabled(true);
        var jwt = new web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties(); jwt.setIssuer(ISSUER);
        var trial = new web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties(); trial.setExpectedConsumerScopeId("scope");
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(PaidExamConfiguration.class)
                .withPropertyValues("billing.reservation.paid-foundation-enabled=true", "billing.reservation.paid-recovery-enabled=true", "billing.reservation.paid-admission-enabled=true")
                .withBean(org.springframework.data.mongodb.core.MongoTemplate.class, () -> mongo)
                .withBean(web.tosunsaeng.billing.domain.payment.repository.PaymentStore.class, () -> payment)
                .withBean(web.tosunsaeng.billing.domain.payment.config.PaymentProperties.class, () -> properties)
                .withBean(web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties.class, () -> workload)
                .withBean(web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties.class, () -> jwt)
                .withBean(web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties.class, () -> trial)
                .withBean(web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties.class, () -> {
                    var events = new web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties(); events.setEnabled(true); return events;
                })
                .withBean(Clock.class, () -> clock).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PaidExamRouter.class).hasSingleBean(PaidAttemptEventService.class).hasSingleBean(ExamAdmissionReader.class);
                });
    }
}
