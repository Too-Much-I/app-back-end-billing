package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static org.assertj.core.api.Assertions.*;

abstract class RefundOperationsTestSupport extends PaidReservationTestSupport {
    String prepare(boolean missingEvidence) {
        purchase(); var reserved = runtime().reserve(command()); runtime().confirm(confirm(command(), reserved));
        if (missingEvidence) db.getCollection("attempt_groups").updateOne(new Document(), new Document("$unset", new Document("lastConfirmedSessionId", "")));
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        RefundCoreSchema.upgradeUnderMaintenance(db, true); RefundFanoutSchema.installUnderMaintenance(db, true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "OPEN")));
        properties.getRefund().setProcessingEnabled(true);
        var p = db.getCollection("purchases").find().first();
        var observation = new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app", p.getString("transactionKey"), "fake-v1",
                "f".repeat(64), PurchaseObservation.Kind.REFUND, account().getString("activeRefId"), "PREMIUM_1D", NOW, null,
                new RefundEvidence(RefundEvidence.Source.REVENUECAT_API, "d".repeat(64), NOW, null, null));
        var core = new PurchaseCore(payment, properties, clock, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), "fake-v1");
        core.receive(observation); core.apply(observation.providerEventKey());
        new RefundFanout(tx, mongo, runtime(), payment, properties, "test", "scope", clock).tick();
        return p.getString("_id");
    }
    RefundOperationsReader reader() { return new RefundOperationsReader(payment, "test", clock); }
}

class RefundOperationsReaderTest extends RefundOperationsTestSupport {
    @Test void refundCaseReadsAreMinimalReadOnlyAndEnvironmentScoped() {
        String purchaseId = prepare(false);
        Document original = db.getCollection("payment_purchase_cases").find().first();
        String id = original.getString("_id");
        Document result = reader().reviewCase(id);
        assertThat(result.get("review", Document.class)).containsEntry("purchaseId", purchaseId)
                .containsEntry("reviewState", "OPEN").containsEntry("reason", "REFUND_AMOUNT_UNKNOWN");
        assertThat(result.toJson()).doesNotContain(USER, "normalizedObservation", "ownerRefId", "transactionKey", "providerEventKey", "actorDigest");
        assertThat(reader().cases(null).getList("items", Document.class)).hasSize(1);
        var production = new RefundOperationsReader(payment, "production", clock);
        assertThat(production.reviewCase(id).getString("status")).isEqualTo("NOT_FOUND");
        assertThat(production.cases(null).getList("items", Document.class)).isEmpty();
        assertThat(reader().reviewCase(UUID.randomUUID().toString()).getString("status")).isEqualTo("NOT_FOUND");
        assertThat(db.getCollection("payment_purchase_cases").find().first()).isEqualTo(original);
    }
    @Test void legacyRefundCasesRemainVisibleButResolvedAndUnrelatedAreNotListed() {
        prepare(false);
        var cases = db.getCollection("payment_purchase_cases");
        String id = cases.find().first().getString("_id");
        cases.updateOne(new Document("_id", id), new Document("$unset", new Document("reviewState", "")));
        assertThat(reader().cases(null).getList("items", Document.class)).hasSize(1);
        assertThat(reader().reviewCase(id).get("review", Document.class).getString("reviewState")).isEqualTo("OPEN");
        cases.updateOne(new Document("_id", id), new Document("$set", new Document("reviewState", "RESOLVED")));
        assertThat(reader().cases(null).getList("items", Document.class)).isEmpty();
        assertThat(reader().reviewCase(id).get("review", Document.class).getString("reviewState")).isEqualTo("RESOLVED");
        cases.updateOne(new Document("_id", id), new Document("$set", new Document("reviewState", "OPEN")
                .append("reason", "OWNER_UNRESOLVED").append("normalizedObservation.kind", "PURCHASE")));
        assertThat(reader().cases(null).getList("items", Document.class)).isEmpty();
        assertThat(reader().reviewCase(id).getString("status")).isEqualTo("NOT_FOUND");
        cases.updateOne(new Document("_id", id), new Document("$set", new Document("normalizedObservation.kind", "REFUND")));
        assertThat(reader().cases(null).getList("items", Document.class)).hasSize(1);
        assertThat(reader().reviewCase(id).get("review", Document.class).getString("reason")).isEqualTo("OTHER");
    }
    @Test void casePaginationIsBoundedAndDoesNotRepeatRows() {
        prepare(false);
        var cases = db.getCollection("payment_purchase_cases");
        Document seed = cases.find().first();
        for (int i = 0; i < 100; i++) {
            Document copy = new Document(seed);
            copy.put("_id", UUID.randomUUID().toString());
            copy.put("providerEventKey", String.format("%064x", i + 1));
            cases.insertOne(copy);
        }
        Document first = reader().cases(null);
        var items = first.getList("items", Document.class);
        assertThat(items).hasSize(100);
        assertThat(first.getBoolean("hasMore")).isTrue();
        Document second = reader().cases(first.getString("nextAfter"));
        assertThat(second.getList("items", Document.class)).hasSize(1).doesNotContainAnyElementsOf(items);
        assertThat(second.getBoolean("hasMore")).isFalse();
        assertThat(second.get("nextAfter")).isNull();
        assertThat(cases.countDocuments()).isEqualTo(101);
    }
    @Test void doneDoesNotMeanDeliveredAndReadsDoNotMutate() {
        String id = prepare(false);
        var purchase = db.getCollection("purchases").find().first();
        var outbox = db.getCollection(RefundFanout.OUTBOX).find().first();
        var result = reader().purchase(id);
        assertThat(result.getBoolean("enumerationDone")).isTrue();
        assertThat(result.getBoolean("cleanupComplete")).isFalse();
        assertThat(result.getInteger("undeliveredCount")).isEqualTo(1);
        String output = reader().outbox(outbox.getString("_id")).toJson();
        assertThat(output).doesNotContain(USER, "body", "digest", "purchaseId", "session", "transaction", "owner");
        assertThat(db.getCollection("purchases").find().first()).isEqualTo(purchase);
        assertThat(db.getCollection(RefundFanout.OUTBOX).find().first()).isEqualTo(outbox);
    }
    @Test void unresolvedGroupStillIncompleteWithoutOutbox() {
        String id = prepare(true);
        var result = reader().purchase(id);
        assertThat(result.getInteger("openReviewCount")).isEqualTo(1);
        assertThat(result.getInteger("undeliveredCount")).isZero();
        assertThat(result.getBoolean("cleanupComplete")).isFalse();
    }
    @Test void deliveredAndNoOpenReviewCompletesCleanup() {
        String id = prepare(false);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.state", "DELIVERED")));
        assertThat(reader().purchase(id).getBoolean("cleanupComplete")).isTrue();
    }
    @Test void environmentIsolationAndUnknownTargets() {
        String id = prepare(false);
        var production = new RefundOperationsReader(payment, "production", clock);
        assertThat(production.purchase(id).getString("status")).isEqualTo("NOT_FOUND");
        assertThat(production.outbox(db.getCollection(RefundFanout.OUTBOX).find().first().getString("_id")).getString("status")).isEqualTo("NOT_FOUND");
        assertThat(reader().purchase(UUID.randomUUID().toString()).getString("status")).isEqualTo("NOT_FOUND");
    }
    @Test void blockedWorkIsNotReportedCompleteEvenAfterDelivery() {
        String id = prepare(false);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.state", "DELIVERED")));
        db.getCollection("purchases").updateOne(new Document("_id", id), new Document("$set", new Document("refundWork.state", "REVIEW_REQUIRED")));
        assertThat(reader().purchase(id).getBoolean("cleanupComplete")).isFalse();
    }
    @Test void boundedCountsAreExplicitlyTruncated() {
        String id = prepare(true);
        for (int i = 0; i < 101; i++) db.getCollection("attempt_groups").insertOne(new Document("_id", UUID.randomUUID().toString())
                .append("sourceSnapshot", new Document("purchaseId", id)).append("refundReview", new Document("state", "OPEN")));
        var result = reader().purchase(id);
        assertThat(result.getInteger("openReviewCount")).isEqualTo(100);
        assertThat(result.getBoolean("openReviewTruncated")).isTrue();
        assertThat(result.getBoolean("cleanupComplete")).isFalse();
    }
}
