package web.tosunsaeng.billing.domain.reservation.paid;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import web.tosunsaeng.billing.domain.examdeletion.*;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import static org.assertj.core.api.Assertions.*;

class DeletionMongoIntegrationTest extends PaidReservationTestSupport {
    @BeforeEach void deletionSchema() { DeletionSchema.installUnderMaintenance(db, true); }
    DeletionService deletion() { return new DeletionService(tx, payment, properties, "test", clock); }
    Document row(String collection, String id) { return db.getCollection(collection).find(new Document("_id", id)).first(); }
    String confirmed() {
        purchase(); var result = runtime().reserve(command()); runtime().confirm(confirm(command(), result));
        return result.responseSnapshot().getString("attemptGroupId");
    }
    DeletionRequestDecoder.Request event(String eventId, String group, String session, String user) {
        var json = new Document("eventId", eventId).append("schemaVersion", 1).append("userId", user)
                .append("deletionOperationId", "33333333-3333-4333-8333-333333333333").append("attemptGroupId", group)
                .append("sessionId", session).append("deletedAt", "2026-10-14T00:00:00.000Z");
        return new DeletionRequestDecoder().decode(json.toJson().getBytes(StandardCharsets.UTF_8));
    }
    DeletionRequestDecoder.Request event(String group) { return event(UUID.randomUUID().toString(), group, "session", USER); }
    @Test void commitsDenyAndReceiptWithoutChangingFinancialRights() {
        String group = confirmed(); var purchases = db.getCollection("purchases").find().into(new ArrayList<>());
        var ledgers = db.getCollection("payment_ledger").find().into(new ArrayList<>());
        var request = event(group); deletion().process(request);
        assertThat(row("attempt_sessions", "session").getBoolean("deleted")).isTrue();
        assertThat(row("attempt_groups", group).getBoolean("replacementBlocked")).isTrue();
        assertThat(row("attempt_groups", group).containsKey("openGuard")).isFalse();
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().get("currentGroupId")).isNull();
        assertThat(db.getCollection("purchases").find().into(new ArrayList<>())).isEqualTo(purchases);
        assertThat(db.getCollection("payment_ledger").find().into(new ArrayList<>())).isEqualTo(ledgers);
        assertThat(db.getCollection(DeletionService.FACTS).countDocuments()).isOne();
        assertThat(row(DeletionService.RECEIPTS, "test:" + request.eventId()).getString("disposition")).isEqualTo("APPLIED");
    }
    @Test void http204IsBackedByCommittedReceiptAndConflictHasNo204() throws Exception {
        String group = confirmed(); var request = event(group);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DeletionController(deletion())).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(DeletionController.ROUTE)
                .contentType("application/json").content(request.canonicalBytes())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(204); assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(row(DeletionService.RECEIPTS, "test:" + request.eventId())).isNotNull();
        var conflict = event(request.eventId(), group, "another-session", USER);
        var rejected = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(DeletionController.ROUTE)
                .contentType("application/json").content(conflict.canonicalBytes())).andReturn().getResponse();
        assertThat(rejected.getStatus()).isEqualTo(409);
        assertThat(db.getCollection(DeletionService.RECEIPTS).countDocuments()).isOne();
    }
    @Test void replaySurvivesAbsentSessionAndNewEventOnlyAddsReceipt() {
        String group = confirmed(); var first = event(group); deletion().process(first);
        Document fact = row(DeletionService.FACTS, "test:session"), guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        Document savedGroup = row("attempt_groups", group);
        db.getCollection("attempt_sessions").deleteOne(new Document("_id", "session"));
        deletion().process(first); var second = event(group); deletion().process(second);
        assertThat(row(DeletionService.FACTS, "test:session")).isEqualTo(fact);
        assertThat(row("attempt_groups", group)).isEqualTo(savedGroup);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection(DeletionService.RECEIPTS).countDocuments()).isEqualTo(2);
        error(() -> deletion().process(event(second.eventId(), group, "other", USER)), "EVENT_ID_CONFLICT");
        error(() -> deletion().process(event(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "session", USER)), "DELETION_TARGET_UNRESOLVED");
    }
    @Test void rejectsUnknownTargetAndUnknownEnvironmentWithoutReceipt() {
        String group = confirmed();
        error(() -> deletion().process(event(UUID.randomUUID().toString(), group, "absent", USER)), "DELETION_TARGET_UNRESOLVED");
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"), new Document("$unset", new Document("creationOwner", "")));
        error(() -> deletion().process(event(group)), "DELETION_TARGET_UNRESOLVED");
        assertThat(db.getCollection(DeletionService.FACTS).countDocuments()).isZero();
        assertThat(db.getCollection(DeletionService.RECEIPTS).countDocuments()).isZero();
    }
    ReserveCommand restart(String group) {
        var context = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var request = new ReserveRequest(USER, "second", "mock", null, null, null, context);
        return new ReserveCommand(UUID.randomUUID().toString(), USER, "second", "mock", null, null, null, new ReservePayloadHasher().hash(request), context);
    }
    @Test void pendingReplacementPreventsDeletionAndRollsBackFenceWrites() {
        String group = confirmed(); runtime().reserve(restart(group));
        var session = row("attempt_sessions", "session"); var guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        error(() -> deletion().process(event(group)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(row("attempt_sessions", "session")).isEqualTo(session);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection(DeletionService.FACTS).countDocuments()).isZero();
    }
    @Test void historicalDeletionPreservesNewSessionGroupAndGuard() {
        String group = confirmed(); var cmd = restart(group); var reserved = runtime().reserve(cmd); runtime().confirm(confirm(cmd, reserved));
        Document current = row("attempt_sessions", "second"), g = row("attempt_groups", group), guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        deletion().process(event(group));
        assertThat(row("attempt_sessions", "session").getBoolean("deleted")).isTrue();
        assertThat(row("attempt_sessions", "second")).isEqualTo(current);
        assertThat(row("attempt_groups", group)).isEqualTo(g);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(row(DeletionService.FACTS, "test:session").getString("disposition")).isEqualTo("SESSION_ONLY");
    }
    @Test void postDeletionRestartUsesFreshInitialNotConsumptionOrRestoration() {
        String group = confirmed(); deletion().process(event(group));
        var result = runtime().reserve(restart(group));
        assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("INITIAL");
        assertThat(result.responseSnapshot().getString("attemptGroupId")).isNotEqualTo(group);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isZero();
    }
    @Test void failedReceiptInsertionRollsBackSessionGroupAndGuard() {
        String group = confirmed(); var session = row("attempt_sessions", "session"); var g = row("attempt_groups", group);
        var guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        db.runCommand(new Document("collMod", DeletionService.RECEIPTS)
                .append("validator", new Document("forbiddenTestOnlyField", new Document("$exists", true))));
        error(() -> deletion().process(event(group)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(row("attempt_sessions", "session")).isEqualTo(session); assertThat(row("attempt_groups", group)).isEqualTo(g);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection(DeletionService.FACTS).countDocuments()).isZero();
    }
    @Test void concurrentEventsConvergeOnOneEffectAndTwoReceipts() throws Exception {
        String group = confirmed(); var a = event(group); var b = event(group);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var tasks = List.of(a, b).stream().map(r -> pool.submit(() -> { start.await(); deletion().process(r); return true; })).toList();
            start.countDown(); for (var f : tasks) assertThat(f.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(db.getCollection(DeletionService.FACTS).countDocuments()).isOne();
        assertThat(db.getCollection(DeletionService.RECEIPTS).countDocuments()).isEqualTo(2);
        assertThat(db.getCollection(DeletionService.RECEIPTS).countDocuments(new Document("disposition", "APPLIED"))).isOne();
    }
    @Test void refundFanoutRacePreservesDeletionAndFinancialRefund() throws Exception {
        String group = confirmed();
        db.getCollection(web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence.COLLECTION)
                .updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        web.tosunsaeng.billing.domain.payment.purchase.RefundCoreSchema.upgradeUnderMaintenance(db, true);
        RefundFanoutSchema.installUnderMaintenance(db, true);
        db.getCollection(web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence.COLLECTION)
                .updateOne(new Document(), new Document("$set", new Document("barrier", "OPEN")));
        properties.getRefund().setProcessingEnabled(true);
        var core = new web.tosunsaeng.billing.domain.payment.purchase.PurchaseCore(payment, properties, clock,
                Map.of(web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Store.PLAY_STORE, "fake.app"), "fake-v1");
        var purchase = db.getCollection("purchases").find().first();
        var observation = new web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation("SANDBOX",
                web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Store.PLAY_STORE, "fake.app", purchase.getString("transactionKey"),
                "fake-v1", "f".repeat(64), web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Kind.REFUND,
                account().getString("activeRefId"), "PREMIUM_1D", NOW, null,
                new web.tosunsaeng.billing.domain.payment.purchase.RefundEvidence(
                        web.tosunsaeng.billing.domain.payment.purchase.RefundEvidence.Source.REVENUECAT_API, "d".repeat(64), NOW, null, null));
        core.receive(observation); core.apply(observation.providerEventKey());
        var refund = row("purchases", purchase.getString("_id")).get("refund", Document.class);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); deletion().process(event(group)); return true; });
            var b = pool.submit(() -> { start.await(); new RefundFanout(tx, mongo, runtime(), payment, properties, "test", "scope", clock).tick(); return true; });
            start.countDown(); assertThat(a.get(10, TimeUnit.SECONDS)).isTrue(); assertThat(b.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(row("attempt_groups", group).getBoolean("deleted")).isTrue();
        assertThat(row("attempt_sessions", "session").getBoolean("deleted")).isTrue();
        assertThat(row("purchases", purchase.getString("_id")).get("refund", Document.class)).isEqualTo(refund);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().get("currentGroupId")).isNull();
    }
    @Test void schemaVerificationRefusesTtlAndDoesNotRepairIt() {
        DeletionSchema.verify(db);
        assertThatThrownBy(() -> DeletionSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        db.getCollection(DeletionService.FACTS).createIndex(new Document("appliedAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(1L, TimeUnit.DAYS));
        assertThatThrownBy(() -> DeletionSchema.verify(db)).isInstanceOf(IllegalStateException.class);
    }
    @Test void unknownCommitRetriesWithoutDuplicateEffects() {
        String group = confirmed(); var request = event(group);
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        deletion().process(request); deletion().process(request);
        assertThat(db.getCollection(DeletionService.FACTS).countDocuments()).isOne();
        assertThat(db.getCollection(DeletionService.RECEIPTS).countDocuments()).isOne();
    }
    String trialWithCreationEvidence() {
        String group = oldTrial();
        // Fixture represents an originally recorded creation snapshot, not a production backfill.
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "old"), new Document("$set", new Document("creationOwner",
                new Document("environment", "test").append("kind", "TRIAL_SUBJECT").append("userId", USER).append("reference", "subject").append("epoch", 1L))));
        return group;
    }
    @Test void trialDeletionDoesNotRestoreConsumedGrant() {
        String group = trialWithCreationEvidence(); Document grant = row("entitlement_grants", "trial-grant");
        deletion().process(event(UUID.randomUUID().toString(), group, "old", USER));
        assertThat(row("attempt_groups", group).getBoolean("deleted")).isTrue();
        assertThat(row("entitlement_grants", "trial-grant")).isEqualTo(grant);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
    }
    @Test void phoneSourceDeletionDoesNotBlockTargetCurrentRights() {
        String group = trialWithCreationEvidence(); Document g = row("attempt_groups", group);
        String target = "99999999-9999-4999-8999-999999999999";
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set",
                new Document("userId", target).append("sessionOwnerEpoch", 2L)));
        deletion().process(event(UUID.randomUUID().toString(), group, "old", USER));
        assertThat(row("attempt_groups", group)).isEqualTo(g);
        assertThat(row("attempt_sessions", "old").getBoolean("deleted")).isTrue();
        assertThat(row(DeletionService.FACTS, "test:old").getString("disposition")).isEqualTo("SESSION_ONLY");
    }
    @Test void guestMergeActorNeedsNoSeparateMergeEvidence() {
        String group = trialWithCreationEvidence(); String target = "99999999-9999-4999-8999-999999999999";
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set", new Document("userId", target)));
        deletion().process(event(UUID.randomUUID().toString(), group, "old", target));
        assertThat(row("attempt_groups", group).getBoolean("deleted")).isTrue();
    }
    @Test void lateCompletionCannotReviveDeletedSessionAndFailureRetainsExactPointer() {
        String group = confirmed();
        var events = new PaidAttemptEventService(tx, mongo, payment, properties, "test", clock);
        var failure = new web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent(UUID.randomUUID().toString(),
                "AttemptGroupStatusChanged", 1, "learning-core", NOW, USER, group, "session",
                web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget.RETAKE_AVAILABLE, null, null, "a".repeat(64));
        events.process(failure);
        assertThat(row("attempt_groups", group).getString("lastConfirmedSessionId")).isEqualTo("session");
        deletion().process(event(group));
        var complete = new web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent(UUID.randomUUID().toString(),
                "AttemptGroupStatusChanged", 1, "learning-core", NOW, USER, group, "session",
                web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget.COMPLETED, null, null, "b".repeat(64));
        error(() -> events.process(complete), "EVENT_TARGET_CONFLICT");
        assertThat(row("attempt_groups", group).getBoolean("deleted")).isTrue();
    }
    @Test void lcActorDoesNotNeedHistoricalMergeProofButOtherCurrentOwnerIsPreserved() {
        String group = confirmed(); var guard = db.getCollection(PaidExamStore.GUARDS).find().first(); var g = row("attempt_groups", group);
        deletion().process(event(UUID.randomUUID().toString(), group, "session", "99999999-9999-4999-8999-999999999999"));
        assertThat(row("attempt_sessions", "session").getBoolean("deleted")).isTrue();
        assertThat(row("attempt_groups", group)).isEqualTo(g);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
    }
}
