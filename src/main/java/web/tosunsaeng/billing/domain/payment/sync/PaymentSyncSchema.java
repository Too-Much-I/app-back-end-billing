package web.tosunsaeng.billing.domain.payment.sync;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** sync-command-r1. Explicit maintenance DDL; not a startup initializer. No TTL on pending work. */
public final class PaymentSyncSchema {
    public static final String COLLECTION = "payment_sync_commands";
    private PaymentSyncSchema() {}
    public static Document validator() {
        Document p = new Document();
        for (String f : List.of("_id", "environment", "userId", "operationId", "store", "appId", "transactionKey", "lookupKeyVersion",
                "payloadDigest", "accountId", "lifecycleId", "state")) p.put(f, new Document("bsonType", "string"));
        p.put("environment", new Document("enum", List.of("SANDBOX", "PRODUCTION")));
        p.put("store", new Document("enum", List.of("APP_STORE", "PLAY_STORE")));
        p.put("state", new Document("enum", List.of("PENDING", "APPLIED", "REJECTED", "REVIEW_REQUIRED", "BLOCKED_AUTH")));
        for (String f : List.of("_id", "operationId", "accountId", "lifecycleId"))
            p.put(f, new Document("bsonType", "string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"));
        for (String f : List.of("transactionKey", "payloadDigest"))
            p.put(f, new Document("bsonType", "string").append("pattern", "^[a-f0-9]{64}(?![\\s\\S])"));
        p.put("counted", new Document("bsonType", "bool"));
        p.put("version", new Document("bsonType", "long").append("minimum", 1L));
        p.put("digestVersion", new Document("bsonType", "long").append("enum", List.of(1L)));
        p.put("attempts", new Document("bsonType", "long").append("minimum", 0L));
        for (String f : List.of("purchaseId", "errorCode", "sourceObservationKey")) p.put(f, new Document("bsonType", List.of("string", "null")));
        for (String f : List.of("createdAt", "updatedAt")) p.put(f, new Document("bsonType", "date"));
        return new Document("$jsonSchema", new Document("bsonType", "object").append("additionalProperties", false)
                .append("required", new ArrayList<>(p.keySet())).append("properties", p))
                .append("$or", List.of(new Document("state", new Document("$in", List.of("PENDING", "REVIEW_REQUIRED", "BLOCKED_AUTH"))).append("counted", true),
                        new Document("state", new Document("$in", List.of("APPLIED", "REJECTED"))).append("counted", false)));
    }
    static Map<String, Document> indexes() {
        Map<String, Document> m = new LinkedHashMap<>();
        m.put("ux_payment_sync_operation", new Document("environment", 1).append("userId", 1).append("operationId", 1));
        m.put("ix_payment_sync_account_state", new Document("environment", 1).append("accountId", 1).append("state", 1).append("createdAt", 1));
        m.put("ix_payment_sync_due", new Document("state", 1).append("createdAt", 1));
        m.put("ix_payment_sync_source", new Document("environment", 1).append("sourceObservationKey", 1));
        return m;
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        if (db.listCollections().filter(new Document("name", COLLECTION)).first() == null) {
            db.createCollection(COLLECTION, new CreateCollectionOptions().validationOptions(new ValidationOptions().validator(validator())
                    .validationAction(ValidationAction.ERROR).validationLevel(ValidationLevel.STRICT)));
            indexes().forEach((name, keys) -> db.getCollection(COLLECTION).createIndex(keys, new IndexOptions().name(name).unique(name.startsWith("ux_"))));
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try {
            Document meta = db.listCollections().filter(new Document("name", COLLECTION)).first();
            if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
            Document options = meta.get("options", Document.class); simple(options);
            if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                    || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false)) throw mismatch();
            var actual = db.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
            if (actual.size() != indexes().size() + 1) throw mismatch();
            indexes().forEach((name, keys) -> {
                Document i = actual.stream().filter(a -> name.equals(a.getString("name"))).findFirst().orElseThrow(PaymentSyncSchema::mismatch);
                Document k = i.get("key", Document.class); simple(i);
                if (!keys.equals(k) || !new ArrayList<>(keys.keySet()).equals(new ArrayList<>(k.keySet()))
                        || name.startsWith("ux_") != i.getBoolean("unique", false) || i.getBoolean("sparse", false) || i.getBoolean("hidden", false)
                        || i.containsKey("expireAfterSeconds") || i.containsKey("partialFilterExpression")) throw mismatch();
            });
        } catch (RuntimeException e) { throw mismatch(); }
    }
    private static void simple(Document d) { var c = d.get("collation", Document.class); if (c != null && !"simple".equals(c.getString("locale"))) throw mismatch(); }
    private static IllegalStateException mismatch() { return new IllegalStateException("Payment sync schema mismatch; explicit maintenance required"); }
}
