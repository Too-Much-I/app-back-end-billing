package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema.*;

/** Optional r7 refund schema. No startup DDL, no changes to earlier exact manifests. */
public final class RefundCoreSchema {
    private RefundCoreSchema() {}
    static Map<String, Definition> manifest() {
        Map<String, Definition> result = PurchaseCoreSchema.manifest();
        var purchase = result.get("purchases");
        optional(purchase.validator(), "refund", object(new Document("effectId", id()).append("appliedAt", type("date"))
                .append("providerConfirmedAt", nullable("date")).append("refundedTotal", nullable("decimal"))
                .append("currency", nullable("string")).append("basis", nullable("string")).append("amountSource", nullable("string"))
                .append("amountVersion", number(0))));
        optional(purchase.validator(), "refundWork", object(new Document("phase", values("RESERVATIONS", "GROUPS", "DONE"))
                .append("state", values("READY", "REVIEW_REQUIRED")).append("afterId", nullable("string"))
                .append("owner", nullable("string")).append("until", nullable("date")).append("leaseVersion", number(1))
                .append("generation", number(1)).append("nextAttemptAt", nullable("date")).append("retryStartedAt", type("date"))
                .append("attempts", number(0)).append("failure", nullable("string"))));
        properties(purchase.validator()).get("refund", Document.class).put("oneOf", List.of(
                new Document("properties", new Document("refundedTotal", type("null")).append("currency", type("null"))
                        .append("basis", type("null")).append("amountSource", type("null")).append("amountVersion", number(0).append("enum", List.of(0L)))),
                new Document("properties", new Document("refundedTotal", type("decimal").append("minimum", 0))
                        .append("currency", currency()).append("basis", values("STORE_REFUND_GROSS"))
                        .append("amountSource", values("REVENUECAT_API", "REVENUECAT_WEBHOOK", "GOOGLE_ORDERS")).append("amountVersion", number(1)))));
        addIndex(result, "purchases", "ix_purchase_refund_due", "environment", "refundWork.state", "refundWork.nextAttemptAt", "_id");
        Document evidence = object(new Document("revision", number(1).append("enum", List.of(1L)))
                .append("source", values("REVENUECAT_API", "REVENUECAT_WEBHOOK", "GOOGLE_ORDERS"))
                .append("evidenceKey", type("string").append("pattern", "^[a-f0-9]{64}(?![\\s\\S])"))
                .append("providerConfirmedAt", nullable("date")).append("amountMode", values("UNKNOWN", "CUMULATIVE"))
                .append("amount", nullable("decimal").append("minimum", 0)).append("currency", nullable("string"))
                .append("basis", nullable("string")));
        evidence.put("oneOf", List.of(
                new Document("properties", new Document("amountMode", values("UNKNOWN")).append("amount", type("null"))
                        .append("currency", type("null")).append("basis", type("null"))),
                new Document("properties", new Document("amountMode", values("CUMULATIVE")).append("amount", type("decimal").append("minimum", 0))
                        .append("currency", currency()).append("basis", values("STORE_REFUND_GROSS")))));
        for (String name : List.of("payment_provider_inbox", "payment_purchase_cases")) {
            Document props = properties(result.get(name).validator());
            props.put("digestVersion", number(1).append("enum", List.of(1L, 2L)));
            props.get("normalizedObservation", Document.class).get("properties", Document.class).put("refundEvidence", evidence);
        }
        Document review = result.get("payment_purchase_cases").validator();
        optional(review, "reviewState", values("OPEN", "RESOLVED"));
        for (String name : List.of("purchaseId", "inquiryId", "actorDigest")) optional(review, name, nullable("string"));
        optional(review, "resolvedAt", nullable("date"));
        addIndex(result, "payment_purchase_cases", "ix_payment_case_refund_review", "environment", "reviewState", "reason", "createdAt", "_id");
        var ledger = result.get("payment_ledger");
        Document refundLedger = new Document("$jsonSchema", object(new Document("_id", id()).append("effectKey", type("string"))
                .append("type", values("ACCESS_REVOKED", "REFUND_AMOUNT_CONFIRMED", "TIMELINE_REFLOWED"))
                .append("purchaseId", id()).append("entitlementId", nullable("string")).append("occurredAt", type("date"))
                .append("sourceKind", values("VERIFIED_OBSERVATION")).append("sourceObservationKey", type("string"))
                .append("detail", type("object"))));
        result.put("payment_ledger", new Definition(new Document("$or", List.of(ledger.validator(), refundLedger)), ledger.indexes()));
        return result;
    }
    public static void verify(MongoDatabase db) {
        manifest().forEach((name, definition) -> verifyOne(db, name, definition));
    }
    public static void upgradeUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier"))))
            throw new IllegalStateException("Refund migration requires isolated writers and ALL_BLOCKED");
        Map<String, Definition> old = PurchaseCoreSchema.manifest(), target = manifest();
        // Validate all collections, including exact intermediates, before any DDL.
        target.forEach((name, next) -> {
            boolean valid = false;
            for (Document validator : List.of(old.get(name).validator(), next.validator()))
                for (var indexes : List.of(old.get(name).indexes(), next.indexes())) {
                    try { verifyOne(db, name, new Definition(validator, indexes)); valid = true; }
                    catch (IllegalStateException ignored) { /* No arbitrary drift accepted. */ }
                }
            if (!valid) throw new IllegalStateException("Refund migration schema mismatch");
        });
        target.forEach((name, next) -> {
            db.runCommand(new Document("collMod", name).append("validator", next.validator())
                    .append("validationLevel", "strict").append("validationAction", "error"));
            for (Index index : next.indexes()) if (!old.get(name).indexes().contains(index))
                db.getCollection(name).createIndex(index.keys(), new IndexOptions().name(index.name()).unique(index.unique()));
        });
        verify(db);
    }
    private static void addIndex(Map<String, Definition> map, String collection, String name, String... keys) {
        var old = map.get(collection); var indexes = new ArrayList<>(old.indexes());
        Document ordered = new Document(); for (String key : keys) ordered.put(key, 1);
        indexes.add(new Index(name, ordered, false, false)); map.put(collection, new Definition(old.validator(), indexes));
    }
    private static Document properties(Document schema) { return schema.get("$jsonSchema", Document.class).get("properties", Document.class); }
    private static void optional(Document schema, String key, Document rule) { properties(schema).put(key, rule); }
    private static Document object(Document properties) {
        return type("object").append("required", new ArrayList<>(properties.keySet())).append("additionalProperties", false).append("properties", properties);
    }
    private static Document type(String value) { return new Document("bsonType", value); }
    private static Document nullable(String value) { return new Document("bsonType", List.of(value, "null")); }
    private static Document number(long min) { return type("long").append("minimum", min); }
    private static Document values(String... values) { return new Document("enum", List.of(values)); }
    private static Document currency() { return type("string").append("pattern", "^[A-Z]{3}(?![\\s\\S])"); }
    private static Document id() { return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"); }
}
