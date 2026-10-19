package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static org.assertj.core.api.Assertions.*;

class RefundOperatorTest extends RefundOperationsTestSupport {
    String epoch() { return db.getCollection(PaymentRecoveryFence.COLLECTION).find().first().getString("epoch"); }
    RefundOperator operator() {
        RefundOperatorSchema.installUnderMaintenance(db, true);
        return new RefundOperator(tx, payment, "test", "scope", clock);
    }
    RefundOperator.Command resume(String id) { return new RefundOperator.Command(UUID.randomUUID().toString(), RefundOperator.Action.OUTBOX_RESUME,
            id, null, 1L, 1L, epoch(), RefundOperator.Reason.CREDENTIALS_FIXED); }
    String block() {
        prepare(false); var row = db.getCollection(RefundFanout.OUTBOX).find().first();
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document("_id", row.getString("_id")), new Document("$set", new Document("work.state", "BLOCKED_AUTH")));
        return row.getString("_id");
    }
    @Test void dryRunThenAtomicResumeAndIdempotentReplayPreserveBody() {
        String id = block(); var op = operator(); var command = resume(id);
        Document before = db.getCollection(RefundFanout.OUTBOX).find().first();
        assertThat(op.execute(command, "a".repeat(64), false, false)).isEqualTo("DRY_RUN_READY");
        assertThat(db.getCollection(RefundFanout.OUTBOX).find().first()).isEqualTo(before);
        assertThat(op.execute(command, "a".repeat(64), true, true)).isEqualTo("RESUMED");
        Document after = db.getCollection(RefundFanout.OUTBOX).find().first();
        assertThat(after.getString("body")).isEqualTo(before.getString("body"));
        assertThat(after.getString("eventId")).isEqualTo(id);
        assertThat(after.get("work", Document.class).getLong("generation")).isEqualTo(2L);
        assertThat(op.execute(command, "a".repeat(64), true, true)).isEqualTo("RESUMED");
        assertThat(db.getCollection(RefundOperatorSchema.COLLECTION).countDocuments()).isOne();
        assertThat(op.result(command.operationId())).isEqualTo("RESUMED");
        assertThatThrownBy(() -> op.execute(command, "b".repeat(64), true, true)).isInstanceOf(IllegalStateException.class);
        assertThat(db.getCollection(RefundFanout.OUTBOX).find().first()).isEqualTo(after);
    }
    @Test void rejectsLiveLeaseStaleEpochAndUnisolatedApply() {
        String id = block(); var op = operator(); var command = resume(id);
        assertThatThrownBy(() -> op.execute(command, "a".repeat(64), true, false)).isInstanceOf(IllegalStateException.class);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.owner", "worker").append("work.until", Date.from(NOW.plusSeconds(30)))));
        assertThatThrownBy(() -> op.execute(command, "a".repeat(64), true, true)).isInstanceOf(IllegalStateException.class);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.owner", null).append("work.until", null)));
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> op.execute(command, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(RefundOperatorSchema.COLLECTION).countDocuments()).isZero();
    }
    @Test void failedAuditRollsBackResume() {
        String id = block(); var op = operator(); var before = db.getCollection(RefundFanout.OUTBOX).find().first();
        db.runCommand(new Document("collMod", RefundOperatorSchema.COLLECTION).append("validator", new Document("result", "reject")));
        assertThatThrownBy(() -> op.execute(resume(id), "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(RefundFanout.OUTBOX).find().first()).isEqualTo(before);
    }
    @Test void resumeCannotBypassFutureRetryAfter() {
        String id = block(); var op = operator();
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.state", "REVIEW_REQUIRED")
                .append("work.nextAttemptAt", Date.from(NOW.plusSeconds(90000)))));
        assertThatThrownBy(() -> op.execute(resume(id), "a".repeat(64), true, true)).isInstanceOf(IllegalStateException.class);
        assertThat(db.getCollection(RefundOperatorSchema.COLLECTION).countDocuments()).isZero();
    }
    @Test void explicitAuditUpgradePreservesExistingSchemaSupport() {
        prepare(false);
        web.tosunsaeng.billing.domain.payment.worker.PaymentOperatorSchema.installUnderMaintenance(db, true);
        assertThatThrownBy(() -> RefundOperatorSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        RefundOperatorSchema.installUnderMaintenance(db, true);
        RefundOperatorSchema.verify(db);
        web.tosunsaeng.billing.domain.payment.worker.PaymentOperatorSchema.verify(db);
        assertThat(db.getCollection(RefundOperatorSchema.COLLECTION).countDocuments()).isZero();
    }
    @Test void recheckKeepsUnprovenOpenThenResolvesWithoutRewindingCursor() {
        String purchase = prepare(true); var op = operator();
        var group = db.getCollection("attempt_groups").find().first();
        var w = db.getCollection("purchases").find().first().get("refundWork", Document.class);
        var cmd = new RefundOperator.Command(UUID.randomUUID().toString(), RefundOperator.Action.GROUP_RECHECK, purchase,
                group.getString("_id"), w.getLong("leaseVersion"), w.getLong("generation"), epoch(), RefundOperator.Reason.EVIDENCE_RECHECKED);
        assertThat(op.execute(cmd, "a".repeat(64), true, true)).isEqualTo("STILL_OPEN");
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isZero();
        // Restore the known original evidence only in this disposable test DB.
        db.getCollection("attempt_groups").updateOne(new Document("_id", group.getString("_id")), new Document("$set", new Document("lastConfirmedSessionId", "session")));
        var next = new RefundOperator.Command(UUID.randomUUID().toString(), RefundOperator.Action.GROUP_RECHECK, purchase,
                group.getString("_id"), w.getLong("leaseVersion") + 1, w.getLong("generation") + 1, epoch(), RefundOperator.Reason.EVIDENCE_RECHECKED);
        assertThat(op.execute(next, "a".repeat(64), true, true)).isEqualTo("RESOLVED");
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isOne();
        assertThat(db.getCollection("purchases").find().first().get("refundWork", Document.class).getString("phase")).isEqualTo("DONE");
        assertThat(db.getCollection(RefundOperatorSchema.COLLECTION).countDocuments()).isEqualTo(2);
    }
    @Test void fanoutResumeRetainsCursorAndEffect() {
        String purchase = prepare(true); var op = operator();
        db.getCollection("purchases").updateOne(new Document("_id", purchase), new Document("$set", new Document("refundWork.state", "REVIEW_REQUIRED")));
        Document before = db.getCollection("purchases").find().first(); var w = before.get("refundWork", Document.class);
        var cmd = new RefundOperator.Command(UUID.randomUUID().toString(), RefundOperator.Action.FANOUT_RESUME, purchase, null,
                w.getLong("leaseVersion"), w.getLong("generation"), epoch(), RefundOperator.Reason.DEPENDENCY_RECOVERED);
        assertThat(op.execute(cmd, "a".repeat(64), true, true)).isEqualTo("RESUMED");
        Document after = db.getCollection("purchases").find().first();
        assertThat(after.get("refund")).isEqualTo(before.get("refund"));
        assertThat(after.get("refundWork", Document.class).get("afterId")).isEqualTo(w.get("afterId"));
    }
}
