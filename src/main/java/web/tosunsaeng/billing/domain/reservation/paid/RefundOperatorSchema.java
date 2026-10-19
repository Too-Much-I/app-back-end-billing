package web.tosunsaeng.billing.domain.reservation.paid;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;

/** Explicit maintenance only. Audit receipts are never automatically expired. */
public final class RefundOperatorSchema {
    public static final String COLLECTION = web.tosunsaeng.billing.domain.payment.worker.PaymentOperatorSchema.COLLECTION;
    private RefundOperatorSchema() {}
    public static Document validator() {
        Document fields = new Document("_id", new Document("bsonType", "string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"))
                .append("digest", hash()).append("actorDigest", hash()).append("targetDigest", hash())
                .append("environment", new Document("enum", List.of("test", "staging", "production")))
                .append("action", new Document("enum", Arrays.stream(RefundOperator.Action.values()).map(Enum::name).toList()))
                .append("reason", new Document("enum", Arrays.stream(RefundOperator.Reason.values()).map(Enum::name).toList()))
                .append("generation", new Document("bsonType", "long").append("minimum", 1L))
                .append("result", new Document("enum", List.of("RESUMED", "RESOLVED", "STILL_OPEN")))
                .append("completedAt", new Document("bsonType", "date"));
        Document refund = new Document("$jsonSchema", new Document("bsonType", "object").append("additionalProperties", false)
                .append("required", new ArrayList<>(fields.keySet())).append("properties", fields));
        return new Document("$or", List.of(web.tosunsaeng.billing.domain.payment.worker.PaymentOperatorSchema.validator(), refund));
    }
    private static Document hash() { return new Document("bsonType", "string").append("pattern", "^[0-9a-f]{64}(?![\\s\\S])"); }
    public static void installUnderMaintenance(MongoDatabase db, boolean isolated) {
        if (!isolated) throw new IllegalStateException("Isolate writers before installing operator schema");
        RefundFanoutSchema.verify(db);
        Document existing = db.listCollections().filter(new Document("name", COLLECTION)).first();
        if (existing == null)
            db.createCollection(COLLECTION, new CreateCollectionOptions().validationOptions(new ValidationOptions()
                    .validator(validator()).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        else if (web.tosunsaeng.billing.domain.payment.worker.PaymentOperatorSchema.validator().equals(existing.get("options", Document.class).get("validator"))) {
            web.tosunsaeng.billing.domain.payment.worker.PaymentOperatorSchema.verify(db);
            db.runCommand(new Document("collMod", COLLECTION).append("validator", validator()).append("validationLevel", "strict").append("validationAction", "error"));
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        Document info = db.listCollections().filter(new Document("name", COLLECTION)).first();
        if (info == null) throw new IllegalStateException("Missing refund operator schema");
        Document options = info.get("options", Document.class);
        var indexes = db.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
        if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                || !"error".equals(options.getString("validationAction")) || options.containsKey("collation") || options.getBoolean("capped", false)
                || indexes.size() != 1 || !"_id_".equals(indexes.getFirst().getString("name"))
                || indexes.getFirst().containsKey("expireAfterSeconds")) throw new IllegalStateException("Refund operator schema mismatch");
    }
}
