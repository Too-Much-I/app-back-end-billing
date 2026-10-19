package web.tosunsaeng.billing.domain.payment.provider;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;

/** Shared-project budget r1. This database must be shared by every caller of the RC project. */
public final class RevenueCatBudgetSchema {
    public static final String COLLECTION = "revenuecat_read_budgets";
    private RevenueCatBudgetSchema() {}
    public static Document validator() {
        Document slot = object(new Document("token", new Document("bsonType", "string")
                .append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])"))
                .append("until", type("date")));
        return new Document("$jsonSchema", object(new Document("_id", type("string"))
                .append("version", type("long").append("minimum", 1L)).append("updatedAt", type("date"))
                .append("blockedAuth", type("bool"))
                .append("starts", type("array").append("maxItems", 240).append("items", type("date")))
                .append("slots", type("array").append("maxItems", 4).append("items", slot))));
    }
    /** No payment recovery control exists in the shared budget DB; all project callers must be isolated. */
    public static void installUnderMaintenance(MongoDatabase db, boolean allProjectCallersIsolated) {
        if (!allProjectCallersIsolated) throw mismatch();
        if (db.listCollections().filter(new Document("name", COLLECTION)).first() == null)
            db.createCollection(COLLECTION, new CreateCollectionOptions().validationOptions(new ValidationOptions()
                    .validator(validator()).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try {
            Document meta = db.listCollections().filter(new Document("name", COLLECTION)).first();
            if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
            Document options = meta.get("options", Document.class);
            if (!validator().equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                    || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false)) throw mismatch();
            simple(options);
            var indexes = db.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
            if (indexes.size() != 1) throw mismatch();
            Document index = indexes.getFirst(); simple(index);
            if (!"_id_".equals(index.getString("name")) || !new Document("_id", 1).equals(index.get("key"))
                    || index.containsKey("expireAfterSeconds") || index.containsKey("partialFilterExpression")
                    || index.getBoolean("hidden", false) || index.getBoolean("sparse", false)) throw mismatch();
        } catch (RuntimeException e) { throw mismatch(); }
    }
    private static Document type(String value) { return new Document("bsonType", value); }
    private static Document object(Document properties) {
        return type("object").append("additionalProperties", false).append("required", new ArrayList<>(properties.keySet())).append("properties", properties);
    }
    private static void simple(Document d) {
        Document c = d.get("collation", Document.class); if (c != null && !"simple".equals(c.getString("locale"))) throw mismatch();
    }
    private static IllegalStateException mismatch() { return new IllegalStateException("RevenueCat shared budget schema mismatch; explicit maintenance required"); }
}
