package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class PaidReservationIntegrationTest extends PaidReservationTestSupport {
    @Test void paidReserveConfirmReplayPreservesSourceAndWritesUsageAtomically() {
        purchase(); var command = command(); var runtime = runtime(); var first = runtime.reserve(command);
        assertThat(first.source().getString("type")).isEqualTo("SUBSCRIPTION");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isZero();
        var confirmed = runtime.confirm(confirm(command, first));
        assertThat(confirmed.snapshot().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(runtime.confirm(confirm(command, first)).snapshot()).isEqualTo(confirmed.snapshot());
        assertThat(runtime.reserve(command)).isEqualTo(first);
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("sourceKind", "EXAM_OPERATION"))).isEqualTo(2);
    }
    @Test void revokedBeforeConfirmCommitsRejectionButConfirmedReplaySurvivesLaterRevoke() {
        purchase(); var runtime = runtime(); var first = runtime.reserve(command());
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")));
        error(() -> runtime.confirm(confirm(command(), first)), "ENTITLEMENT_REVOKED");
        error(() -> runtime.confirm(confirm(command(), first)), "ENTITLEMENT_REVOKED");
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CANCELED);
        assertThat(db.getCollection("attempt_groups").countDocuments()).isZero();
    }
    @Test void unknownCommitAndParallelConfirmProduceOneGroup() throws Exception {
        purchase(); var runtime = runtime(); var first = runtime.reserve(command());
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> runtime.confirm(confirm(command(), first)));
            var b = pool.submit(() -> runtime.confirm(confirm(command(), first)));
            assertThat(a.get(10, TimeUnit.SECONDS).snapshot()).isEqualTo(b.get(10, TimeUnit.SECONDS).snapshot());
        }
        assertThat(db.getCollection("attempt_groups").countDocuments()).isOne();
    }
    @Test void trialPaidConfirmRestoresOnceAndLateCompletionKeepsNewGuard() {
        purchase(); String old = oldTrial(); var command = transition(old); var runtime = runtime();
        var reserved = runtime.reserve(command);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getBoolean("replacementBlocked", false)).isFalse();
        runtime.confirm(confirm(command, reserved)); runtime.confirm(confirm(command, reserved));
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isOne();
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        tx.execute(c -> { var group = c.groups().findById(old).orElseThrow();
            c.groups().markCompleted(old, "old", group.getVersion(), NOW, NOW).orElseThrow();
            var session = c.sessions().findBySessionId("old").orElseThrow();
            c.sessions().completeActive("old", old, "subject", session.getVersion(), NOW).orElseThrow();
            return true;
        });
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(runtime.reserve(command)).isEqualTo(reserved);
    }
    @Test void completionBeforeConfirmPreventsRestoration() {
        purchase(); String old = oldTrial(); var command = transition(old); var runtime = runtime(); var reserved = runtime.reserve(command);
        tx.execute(c -> { var group = c.groups().findById(old).orElseThrow(); c.groups().markCompleted(old, "old", group.getVersion(), NOW, NOW).orElseThrow(); return true; });
        runtime.confirm(confirm(command, reserved));
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isZero();
    }
    @Test void cancelAndExpiryNeverTerminateOldTrialOrRestore() {
        purchase(); String old = oldTrial(); var command = transition(old); var runtime = runtime(); var reserved = runtime.reserve(command);
        assertThat(runtime(Clock.fixed(NOW.plusSeconds(300), ZoneOffset.UTC)).expire(USER, OP, reserved.responseSnapshot().getString("reservationId"))).isTrue();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getBoolean("replacementBlocked", false)).isFalse();
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
    }
    @Test void sameSourceRestartIsReplacementAndNewAfterSubmissionIsInitial() {
        purchase(); var runtime = runtime(); var initial = runtime.reserve(command()); runtime.confirm(confirm(command(), initial));
        String group = initial.responseSnapshot().getString("attemptGroupId");
        var restart = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var req = new ReserveRequest(USER, "next", "mock", null, null, null, restart);
        var cmd = new ReserveCommand(UUID.randomUUID().toString(), USER, "next", "mock", null, null, null, new ReservePayloadHasher().hash(req), restart);
        var replacement = runtime.reserve(cmd);
        assertThat(replacement.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        assertThat(replacement.responseSnapshot()).doesNotContainKey("transition");
        runtime.confirm(confirm(cmd, replacement));
        var fresh = new RestartContext(group, "next", RestartContext.StartReason.NEW_AFTER_SUBMISSION);
        var req2 = new ReserveRequest(USER, "third", "next-mock", null, null, null, fresh);
        var cmd2 = new ReserveCommand(UUID.randomUUID().toString(), USER, "third", "next-mock", null, null, null, new ReservePayloadHasher().hash(req2), fresh);
        var result = runtime.reserve(cmd2); assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("INITIAL");
        runtime.confirm(confirm(cmd2, result));
    }
    @Test void phoneTargetPaidStartsInitialAndRestoresTargetWithoutTransition() {
        purchase(); String old = oldTrial(); String continuation = UUID.randomUUID().toString();
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set",
                new Document("ownerTransitionReason", "PHONE_REJOIN").append("ownerTransitionId", continuation).append("sessionOwnerEpoch", 2L)));
        var req = new ReserveRequest(USER, "session", "mock", Reservation.ContinuationReason.PHONE_REJOIN, continuation, old);
        var cmd = new ReserveCommand(OP, USER, "session", "mock", req.continuationReason(), continuation, old, new ReservePayloadHasher().hash(req));
        var runtime = runtime(); var result = runtime.reserve(cmd);
        assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("INITIAL");
        assertThat(result.responseSnapshot().getString("attemptGroupId")).isNotEqualTo(old);
        assertThat(result.responseSnapshot().getString("continuationId")).isEqualTo(continuation);
        assertThat(result.responseSnapshot()).doesNotContainKey("transition");
        runtime.confirm(confirm(cmd, result));
        assertThat(db.getCollection("entitlement_grants").find(new Document("sourceType", "TRIAL_PAID_RESTORATION")).first().getString("ownerUserId")).isEqualTo(USER);
    }
    @Test void oldOwnerEpochChangeBeforeConfirmDeniesAndKeepsPreviousGroup() {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$inc", new Document("sessionOwnerEpoch", 1L)));
        error(() -> runtime().confirm(confirm(cmd, result)), "OPERATION_OWNER_MISMATCH");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getBoolean("replacementBlocked", false)).isFalse();
    }
    @Test void rollbackAtReceiptWriteLeavesNoPartialRestorationOrGroupClosure() {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        db.runCommand(new Document("collMod", PaidExamStore.RECEIPTS).append("validator", new Document("commandType", "RESERVE").append("state", "RESERVED")));
        error(() -> runtime().confirm(confirm(cmd, result)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").find().first().getBoolean("replacementBlocked", false)).isFalse();
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection("reservations").find().first().getString("status")).isEqualTo("RESERVED");
    }
    @Test void missingContextIsDurableFailureAndDifferentPayloadCannotOverwriteIt() {
        purchase(); oldTrial(); error(() -> runtime().reserve(command()), "TRANSITION_CONTEXT_CONFLICT");
        error(() -> service.status(USER, OP), "RESERVE_REJECTED_BEFORE_CREATION");
        error(() -> runtime().reserve(command("different")), "IDEMPOTENCY_KEY_CONFLICT");
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
    }
    @Test void noRightsPreservesOldTrialAndDoesNotInventTerminalContextFailure() {
        accounts.getOrCreate(evidence()); String old = oldTrial();
        var restart = new RestartContext(old, "old", RestartContext.StartReason.NEW_AFTER_SUBMISSION);
        var req = new ReserveRequest(USER, "session", "mock-next", null, null, null, restart);
        var cmd = new ReserveCommand(OP, USER, "session", "mock-next", null, null, null, new ReservePayloadHasher().hash(req), restart);
        error(() -> runtime().reserve(cmd), "ENTITLEMENT_INSUFFICIENT");
        assertThat(db.getCollection("attempt_groups").find().first().getBoolean("replacementBlocked", false)).isFalse();
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isZero();
    }
    @Test void freeGrantFifoCancelReleaseAndNoRestorationOfRestoredPass() {
        accounts.getOrCreate(evidence());
        tx.execute(c -> {
            c.grants().insert(web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant.restoredTrial("first", "test", USER, "consumed-one", 1L, NOW.minusSeconds(20)));
            c.grants().insert(web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant.restoredTrial("second", "test", USER, "consumed-two", 2L, NOW.minusSeconds(10)));
            return true;
        });
        var runtime = runtime(); var result = runtime.reserve(command()); assertThat(result.source().getString("grantId")).isEqualTo("first");
        var request = new web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest(USER, web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.SESSION_COMMIT_FAILED);
        var cancel = new CancelCommand(OP, result.responseSnapshot().getString("reservationId"), USER, request.reason(), new LifecyclePayloadHasher().hashCancel(result.responseSnapshot().getString("reservationId"), request));
        runtime.cancel(cancel); runtime.cancel(cancel);
        assertThat(db.getCollection("entitlement_grants").find(new Document("_id", "first")).first().getInteger("availableUnits")).isEqualTo(1);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(2);
    }
    @Test void confirmedReplayRemainsSuccessfulAfterRefundAndNormalExpiryDoesNotRejectHeldReservation() {
        purchase(); var runtime = runtime(); var result = runtime.reserve(command());
        db.getCollection("subscription_entitlements").updateOne(new Document(), new Document("$set", new Document("endsAt", Date.from(NOW.plusSeconds(1)))));
        var later = runtime(Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
        var confirmed = later.confirm(confirm(command(), result));
        payment.transaction(session -> {
            db.getCollection("purchases").updateOne(session, new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")));
            db.getCollection("subscription_entitlements").updateOne(session, new Document(), new Document("$set", new Document("revokedAt", Date.from(NOW.plusSeconds(3)))));
            return true;
        });
        assertThat(later.confirm(confirm(command(), result)).snapshot()).isEqualTo(confirmed.snapshot());
    }
    @Test void admissionSchemaUpgradeIsExplicitAndRetainsOriginalReceipt() {
        purchase(); runtime().reserve(command());
        Document before = receipt();
        db.runCommand(new Document("collMod", PaidExamStore.RECEIPTS).append("validator", PaidExamSchema.recoveryValidators().get(PaidExamStore.RECEIPTS)));
        assertThatThrownBy(() -> PaidExamSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaidExamSchema.upgradeAdmissionUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        PaidExamSchema.upgradeAdmissionUnderMaintenance(db, true);
        assertThat(receipt()).isEqualTo(before);
    }
    @Test void confirmAndRefundRaceConvergeThroughPurchaseWriteConflict() throws Exception {
        purchase(); var runtime = runtime(); var result = runtime.reserve(command());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await();
                try { runtime.confirm(confirm(command(), result)); return "CONFIRMED"; }
                catch (InternalApiException e) { assertThat(e.code()).isEqualTo("ENTITLEMENT_REVOKED"); return "CANCELED"; }
            });
            var b = pool.submit(() -> { start.await(); return payment.transaction(session -> {
                db.getCollection("purchases").updateOne(session, new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")).append("$inc", new Document("version", 1L)));
                db.getCollection("subscription_entitlements").updateOne(session, new Document(), new Document("$set", new Document("revokedAt", Date.from(NOW))).append("$inc", new Document("version", 1L)));
                return true;
            }); });
            start.countDown(); String outcome = a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertThat(service.status(USER, OP).orElseThrow().reservationStatus().name()).isEqualTo(outcome);
            if (outcome.equals("CONFIRMED")) assertThat(runtime.confirm(confirm(command(), result)).snapshot().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
            else error(() -> runtime.confirm(confirm(command(), result)), "ENTITLEMENT_REVOKED");
        }
    }
    @Test void confirmAndPreviousCompletionRaceNeverDuplicatesRestorationOrReleasesNewGuard() throws Exception {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return runtime().confirm(confirm(cmd, result)); });
            var b = pool.submit(() -> { start.await(); return tx.execute(c -> {
                var group = c.groups().findById(old).orElseThrow();
                c.groups().markCompleted(old, "old", group.getVersion(), NOW, NOW).orElseThrow();
                return true;
            }); });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        long restorations = db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"));
        assertThat(restorations).isBetween(0L, 1L);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().getString("currentGroupId")).isEqualTo(result.responseSnapshot().getString("attemptGroupId"));
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getString("status")).isEqualTo("COMPLETED");
    }
    @Test void sameKeyConcurrentTrialPaidConfirmRestoresExactlyOnce() throws Exception {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> runtime().confirm(confirm(cmd, result)));
            var b = pool.submit(() -> runtime().confirm(confirm(cmd, result)));
            assertThat(a.get(10, TimeUnit.SECONDS).snapshot()).isEqualTo(b.get(10, TimeUnit.SECONDS).snapshot());
        }
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isOne();
        assertThat(db.getCollection("entitlement_ledger").countDocuments(new Document("dedupeKey", "TRIAL_PAID_RESTORATION:test:consumption"))).isOne();
    }
    @Test void trialOwnerFencePrecedesPayloadConflictOnReplay() {
        accounts.getOrCreate(evidence()); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set", new Document("userId", "99999999-9999-4999-8999-999999999999")));
        error(() -> runtime().reserve(command("changed")), "OPERATION_OWNER_MISMATCH");
        error(() -> service.status(USER, OP), "OPERATION_OWNER_MISMATCH");
    }
    @Test void retainedLegacyTrialOperationCannotBeRecreatedAsPaid() {
        purchase(); oldTrial();
        tx.execute(c -> { c.reservations().insert(Reservation.reserved(UUID.randomUUID().toString(), "subject", OP, command().payloadHash(),
                Reservation.Kind.REPLACEMENT, UUID.randomUUID().toString(), "legacy", "mock", null, null, NOW, NOW.plusSeconds(300))); return true; });
        error(() -> runtime().reserve(command()), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isZero();
        assertThat(db.getCollection("reservations").countDocuments()).isOne();
    }
    @Test void revokedPhoneBindingCannotUseExistingFreeReplacement() {
        accounts.getOrCreate(evidence()); String old = oldTrial();
        db.getCollection("trial_eligibility").updateOne(new Document(), new Document("$set", new Document("state", "REVOKED")));
        error(() -> runtime().reserve(transition(old)), "ENTITLEMENT_INSUFFICIENT");
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
        assertThat(db.getCollection("attempt_groups").find().first().getBoolean("replacementBlocked", false)).isFalse();
    }
    @Test void firstTrialIsIssuedHeldAndConsumedInSameTransactionWithoutPaid() {
        db.getCollection("trial_eligibility").insertOne(new Document("consumerScopeId", "scope").append("userId", USER)
                .append("state", "VERIFIED").append("lastEventId", "fake-event")
                .append("candidates", List.of(new Document("keyVersion", "fake-v1").append("value", "fake-candidate"))));
        tx.execute(c -> { c.operations().insert(web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition.freeExamOnce(NOW)); return true; });
        var result = runtime().reserve(command());
        assertThat(result.source().getString("type")).isEqualTo("EXAM_PASS");
        assertThat(result.source().getString("trialClaimId")).isNotBlank();
        runtime().confirm(confirm(command(), result));
        assertThat(db.getCollection("trial_claims").countDocuments()).isOne();
        assertThat(db.getCollection("entitlement_grants").find().first().getInteger("consumedUnits")).isEqualTo(1);
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(runtime().reserve(command())).isEqualTo(result);
    }
}
