package web.tosunsaeng.billing.domain.reservation.paid;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.purchase.RefundCoreSchema;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static web.tosunsaeng.billing.domain.reservation.paid.PaidExamSchema.*;

/** Additive explicit maintenance manifest. No startup DDL or TTL for unfinished delivery. */
public final class RefundFanoutSchema {
    public static final String VERSION = "payment-refund-fanout-r1";
    private RefundFanoutSchema() {}
    record Entry(String collection, String name, Document keys, boolean unique) {}
    static List<Entry> indexes() {
        return List.of(
                new Entry("reservations", "ix_reservation_refund_source", new Document("sourceSnapshot.purchaseId", 1).append("status", 1).append("_id", 1), false),
                new Entry("attempt_groups", "ix_group_refund_source", new Document("sourceSnapshot.purchaseId", 1).append("_id", 1), false),
                new Entry(RefundFanout.OUTBOX, "ux_access_outbox_group", new Document("environment", 1).append("producer", 1).append("consumerScopeId", 1).append("attemptGroupId", 1), true),
                new Entry(RefundFanout.OUTBOX, "ux_access_outbox_event", new Document("eventId", 1), true),
                new Entry(RefundFanout.OUTBOX, "ix_access_outbox_due", new Document("environment", 1).append("work.state", 1).append("work.nextAttemptAt", 1).append("_id", 1), false));
    }
    static Document validator() {
        Document work = object(new Document("state", values("READY", "BLOCKED_AUTH", "REVIEW_REQUIRED", "DELIVERED"))
                .append("owner", nullable("string")).append("until", nullable("date"))
                .append("leaseVersion", positive()).append("generation", positive()).append("nextAttemptAt", nullable("date"))
                .append("retryStartedAt", type("date")).append("attempts", type("long").append("minimum", 0L)).append("failure", nullable("string")));
        Document row = object(new Document("_id", uuid(true)).append("eventId", uuid(true))
                .append("environment", values("test", "staging", "production")).append("producer", values("billing"))
                .append("consumerScopeId", ref()).append("attemptGroupId", uuid(true)).append("purchaseId", uuid(true)).append("refundEffectId", uuid(true))
                .append("body", type("string").append("minLength", 1).append("maxLength", 16384)).append("digest", hash())
                .append("createdAt", type("date")).append("deliveredAt", nullable("date")).append("work", work));
        return new Document("$jsonSchema", row).append("$and", List.of(
                new Document("$expr", new Document("$eq", List.of("$_id", "$eventId"))),
                new Document("$or", List.of(new Document("work.owner", null).append("work.until", null),
                        new Document("work.owner", new Document("$type", "string")).append("work.until", new Document("$type", "date"))))));
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier"))))
            throw new IllegalStateException("Refund fan-out requires isolated writers");
        RefundCoreSchema.verify(db);
        Set<String> names = new HashSet<>(db.listCollectionNames().into(new ArrayList<>()));
        if (!names.containsAll(List.of("reservations", "attempt_groups"))) throw new IllegalStateException("Missing paid collections");
        if (names.contains(RefundFanout.OUTBOX)) verifyValidator(db);
        // Preflight existing named indexes before any mutation. Never drop or silently replace drift.
        for (Entry entry : indexes()) if (names.contains(entry.collection())) verifyIndex(db, entry, false);
        if (!names.contains(RefundFanout.OUTBOX)) db.createCollection(RefundFanout.OUTBOX,
                new CreateCollectionOptions().validationOptions(new ValidationOptions().validator(validator()).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        for (Entry entry : indexes()) db.getCollection(entry.collection()).createIndex(entry.keys(), new IndexOptions().name(entry.name()).unique(entry.unique()));
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        RefundCoreSchema.verify(db); verifyValidator(db);
        for (Entry entry : indexes()) verifyIndex(db, entry, true);
    }
    private static void verifyValidator(MongoDatabase db) {
        Document info = db.listCollections().filter(new Document("name", RefundFanout.OUTBOX)).first();
        if (info == null) throw new IllegalStateException("Missing refund outbox");
        Document options = info.get("options", Document.class);
        if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel")) || !"error".equals(options.getString("validationAction")))
            throw new IllegalStateException("Refund outbox validator mismatch");
        Set<String> allowed = new HashSet<>(List.of("_id_"));
        indexes().stream().filter(e -> RefundFanout.OUTBOX.equals(e.collection())).forEach(e -> allowed.add(e.name()));
        for (Document index : db.getCollection(RefundFanout.OUTBOX).listIndexes())
            if (!allowed.contains(index.getString("name")) || index.containsKey("expireAfterSeconds"))
                throw new IllegalStateException("Unexpected refund outbox index");
    }
    private static void verifyIndex(MongoDatabase db, Entry entry, boolean required) {
        Document found = db.getCollection(entry.collection()).listIndexes().into(new ArrayList<>()).stream()
                .filter(i -> entry.name().equals(i.getString("name"))).findFirst().orElse(null);
        if (found == null) { if (required) throw new IllegalStateException("Missing refund index"); return; }
        if (!entry.keys().equals(found.get("key"))
                || !new ArrayList<>(entry.keys().keySet()).equals(new ArrayList<>(found.get("key", Document.class).keySet()))
                || entry.unique() != found.getBoolean("unique", false)
                || found.getBoolean("sparse", false) || found.containsKey("partialFilterExpression") || found.containsKey("expireAfterSeconds")
                || found.containsKey("collation")) throw new IllegalStateException("Refund index mismatch");
    }
}
