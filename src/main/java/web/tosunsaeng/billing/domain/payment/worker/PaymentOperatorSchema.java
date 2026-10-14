package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;

/** Explicit additive schema. No TTL: retention/purge needs a separate operational decision. */
public final class PaymentOperatorSchema {
    public static final String COLLECTION = "payment_operator_receipts";
    private PaymentOperatorSchema() {}
    public static Document validator() {
        var hash = new Document("bsonType", "string").append("pattern", "^[0-9a-f]{64}(?![\\s\\S])");
        var properties = new Document("_id", new Document("bsonType", "string").append("pattern",
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"))
                .append("schemaVersion", new Document("bsonType", "long").append("enum", List.of(1L)))
                .append("action", new Document("enum", List.of("ACCOUNT", "AUTH")))
                .append("scope", new Document("enum", List.of("SANDBOX", "PRODUCTION", "SHARED")))
                .append("requestDigest", hash).append("targetDigest", hash).append("actorDigest", hash)
                .append("reason", new Document("enum", Arrays.stream(PaymentReviewOperator.Reason.values()).map(Enum::name).toList()))
                .append("count", new Document("bsonType", "long").append("minimum", 0L).append("maximum", 100L))
                .append("completedAt", new Document("bsonType", "date"));
        return new Document("$jsonSchema", new Document("bsonType", "object").append("additionalProperties", false)
                .append("required", new ArrayList<>(properties.keySet())).append("properties", properties));
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean isolated) {
        if (!isolated) throw mismatch();
        if (db.listCollections().filter(new Document("name", COLLECTION)).first() == null)
            db.createCollection(COLLECTION, new CreateCollectionOptions().validationOptions(new ValidationOptions()
                    .validator(validator()).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try {
            var meta = db.listCollections().filter(new Document("name", COLLECTION)).first();
            if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
            var options = meta.get("options", Document.class);
            if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                    || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false)) throw mismatch();
            simple(options);
            var indexes = db.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
            if (indexes.size() != 1) throw mismatch();
            var i = indexes.getFirst(); simple(i);
            if (!"_id_".equals(i.getString("name")) || !new Document("_id", 1).equals(i.get("key"))
                    || i.containsKey("expireAfterSeconds") || i.containsKey("partialFilterExpression")
                    || i.getBoolean("hidden", false) || i.getBoolean("sparse", false)) throw mismatch();
        } catch (RuntimeException invalid) { throw mismatch(); }
    }
    private static void simple(Document d) {
        Document c = d.get("collation", Document.class);
        if (c != null && !"simple".equals(c.getString("locale"))) throw mismatch();
    }
    private static IllegalStateException mismatch() { return new IllegalStateException("Operator receipt schema mismatch; explicit maintenance required"); }
}
