package web.tosunsaeng.billing.domain.payment.provider;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** Purchase lookup reference r1, explicit isolated maintenance only. No TTL. */
public final class PurchaseReferenceSchema {
    public static final String COLLECTION = "payment_purchase_references";
    private PurchaseReferenceSchema() {}
    public static Document validator() {
        Document p = new Document();
        for (String field : List.of("_id", "environment", "project", "store", "appId", "transactionKey", "lookupKeyVersion", "accountId")) p.put(field, type("string"));
        p.put("environment", new Document("enum", List.of("SANDBOX", "PRODUCTION")));
        p.put("store", new Document("enum", List.of("APP_STORE", "PLAY_STORE")));
        p.put("transactionKey", type("string").append("pattern", "^[a-f0-9]{64}(?![\\s\\S])"));
        for (String f : List.of("_id", "accountId")) p.put(f, type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"));
        p.put("envelope", object(new Document("revision", type("long").append("enum", List.of(1L)))
                .append("keyId", type("string")).append("nonce", type("string")).append("ciphertext", type("string"))));
        p.put("version", type("long").append("minimum", 1L)); p.put("retentionExpiresAt", type("date"));
        return new Document("$jsonSchema", object(p));
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        if (db.listCollections().filter(new Document("name", COLLECTION)).first() == null)
            db.createCollection(COLLECTION, new CreateCollectionOptions().validationOptions(new ValidationOptions()
                    .validator(validator()).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try {
            var meta = db.listCollections().filter(new Document("name", COLLECTION)).first();
            if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
            Document options = meta.get("options", Document.class); simple(options);
            if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                    || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false)) throw mismatch();
            var indexes = db.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
            if (indexes.size() != 1) throw mismatch();
            Document index = indexes.getFirst(); simple(index);
            if (!"_id_".equals(index.getString("name")) || !new Document("_id", 1).equals(index.get("key"))
                    || index.containsKey("expireAfterSeconds") || index.containsKey("partialFilterExpression")
                    || index.getBoolean("hidden", false) || index.getBoolean("sparse", false)) throw mismatch();
        } catch (RuntimeException e) { throw mismatch(); }
    }
    private static Document type(String value) { return new Document("bsonType", value); }
    private static Document object(Document p) { return type("object").append("additionalProperties", false).append("required", new ArrayList<>(p.keySet())).append("properties", p); }
    private static void simple(Document d) { Document c = d.get("collation", Document.class); if (c != null && !"simple".equals(c.getString("locale"))) throw mismatch(); }
    private static IllegalStateException mismatch() { return new IllegalStateException("Purchase reference schema mismatch; explicit maintenance required"); }
}
