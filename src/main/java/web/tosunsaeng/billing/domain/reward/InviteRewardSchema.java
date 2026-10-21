package web.tosunsaeng.billing.domain.reward;

import java.time.Instant;
import java.util.*;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;

/** Independent additive schema r1. No startup DDL and no automatic receipt TTL. */
public final class InviteRewardSchema {
    public static final String VERSION = "invite-share-reward-r1";
    private InviteRewardSchema() {}
    private static Document type(String t) { return new Document("bsonType", t); }
    private static Document uuid() { return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"); }
    private static Document object(Document p) { return type("object").append("additionalProperties", false).append("required", new ArrayList<>(p.keySet())).append("properties", p); }
    public static Map<String, Document> validators() {
        Document common = new Document("_id", uuid()).append("schemaVersion", type("long").append("enum", List.of(1L)))
                .append("environment", type("string").append("enum", List.of("test", "staging", "production")))
                .append("userId", type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
                .append("accountId", uuid());
        Document counter = new Document(common).append("awardedCount", type("int").append("minimum", 1).append("maximum", 3))
                .append("version", type("long").append("minimum", 1L)).append("createdAt", type("date")).append("updatedAt", type("date"));
        Document snapshot = object(new Document("operationId", uuid())
                .append("awardedQuantity", type("int").append("enum", List.of(1)))
                .append("awardedCount", type("int").append("minimum", 1).append("maximum", 3))
                .append("limit", type("int").append("enum", List.of(3)))
                .append("remainingAwards", type("int").append("minimum", 0).append("maximum", 2)));
        Document receipt = new Document(common).append("operationId", uuid())
                .append("state", type("string").append("enum", List.of("AWARDED", "LIMIT_REACHED")))
                .append("httpStatus", type("int"))
                .append("grantId", new Document("bsonType", List.of("string", "null")))
                .append("ledgerEventId", new Document("bsonType", List.of("string", "null")))
                .append("errorCode", new Document("bsonType", List.of("string", "null")))
                .append("responseSnapshot", new Document("anyOf", List.of(type("null"), snapshot)))
                .append("terminalAt", type("date")).append("detailEligibleAt", type("date"));
        Document receiptValidator = new Document("$jsonSchema", object(receipt)).append("$and", List.of(
                new Document("$expr", new Document("$eq", List.of("$detailEligibleAt", new Document("$add", List.of("$terminalAt", 10_368_000_000L))))),
                new Document("$or", List.of(
                        new Document("state", "LIMIT_REACHED").append("httpStatus", 409).append("errorCode", "INVITE_REWARD_LIMIT_REACHED")
                                .append("grantId", null).append("ledgerEventId", null).append("responseSnapshot", null),
                        new Document("state", "AWARDED").append("httpStatus", 200).append("errorCode", null)
                                .append("grantId", new Document("$type", "string")).append("ledgerEventId", new Document("$type", "string"))
                                .append("$expr", new Document("$and", List.of(
                                        new Document("$eq", List.of("$operationId", "$responseSnapshot.operationId")),
                                        new Document("$eq", List.of(3, new Document("$add", List.of("$responseSnapshot.awardedCount", "$responseSnapshot.remainingAwards")))))))))));
        return Map.of(InviteRewardService.ACCOUNTS, new Document("$jsonSchema", object(counter)), InviteRewardService.RECEIPTS, receiptValidator);
    }
    private record Index(String collection, String name, Document keys, boolean unique, Document partial) {}
    private static List<Index> indexes() {
        return List.of(new Index(InviteRewardService.ACCOUNTS, "ux_invite_reward_account", new Document("environment", 1).append("userId", 1), true, null),
                new Index(InviteRewardService.RECEIPTS, "ux_invite_reward_operation", new Document("environment", 1).append("userId", 1).append("operationId", 1), true, null),
                new Index(InviteRewardService.RECEIPTS, "ix_invite_reward_detail_eligible", new Document("detailEligibleAt", 1).append("_id", 1), false, null),
                new Index("entitlement_grants", "ux_invite_reward_source", new Document("environment", 1).append("sourceType", 1).append("sourceId", 1), true, new Document("sourceType", "INVITE")),
                new Index("entitlement_ledger", "ix_invite_ledger_owner", new Document("environment", 1).append("ownerUserId", 1), false, new Document("sourceType", "INVITE")));
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        if (!writersIsolated) throw mismatch();
        web.tosunsaeng.billing.domain.reservation.paid.PaidExamSchema.verify(db);
        validators().forEach((name, value) -> { if (db.listCollections().filter(new Document("name", name)).first() != null) verifyValidator(db, name, value); });
        validators().forEach((name, value) -> {
            if (db.listCollections().filter(new Document("name", name)).first() == null)
                db.createCollection(name, new CreateCollectionOptions().validationOptions(new ValidationOptions().validator(value)
                        .validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        });
        for (Index i : indexes()) {
            var options = new IndexOptions().name(i.name()).unique(i.unique());
            if (i.partial() != null) options.partialFilterExpression(i.partial());
            db.getCollection(i.collection()).createIndex(i.keys(), options);
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        validators().forEach((name, value) -> verifyValidator(db, name, value));
        for (String name : validators().keySet()) {
            var actual = db.getCollection(name).listIndexes().into(new ArrayList<>());
            if (actual.size() != indexes().stream().filter(i -> i.collection().equals(name)).count() + 1) throw mismatch();
        }
        for (Index i : indexes()) {
            var actual = db.getCollection(i.collection()).listIndexes().into(new ArrayList<>());
            if (actual.stream().anyMatch(v -> v.containsKey("expireAfterSeconds"))) throw mismatch();
            Document found = actual.stream().filter(v -> i.name().equals(v.getString("name"))).findFirst().orElseThrow(InviteRewardSchema::mismatch);
            if (!i.keys().equals(found.get("key")) || i.unique() != found.getBoolean("unique", false)
                    || !Objects.equals(i.partial(), found.get("partialFilterExpression")) || found.getBoolean("sparse", false)
                    || found.getBoolean("hidden", false) || !simple(found)) throw mismatch();
        }
    }
    private static void verifyValidator(MongoDatabase db, String name, Document value) {
        Document meta = db.listCollections().filter(new Document("name", name)).first();
        if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
        Document options = meta.get("options", Document.class);
        if (!value.equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false) || !simple(options)) throw mismatch();
    }
    private static boolean simple(Document d) { return !d.containsKey("collation") || "simple".equals(d.get("collation", Document.class).getString("locale")); }
    /** Operator-only bounded cleanup. Counter, grants and ledgers are deliberately untouched. */
    public static long purgeDetailsUnderMaintenance(MongoDatabase db, boolean writersIsolated, Instant now, int limit) {
        if (!writersIsolated || now == null || limit < 1 || limit > 1000) throw mismatch();
        verify(db);
        var receipts = db.getCollection(InviteRewardService.RECEIPTS);
        var cutoff = new Document("detailEligibleAt", new Document("$lte", Date.from(now)));
        var ids = receipts.find(cutoff).sort(new Document("detailEligibleAt", 1).append("_id", 1)).limit(limit)
                .map(d -> d.getString("_id")).into(new ArrayList<>());
        return ids.isEmpty() ? 0 : receipts.deleteMany(new Document(cutoff).append("_id", new Document("$in", ids))).getDeletedCount();
    }
    private static IllegalStateException mismatch() { return new IllegalStateException("Invite reward schema mismatch; explicit maintenance required"); }
}
