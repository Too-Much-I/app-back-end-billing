package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import static org.assertj.core.api.Assertions.*;

/** Billing-side C04-C06/H16. Does not simulate LC accepted-work authorization. */
class HistoricalPaidSessionEventIntegrationTest extends PaidReservationTestSupport {
    PaidAttemptEventService events() { return new PaidAttemptEventService(tx, mongo, payment, properties, "test", clock); }
    AttemptGroupStatusEvent event(String group, AttemptGroupEventTarget target) {
        return new AttemptGroupStatusEvent(UUID.randomUUID().toString(), "AttemptGroupStatusChanged", 1, "learning-core", NOW,
                USER, group, "session", target, null, null, "a".repeat(64));
    }
    String prepare() {
        purchase(); var cmd = command(); var first = runtime().reserve(cmd); runtime().confirm(confirm(cmd, first));
        String group = first.responseSnapshot().getString("attemptGroupId");
        var restart = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var request = new ReserveRequest(USER, "second-session", "mock", null, null, null, restart);
        var next = new ReserveCommand(UUID.randomUUID().toString(), USER, "second-session", "mock", null, null, null,
                new ReservePayloadHasher().hash(request), restart);
        var reserved = runtime().reserve(next); runtime().confirm(confirm(next, reserved));
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        RefundCoreSchema.upgradeUnderMaintenance(db, true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "OPEN")));
        properties.getRefund().setProcessingEnabled(true);
        var core = new PurchaseCore(payment, properties, clock, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), "fake-v1");
        var purchase = db.getCollection("purchases").find().first();
        var refund = new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app", purchase.getString("transactionKey"),
                "fake-v1", "a".repeat(64), PurchaseObservation.Kind.REFUND, account().getString("activeRefId"), "PREMIUM_1D", NOW.minusSeconds(10), null,
                new RefundEvidence(RefundEvidence.Source.REVENUECAT_API, "b".repeat(64), NOW, null, null));
        core.receive(refund); core.apply(refund.providerEventKey());
        return group;
    }
    Document row(String collection, String id) { return db.getCollection(collection).find(new Document("_id", id)).first(); }
    @Test void lateCompletionAfterRefundOnlyChangesOldSessionAndUsage() {
        String group = prepare(); var before = row("attempt_groups", group); var second = row("attempt_sessions", "second-session");
        var guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        var e = event(group, AttemptGroupEventTarget.COMPLETED);
        assertThat(events().process(e)).isEqualTo(AttemptGroupEventOutcome.APPLIED);
        assertThat(events().process(e)).isEqualTo(AttemptGroupEventOutcome.DUPLICATE);
        assertThat(events().process(event(group, AttemptGroupEventTarget.RETAKE_AVAILABLE))).isEqualTo(AttemptGroupEventOutcome.STALE);
        assertThat(row("attempt_sessions", "session").getString("state")).isEqualTo("COMPLETED");
        assertThat(row("attempt_groups", group)).isEqualTo(before);
        assertThat(row("attempt_sessions", "second-session")).isEqualTo(second);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("type", "EXAM_STATUS"))).isOne();
    }
    @Test void gradingCannotAdvanceCurrentGroupAndFailureCannotEnableRetake() {
        String group = prepare(); var before = row("attempt_groups", group);
        assertThat(events().process(event(group, AttemptGroupEventTarget.GRADING))).isEqualTo(AttemptGroupEventOutcome.STALE);
        assertThat(events().process(event(group, AttemptGroupEventTarget.RETAKE_AVAILABLE))).isEqualTo(AttemptGroupEventOutcome.APPLIED);
        assertThat(row("attempt_sessions", "session").getString("state")).isEqualTo("FAILED");
        assertThat(events().process(event(group, AttemptGroupEventTarget.COMPLETED))).isEqualTo(AttemptGroupEventOutcome.STALE);
        assertThat(row("attempt_groups", group)).isEqualTo(before);
    }
    @Test void missingConfirmOrCompletionPermissionDoesNotCreateInbox() {
        String group = prepare();
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"), new Document("$set", new Document("completionAllowed", false)));
        error(() -> events().process(event(group, AttemptGroupEventTarget.COMPLETED)), "EVENT_TARGET_CONFLICT");
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"), new Document("$set", new Document("completionAllowed", true)).append("$unset", new Document("confirmedAt", "")));
        error(() -> events().process(event(group, AttemptGroupEventTarget.COMPLETED)), "EVENT_TARGET_CONFLICT");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
    }
    @Test void deletionAndWithdrawalWinOverHistoricalPermission() {
        String group = prepare();
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"), new Document("$set", new Document("deleted", true)));
        error(() -> events().process(event(group, AttemptGroupEventTarget.COMPLETED)), "EVENT_TARGET_CONFLICT");
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"), new Document("$unset", new Document("deleted", "")));
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$set", new Document("state", "WITHDRAWN")));
        error(() -> events().process(event(group, AttemptGroupEventTarget.COMPLETED)), "EVENT_TARGET_CONFLICT");
        assertThat(row("attempt_sessions", "session").getString("state")).isEqualTo("ACTIVE");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
    }
    @Test void usageFailureRollsBackHistoricalTerminalAndInbox() {
        String group = prepare();
        db.runCommand(new Document("collMod", "payment_ledger").append("validator", new Document("type", new Document("$ne", "EXAM_STATUS"))));
        error(() -> events().process(event(group, AttemptGroupEventTarget.COMPLETED)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(row("attempt_sessions", "session").getString("state")).isEqualTo("ACTIVE");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
    }
    @Test void mismatchedSourceCannotUseHistoricalPermission() {
        String group = prepare();
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"),
                new Document("$set", new Document("sourceSnapshot.purchaseId", UUID.randomUUID().toString())));
        error(() -> events().process(event(group, AttemptGroupEventTarget.COMPLETED)), "EVENT_TARGET_CONFLICT");
        assertThat(db.getCollection("inbound_event_inbox").countDocuments()).isZero();
    }
    @Test void newPendingOperationGuardIsUntouchedByLateCompletion() {
        String group = prepare();
        // Represent a newer operation's guard; this old event must not touch even its version.
        db.getCollection(PaidExamStore.GUARDS).updateOne(new Document(), new Document("$set",
                new Document("pendingOperationId", UUID.randomUUID().toString())
                        .append("pendingReservationId", UUID.randomUUID().toString())
                        .append("pendingGroupId", UUID.randomUUID().toString()).append("version", 50L)));
        var guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        assertThat(events().process(event(group, AttemptGroupEventTarget.COMPLETED))).isEqualTo(AttemptGroupEventOutcome.APPLIED);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
    }
    @Test void concurrentDuplicateHasOneTerminalEffectAndPreservesGuard() throws Exception {
        String group = prepare(); var guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        var e = event(group, AttemptGroupEventTarget.COMPLETED);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> events().process(e)); var b = pool.submit(() -> events().process(e));
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(AttemptGroupEventOutcome.APPLIED, AttemptGroupEventOutcome.DUPLICATE);
        }
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("type", "EXAM_STATUS"))).isOne();
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
    }
}
