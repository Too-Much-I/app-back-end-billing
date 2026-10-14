package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** payment-purchase-core-r6: source worker and minimum exam usage, explicit maintenance only. */
public final class PurchaseCoreSchema {
    private PurchaseCoreSchema() {}
    record Index(String name, Document keys, boolean unique, boolean ttl) {}
    record Definition(Document validator, List<Index> indexes) {}

    static Map<String, Definition> manifest() {
        var result = examOperationManifest();
        Definition old = result.get("payment_ledger");
        Document event = schema(fields("_id", id(), "effectKey", type("string"), "type", values("EXAM_STATUS"),
                "purchaseId", id(), "entitlementId", id(), "occurredAt", type("date"),
                "sourceKind", values("EXAM_EVENT"), "eventId", id(),
                "detail", schema(fields("attemptGroupId", id(), "sessionId", type("string"),
                        "targetStatus", values("GRADING", "COMPLETED", "RETAKE_AVAILABLE"), "occurredAt", type("date"))).get("$jsonSchema")));
        result.put("payment_ledger", new Definition(new Document("$or", List.of(old.validator(), event)), old.indexes()));
        return result;
    }
    static Map<String, Definition> examOperationManifest() {
        var result = manifest(true, true, true);
        Definition old = result.get("payment_ledger");
        Document exam = schema(fields("_id", id(), "effectKey", type("string"),
                "type", values("EXAM_RESERVED", "EXAM_CONFIRMED", "EXAM_CANCELED", "EXAM_EXPIRED"),
                "purchaseId", id(), "entitlementId", id(), "occurredAt", type("date"),
                "sourceKind", values("EXAM_OPERATION"), "operationId", id(), "reservationId", id(),
                "detail", schema(fields("attemptGroupId", id(), "sessionId", type("string"))).get("$jsonSchema")));
        result.put("payment_ledger", new Definition(new Document("$or", List.of(old.validator(), exam)), old.indexes()));
        return result;
    }
    static Map<String, Definition> manifest(boolean moneyEvidence) {
        return manifest(moneyEvidence, false);
    }
    static Map<String, Definition> manifest(boolean moneyEvidence, boolean webhook) {
        return manifest(moneyEvidence, webhook, false);
    }
    private static Map<String, Definition> manifest(boolean moneyEvidence, boolean webhook, boolean worker) {
        Map<String, Definition> all = new LinkedHashMap<>();
        Document purchase = fields("_id", id(), "environment", env(), "store", values("APP_STORE", "PLAY_STORE"),
                "appId", type("string"), "transactionKey", hash(), "lookupKeyVersion", type("string"),
                "accountId", id(), "ownerRefId", id(), "storeProductId", nullable("string"), "purchasedAt", nullable("date"),
                "productSnapshot", product(), "money", money(),
                "purchaseState", values("VERIFIED", "REFUNDED", "REVOKED"), "accessState", values("NOT_GRANTED", "ALLOWED", "REVOKED"),
                "processingStatus", values("READY", "NEEDS_LOOKUP", "REVIEW_REQUIRED"), "version", positive(),
                "firstVerifiedAt", type("date"), "lastVerifiedAt", type("date"), "retentionExpiresAt", nullable("date"));
        all.put("purchases", definition(schema(purchase), index("ux_purchase_transaction", true, "environment", "store", "appId", "transactionKey"),
                index("ix_purchase_account", false, "accountId", "purchaseState")));
        all.put("subscription_entitlements", definition(schema(fields("_id", id(), "purchaseId", id(), "accountId", id(),
                "sequence", positive(), "durationSeconds", positive(), "originalStartsAt", type("date"), "originalEndsAt", type("date"),
                "startsAt", type("date"), "endsAt", type("date"), "revokedAt", nullable("date"), "version", positive())),
                index("ux_entitlement_purchase", true, "purchaseId"), index("ux_entitlement_sequence", true, "accountId", "sequence"),
                index("ix_entitlement_account_end", false, "accountId", "endsAt")));
        all.put("payment_timelines", definition(schema(fields("_id", id(), "accountId", id(), "environment", env(),
                "nextSequence", positive(), "timelineEnd", type("date"), "version", positive())),
                index("ux_payment_timeline_account", true, "environment", "accountId")));
        all.put("payment_ledger", definition(schema(fields("_id", id(), "effectKey", type("string"),
                "type", moneyEvidence ? values("PURCHASE_OBSERVED", "PURCHASE_VERIFIED", "MONEY_CONFIRMED")
                        : values("PURCHASE_OBSERVED", "PURCHASE_VERIFIED"), "purchaseId", id(), "entitlementId", nullable("string"),
                "occurredAt", type("date"), "sourceKind", values("VERIFIED_OBSERVATION"), "sourceObservationKey", hash(), "detail", type("object"))),
                index("ux_payment_ledger_effect", true, "effectKey"), index("ix_payment_ledger_purchase", false, "purchaseId", "occurredAt")));
        Document source = fields("_id", id(), "environment", env(), "providerEventKey", hash(), "digestVersion", new Document("bsonType", "long").append("enum", List.of(1L)),
                "digest", hash(), "normalizedObservation", observation(), "accountId", nullable("string"), "lifecycleId", nullable("string"),
                "counted", type("bool"), "state", values("RECEIVED", "APPLIED", "QUARANTINED"), "version", positive(),
                "attempts", new Document("bsonType", "long").append("minimum", 0L), "receivedAt", type("date"), "retentionDeadline", type("date"), "purgeAt", type("date"));
        if (webhook) {
            source.put("receptionDigest", hash());
            source.put("receptionLookupKeyVersion", type("string"));
            source.get("normalizedObservation", Document.class).put("bsonType", List.of("object", "null"));
            source.put("state", values("RECEIVED", "APPLIED", "QUARANTINED", "IGNORED"));
        }
        if (worker) source.put("work", schema(fields("owner", nullable("string"), "until", nullable("date"),
                "leaseVersion", positive(), "generation", positive(), "fenceVersion", new Document("bsonType", "long").append("minimum", 0L),
                "nextAttemptAt", nullable("date"), "retryStartedAt", type("date"), "attempts", positive(), "failure", nullable("string"))).get("$jsonSchema"));
        Document sourceSchema = schema(source, "purgeAt", "receptionDigest", "receptionLookupKeyVersion", "work");
        sourceSchema.put("$or", List.of(new Document("state", "RECEIVED").append("purgeAt", new Document("$exists", false)),
                new Document("state", new Document("$in", webhook ? List.of("APPLIED", "QUARANTINED", "IGNORED") : List.of("APPLIED", "QUARANTINED"))).append("counted", false)
                        .append("purgeAt", new Document("$type", "date"))));
        if (webhook) sourceSchema.put("$and", List.of(new Document("$or", List.of(
                new Document("state", new Document("$ne", "RECEIVED")), new Document("normalizedObservation", new Document("$type", "object")))),
                new Document("$or", List.of(new Document("normalizedObservation", new Document("$type", "object")),
                        new Document("receptionDigest", new Document("$type", "string"))))));
        all.put("payment_provider_inbox", definition(sourceSchema, index("ux_payment_provider_event", true, "environment", "providerEventKey"),
                index("ix_payment_provider_due", false, "state", "receivedAt"), new Index("ttl_payment_provider_purge", new Document("purgeAt", 1), false, true)));
        if (worker) {
            var indexes = new ArrayList<>(all.get("payment_provider_inbox").indexes());
            indexes.add(index("ix_payment_provider_work_due", false, "environment", "work.nextAttemptAt", "work.until"));
            all.put("payment_provider_inbox", new Definition(sourceSchema, indexes));
        }
        Document review = new Document(source);
        review.remove("work");
        review.remove("attempts"); review.remove("purgeAt"); review.remove("retentionDeadline");
        review.put("state", values("REVIEW_REQUIRED")); review.put("reason", type("string")); review.put("createdAt", type("date"));
        all.put("payment_purchase_cases", definition(schema(review, "receptionDigest", "receptionLookupKeyVersion"), index("ux_payment_case_event", true, "environment", "providerEventKey"),
                index("ix_payment_case_account", false, "accountId", "state"),
                index("ix_payment_case_transaction", false, "environment", "normalizedObservation.store", "normalizedObservation.appId", "normalizedObservation.transactionKey")));
        return all;
    }

    /** Only creates missing empty collections; never repairs an existing incompatible schema or drops indexes. */
    public static void installUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        if (!writersIsolated) throw mismatch();
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        for (var entry : manifest().entrySet()) {
            if (db.listCollections().filter(new Document("name", entry.getKey())).first() != null) {
                verifyOne(db, entry.getKey(), entry.getValue()); continue;
            }
            db.createCollection(entry.getKey(), new CreateCollectionOptions().validationOptions(new ValidationOptions()
                    .validator(entry.getValue().validator()).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
            for (Index index : entry.getValue().indexes()) {
                var options = new IndexOptions().name(index.name()).unique(index.unique());
                if (index.ttl()) options.expireAfter(0L, TimeUnit.SECONDS);
                db.getCollection(entry.getKey()).createIndex(index.keys(), options);
            }
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try { manifest().forEach((name, definition) -> verifyOne(db, name, definition)); }
        catch (RuntimeException error) { RefundCoreSchema.verify(db); }
    }
    /** Explicit r1 -> r2 migration; all old writers must be stopped. No automatic rollback. */
    public static void upgradeMoneyEvidenceUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!writersIsolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        try { manifest(true).forEach((name, definition) -> verifyOne(db, name, definition)); return; }
        catch (IllegalStateException oldOrInvalid) { /* Verify exact old manifest before modifying. */ }
        try { verify(db); return; } catch (IllegalStateException oldOrInvalid) { /* Already r3 is also valid. */ }
        try { examOperationManifest().forEach((name, definition) -> verifyOne(db, name, definition)); return; }
        catch (IllegalStateException earlier) { /* Exact prior version only. */ }
        manifest(false).forEach((name, definition) -> verifyOne(db, name, definition));
        db.runCommand(new Document("collMod", "payment_ledger").append("validator", manifest(true).get("payment_ledger").validator())
                .append("validationLevel", "strict").append("validationAction", "error"));
        manifest(true).forEach((name, definition) -> verifyOne(db, name, definition));
    }
    /** Resumable r2 -> r3; no indexes/data change and no automatic migration on application startup. */
    public static void upgradeWebhookUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!writersIsolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        try { verify(db); return; } catch (IllegalStateException earlier) { /* Already r4 needs no downgrade. */ }
        try { examOperationManifest().forEach((name, definition) -> verifyOne(db, name, definition)); return; }
        catch (IllegalStateException earlier) { /* Exact prior version only. */ }
        Map<String, Definition> old = manifest(true);
        Map<String, Definition> target = manifest(true, true);
        Set<String> changed = Set.of("payment_provider_inbox", "payment_purchase_cases");
        // Check every collection before the first DDL; a half-completed exact migration can be resumed.
        target.forEach((name, definition) -> {
            try { verifyOne(db, name, definition); }
            catch (IllegalStateException e) { if (!changed.contains(name)) throw e; verifyOne(db, name, old.get(name)); }
        });
        for (String name : changed) db.runCommand(new Document("collMod", name).append("validator", target.get(name).validator())
                .append("validationLevel", "strict").append("validationAction", "error"));
        target.forEach((name, definition) -> verifyOne(db, name, definition));
    }
    /** r3 -> r4, resumable after validator or index creation, without dropping existing indexes. */
    public static void upgradeWorkerUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        String name = "payment_provider_inbox";
        manifest().forEach((n, d) -> {
            if (!n.equals(name)) {
                try { verifyOne(db, n, d); }
                catch (IllegalStateException previous) {
                    if (!n.equals("payment_ledger")) throw previous;
                    try { verifyOne(db, n, examOperationManifest().get(n)); }
                    catch (IllegalStateException beforeExam) { verifyOne(db, n, manifest(true, true, true).get(n)); }
                }
            }
        });
        Definition old = manifest(true, true).get(name), target = manifest().get(name);
        boolean valid = false;
        for (var v : List.of(old.validator(), target.validator())) for (var indexes : List.of(old.indexes(), target.indexes())) {
            try { verifyOne(db, name, new Definition(v, indexes)); valid = true; } catch (IllegalStateException ignored) { /* Exact permitted intermediate only. */ }
        }
        if (!valid) throw mismatch();
        db.runCommand(new Document("collMod", name).append("validator", target.validator()).append("validationLevel", "strict").append("validationAction", "error"));
        Index index = target.indexes().getLast(); db.getCollection(name).createIndex(index.keys(), new IndexOptions().name(index.name()).unique(false));
        try { verify(db); } catch (IllegalStateException previous) {
            try { examOperationManifest().forEach((n, d) -> verifyOne(db, n, d)); }
            catch (IllegalStateException beforeExam) { manifest(true, true, true).forEach((n, d) -> verifyOne(db, n, d)); }
        }
    }
    /** Explicit r4/r5 -> r6, prior writers isolated; no automatic startup modification. */
    public static void upgradeExamUsageUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        try { verify(db); return; } catch (IllegalStateException previous) { /* Verify exact r4 before DDL. */ }
        try { examOperationManifest().forEach((name, definition) -> verifyOne(db, name, definition)); }
        catch (IllegalStateException notR5) { manifest(true, true, true).forEach((name, definition) -> verifyOne(db, name, definition)); }
        db.runCommand(new Document("collMod", "payment_ledger").append("validator", manifest().get("payment_ledger").validator())
                .append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
    static void verifyOne(MongoDatabase db, String name, Definition definition) {
        var meta = db.listCollections().filter(new Document("name", name)).first();
        if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
        Document options = meta.get("options", Document.class);
        if (!definition.validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false)) throw mismatch();
        simple(options);
        List<Document> actual = db.getCollection(name).listIndexes().into(new ArrayList<>());
        if (actual.size() != definition.indexes().size() + 1) throw mismatch();
        for (Index index : definition.indexes()) {
            Document found = actual.stream().filter(i -> index.name().equals(i.getString("name"))).findFirst().orElseThrow(PurchaseCoreSchema::mismatch);
            Document keys = found.get("key", Document.class);
            if (!index.keys().equals(keys) || !new ArrayList<>(index.keys().keySet()).equals(new ArrayList<>(keys.keySet()))
                    || index.unique() != found.getBoolean("unique", false) || found.getBoolean("hidden", false) || found.getBoolean("sparse", false)
                    || found.containsKey("partialFilterExpression") || index.ttl() != found.containsKey("expireAfterSeconds")) throw mismatch();
            if (index.ttl() && (!(found.get("expireAfterSeconds") instanceof Number n) || n.longValue() != 0)) throw mismatch();
            simple(found);
        }
    }
    private static Document observation() {
        Document d = fields("environment", env(), "store", values("APP_STORE", "PLAY_STORE"), "appId", type("string"),
                "transactionKey", hash(), "lookupKeyVersion", type("string"), "kind", values("PURCHASE", "REFUND", "REVOKE"),
                "ownerRefId", nullable("string"), "storeProductId", nullable("string"), "purchasedAt", nullable("date"), "money", money());
        return schema(d).get("$jsonSchema", Document.class);
    }
    private static Document money() {
        Document d = schema(fields("amount", new Document("bsonType", "decimal").append("minimum", 0),
                "currency", type("string").append("pattern", "^[A-Z]{3}(?![\\s\\S])"), "basis", values("STORE_PAID"))).get("$jsonSchema", Document.class);
        d.put("bsonType", List.of("object", "null")); return d;
    }
    private static Document product() {
        Document d = schema(fields("offerCode", values("PREMIUM_1D", "PREMIUM_3D", "PREMIUM_7D", "PREMIUM_14D", "PREMIUM_28D"),
                "durationSeconds", positive().append("enum", List.of(86400L, 259200L, 604800L, 1209600L, 2419200L)),
                "catalogVersion", positive(), "storeProductId", type("string"), "rcProductId", type("string"))).get("$jsonSchema", Document.class);
        d.put("bsonType", List.of("object", "null")); return d;
    }
    private static void simple(Document d) {
        var c = d.get("collation", Document.class); if (c != null && !"simple".equals(c.getString("locale"))) throw mismatch();
    }
    private static Definition definition(Document schema, Index... indexes) { return new Definition(schema, List.of(indexes)); }
    private static Index index(String name, boolean unique, String... names) {
        Document keys = new Document(); for (String n : names) keys.put(n, 1); return new Index(name, keys, unique, false);
    }
    private static Document schema(Document properties, String... optional) {
        List<String> required = new ArrayList<>(properties.keySet()); required.removeAll(List.of(optional));
        return new Document("$jsonSchema", new Document("bsonType", "object").append("required", required)
                .append("additionalProperties", false).append("properties", properties));
    }
    private static Document fields(Object... pairs) {
        Document fields = new Document(); for (int i = 0; i < pairs.length; i += 2) fields.put((String) pairs[i], pairs[i + 1]); return fields;
    }
    private static Document type(String type) { return new Document("bsonType", type); }
    private static Document nullable(String type) { return new Document("bsonType", List.of(type, "null")); }
    private static Document values(String... values) { return new Document("enum", List.of(values)); }
    private static Document positive() { return new Document("bsonType", "long").append("minimum", 1L); }
    private static Document env() { return values("SANDBOX", "PRODUCTION"); }
    private static Document id() { return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"); }
    private static Document hash() { return type("string").append("pattern", "^[a-f0-9]{64}(?![\\s\\S])"); }
    private static IllegalStateException mismatch() { return new IllegalStateException("Purchase core schema mismatch; explicit maintenance required"); }
}
