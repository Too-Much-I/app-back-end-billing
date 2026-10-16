package web.tosunsaeng.billing.domain.examdeletion;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;

/** Explicit maintenance only. No TTL and no implicit upgrade/drop of an existing collection. */
public final class DeletionSchema {
    private DeletionSchema() {}
    private record Index(String name, Document keys, boolean unique) {}
    private static List<Index> indexes(String name) {
        return name.equals(DeletionService.FACTS) ? List.of(
                new Index("ux_exam_deletion_fact_session", new Document("environment", 1).append("sessionId", 1), true),
                new Index("ix_exam_deletion_fact_group", new Document("environment", 1).append("attemptGroupId", 1), false)) : List.of(
                new Index("ux_exam_deletion_receipt_event", new Document("environment", 1).append("eventId", 1), true),
                new Index("ix_exam_deletion_receipt_review", new Document("environment", 1).append("detailReviewAt", 1).append("_id", 1), false));
    }
    private static Document type(String name) { return new Document("bsonType", name); }
    private static Document values(String... values) { return type("string").append("enum", List.of(values)); }
    private static Document uuid(boolean v4) {
        return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-" + (v4 ? "4[0-9a-f]{3}-[89ab]" : "[0-9a-f]{4}-[0-9a-f]")
                + "[0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])");
    }
    public static Document validator(String name) {
        if (!Set.of(DeletionService.FACTS, DeletionService.RECEIPTS).contains(name)) throw new IllegalArgumentException("Unknown deletion collection");
        Document fields = new Document("_id", type("string")).append("environment", values("test", "staging", "production"))
                .append("sessionId", type("string").append("minLength", 1).append("maxLength", 128)).append("attemptGroupId", uuid(true))
                .append("eventId", uuid(true)).append("payloadDigest", type("string").append("pattern", "^[0-9a-f]{64}(?![\\s\\S])"))
                .append("version", type("long").append("enum", List.of(1L)));
        if (name.equals(DeletionService.FACTS)) fields.append("deletionOperationId", uuid(true)).append("userId", uuid(false))
                .append("schemaVersion", type("long").append("enum", List.of(1L))).append("deletedAt", type("date"))
                .append("appliedAt", type("date")).append("disposition", values("SESSION_ONLY", "CURRENT_GROUP_BLOCKED"));
        else fields.append("processedAt", type("date")).append("detailReviewAt", type("date"))
                .append("disposition", values("APPLIED", "ALREADY_DELETED"));
        return new Document("$jsonSchema", type("object").append("additionalProperties", false)
                .append("required", new ArrayList<>(fields.keySet())).append("properties", fields));
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean writersStopped) {
        if (!writersStopped) throw new IllegalStateException("Deletion schema requires stopped writers");
        Set<String> existing = new HashSet<>(db.listCollectionNames().into(new ArrayList<>()));
        for (String name : List.of(DeletionService.FACTS, DeletionService.RECEIPTS)) {
            if (existing.contains(name)) { verifyCollection(db, name); continue; }
            db.createCollection(name, new CreateCollectionOptions().validationOptions(new ValidationOptions()
                    .validator(validator(name)).validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
            for (Index index : indexes(name)) db.getCollection(name).createIndex(index.keys(),
                    new IndexOptions().name(index.name()).unique(index.unique()));
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        for (String name : List.of(DeletionService.FACTS, DeletionService.RECEIPTS)) verifyCollection(db, name);
    }
    private static void verifyCollection(MongoDatabase db, String name) {
        Document meta = db.listCollections().filter(new Document("name", name)).first();
        if (meta == null) throw new IllegalStateException("Deletion schema missing");
        Document options = meta.get("options", Document.class);
        if (options == null || !validator(name).equals(options.get("validator"))
                || !"strict".equals(options.getString("validationLevel")) || !"error".equals(options.getString("validationAction")))
            throw new IllegalStateException("Deletion validator mismatch");
        List<Document> actual = db.getCollection(name).listIndexes().into(new ArrayList<>());
        if (actual.size() != 3) throw new IllegalStateException("Deletion index inventory mismatch");
        for (Index index : indexes(name)) {
            Document found = actual.stream().filter(i -> index.name().equals(i.getString("name"))).findFirst().orElseThrow();
            Document keys = found.get("key", Document.class);
            if (!index.keys().equals(keys) || !new ArrayList<>(index.keys().keySet()).equals(new ArrayList<>(keys.keySet()))
                    || index.unique() != found.getBoolean("unique", false) || found.containsKey("partialFilterExpression")
                    || found.containsKey("expireAfterSeconds") || found.getBoolean("sparse", false) || found.containsKey("collation"))
                throw new IllegalStateException("Deletion index mismatch");
        }
    }
}
