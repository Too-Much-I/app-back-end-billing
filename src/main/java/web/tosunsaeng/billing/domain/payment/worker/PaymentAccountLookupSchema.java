package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.MongoDatabase;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** Explicit cursor-r2 migration; never run DDL from a worker or a startup constructor. */
public final class PaymentAccountLookupSchema {
    public static final String COLLECTION = "payment_reconcile_cursors";
    private PaymentAccountLookupSchema() {}
    public static Document validator() {
        return validator(true);
    }
    private static Document validator(boolean periodic) {
        Document envelope = object(new Document("revision", new Document("bsonType", "long").append("enum", List.of(1L)))
                .append("keyId", type("string")).append("nonce", type("string")).append("ciphertext", type("string")));
        envelope.put("bsonType", List.of("object", "null"));
        Document lookup = object(new Document("owner", nullable("string")).append("until", nullable("date"))
                .append("leaseVersion", number(1)).append("generation", number(1)).append("attempts", number(1))
                .append("fenceVersion", number(0)).append("pages", number(0)).append("reference", id())
                .append("cursor", envelope).append("failure", nullable("string")));
        Document p = new Document("_id", id()).append("accountId", id()).append("kind", new Document("enum", List.of("ACCOUNT_LOOKUP")))
                .append("version", number(1)).append("nextAttemptAt", nullable("date")).append("lastActivityAt", type("date"));
        Document schema = object(p); p.put("lookup", lookup); // Optional for existing/new account writer rows.
        if (periodic) { p.put("reconcileAt", nullable("date")); p.put("reconcileStartedAt", nullable("date")); }
        return new Document("$jsonSchema", schema);
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(d -> !"ALL_BLOCKED".equals(d.getString("barrier")))) throw mismatch();
        Document options = options(db); indexes(db);
        Object previous = options.get("validator");
        if (previous != null && !new Document().equals(previous) && !validator().equals(previous)) throw mismatch();
        if (validator().equals(previous)) { verify(db); return; }
        if (db.getCollection(COLLECTION).find(new Document("$nor", List.of(validator()))).limit(1).first() != null) throw mismatch();
        db.runCommand(new Document("collMod", COLLECTION).append("validator", validator()).append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        Document options = options(db); indexes(db);
        if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                || !"error".equals(options.getString("validationAction"))) throw mismatch();
    }
    /** cursor-r1 -> r2, optional periodic schedule; existing indexes/rows are preserved. */
    public static void upgradeReconciliationUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(d -> !"ALL_BLOCKED".equals(d.getString("barrier")))) throw mismatch();
        Document options = options(db); indexes(db);
        if (validator().equals(options.get("validator"))) { verify(db); return; }
        if (!validator(false).equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                || !"error".equals(options.getString("validationAction"))) throw mismatch();
        if (db.getCollection(COLLECTION).find(new Document("$nor", List.of(validator()))).limit(1).first() != null) throw mismatch();
        db.runCommand(new Document("collMod", COLLECTION).append("validator", validator()).append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
    private static Document options(MongoDatabase db) {
        Document meta = db.listCollections().filter(new Document("name", COLLECTION)).first();
        if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
        Document options = meta.get("options", Document.class); simple(options);
        if (options.getBoolean("capped", false)) throw mismatch(); return options;
    }
    private static void indexes(MongoDatabase db) {
        var actual = db.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
        Map<String, Document> expected = Map.of("_id_", new Document("_id", 1),
                "ux_payment_reconcile_cursor", new Document("accountId", 1).append("kind", 1),
                "ix_payment_reconcile_due", new Document("nextAttemptAt", 1).append("_id", 1));
        if (actual.size() != expected.size()) throw mismatch();
        expected.forEach((name, keys) -> {
            Document i = actual.stream().filter(d -> name.equals(d.getString("name"))).findFirst().orElseThrow(PaymentAccountLookupSchema::mismatch);
            Document k = i.get("key", Document.class); simple(i);
            if (!keys.equals(k) || !new ArrayList<>(keys.keySet()).equals(new ArrayList<>(k.keySet()))
                    || !name.equals("_id_") && name.startsWith("ux_") != i.getBoolean("unique", false)
                    || i.containsKey("expireAfterSeconds") || i.containsKey("partialFilterExpression") || i.getBoolean("sparse", false) || i.getBoolean("hidden", false)) throw mismatch();
        });
    }
    private static Document id() { return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"); }
    private static Document number(long min) { return type("long").append("minimum", min); }
    private static Document nullable(String type) { return new Document("bsonType", List.of(type, "null")); }
    private static Document type(String type) { return new Document("bsonType", type); }
    private static Document object(Document p) { return type("object").append("additionalProperties", false).append("required", new ArrayList<>(p.keySet())).append("properties", p); }
    private static void simple(Document d) { Document c = d.get("collation", Document.class); if (c != null && !"simple".equals(c.getString("locale"))) throw mismatch(); }
    private static IllegalStateException mismatch() { return new IllegalStateException("Payment account lookup schema mismatch; explicit maintenance required"); }
}
