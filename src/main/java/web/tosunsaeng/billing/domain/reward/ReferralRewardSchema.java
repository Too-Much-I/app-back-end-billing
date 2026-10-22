package web.tosunsaeng.billing.domain.reward;

import java.time.Instant;
import java.util.*;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;

/** Additive, explicit maintenance only. Successful application evidence never expires via TTL. */
public final class ReferralRewardSchema {
    public static final String VERSION = "referral-reward-r1";
    private ReferralRewardSchema() {}
    private static Document type(String value) { return new Document("bsonType", value); }
    private static Document uuid() { return type("string").append("pattern", "^" + ReferralRewardService.UUID_V4 + "$"); }
    private static Document user() { return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"); }
    private static Document object(Document fields) { return type("object").append("additionalProperties", false)
            .append("required", new ArrayList<>(fields.keySet())).append("properties", fields); }
    public static Map<String, Document> validators() {
        Document common = new Document("_id", uuid()).append("schemaVersion", type("long").append("enum", List.of(1L)))
                .append("environment", type("string").append("enum", List.of("test", "staging", "production")));
        Document code = type("string").append("pattern", "^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}$");
        Document codes = object(new Document(common).append("code", code)
                .append("ownerUserId", new Document("anyOf", List.of(user(), type("null"))))
                .append("accountId", new Document("anyOf", List.of(uuid(), type("null"))))
                .append("createdAt", type("date")));
        Document detail = object(new Document("referrerUserId", new Document("anyOf", List.of(user(), type("null"))))
                .append("applicantGrantId", uuid()).append("referrerGrantId", uuid())
                .append("applicantLedgerId", uuid()).append("referrerLedgerId", uuid()));
        Document applications = object(new Document(common).append("applicantUserId", user()).append("normalizedCode", code)
                .append("operationId", uuid()).append("appliedAt", type("date")).append("detailEligibleAt", type("date"))
                .append("detail", new Document("anyOf", List.of(detail, type("null")))));
        return Map.of(ReferralRewardService.CODES, new Document("$jsonSchema", codes).append("$or", List.of(
                        new Document("ownerUserId", null).append("accountId", null),
                        new Document("ownerUserId", new Document("$type", "string")).append("accountId", new Document("$type", "string")))),
                ReferralRewardService.APPLICATIONS, new Document("$jsonSchema", applications).append("$expr",
                        new Document("$eq", List.of("$detailEligibleAt", new Document("$add", List.of("$appliedAt", 10_368_000_000L))))));
    }
    private record Index(String collection, String name, Document keys, boolean unique, Document partial) {}
    private static List<Index> indexes() {
        return List.of(new Index(ReferralRewardService.CODES, "ux_referral_code", new Document("environment", 1).append("code", 1), true, null),
                new Index(ReferralRewardService.CODES, "ux_referral_owner", new Document("environment", 1).append("ownerUserId", 1), true,
                        new Document("ownerUserId", new Document("$type", "string"))),
                new Index(ReferralRewardService.APPLICATIONS, "ux_referral_applicant", new Document("environment", 1).append("applicantUserId", 1), true, null),
                new Index(ReferralRewardService.APPLICATIONS, "ux_referral_operation", new Document("environment", 1).append("applicantUserId", 1).append("operationId", 1), true, null),
                new Index(ReferralRewardService.APPLICATIONS, "ix_referral_detail_eligible", new Document("detailEligibleAt", 1).append("_id", 1), false, null),
                new Index(ReferralRewardService.APPLICATIONS, "ix_referral_detail_owner", new Document("environment", 1).append("detail.referrerUserId", 1), false, null),
                new Index("entitlement_grants", "ux_referral_source", new Document("environment", 1).append("sourceType", 1).append("sourceId", 1), true,
                        new Document("sourceType", "REFERRAL")));
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
            Document found = actual.stream().filter(v -> i.name().equals(v.getString("name"))).findFirst().orElseThrow(ReferralRewardSchema::mismatch);
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
    /** Removes relationship details only. Minimal once-per-account and replay evidence remains. */
    public static long purgeDetailsUnderMaintenance(MongoDatabase db, boolean writersIsolated, Instant now, int limit) {
        if (!writersIsolated || now == null || limit < 1 || limit > 1000) throw mismatch();
        verify(db);
        var applications = db.getCollection(ReferralRewardService.APPLICATIONS);
        var cutoff = new Document("detailEligibleAt", new Document("$lte", Date.from(now))).append("detail", new Document("$ne", null));
        var ids = applications.find(cutoff).sort(new Document("detailEligibleAt", 1).append("_id", 1)).limit(limit)
                .map(d -> d.getString("_id")).into(new ArrayList<>());
        return ids.isEmpty() ? 0 : applications.updateMany(new Document(cutoff).append("_id", new Document("$in", ids)),
                new Document("$set", new Document("detail", null))).getModifiedCount();
    }
    /** Read trusted withdrawal evidence directly; never accept a public cleanup timestamp or user assertion. */
    public static long cleanupWithdrawnUnderMaintenance(MongoDatabase db, boolean writersIsolated, String environment,
            String paymentEnvironment, String user, Instant now) {
        if (!writersIsolated || now == null || user == null || !user.matches("[0-9a-f-]{36}")
                || !(Set.of("test", "staging").contains(environment) && "SANDBOX".equals(paymentEnvironment)
                || "production".equals(environment) && "PRODUCTION".equals(paymentEnvironment))) throw mismatch();
        verify(db);
        Document life = db.getCollection("payment_account_lifecycles").find(new Document("environment", paymentEnvironment).append("userId", user)).first();
        if (life == null || !"WITHDRAWN".equals(life.getString("state")) || !(life.get("withdrawnAt") instanceof Date withdrawn)
                || !(life.get("updatedAt") instanceof Date confirmed) || confirmed.toInstant().isAfter(now)
                || !(life.get("withdrawnAtCanonical") instanceof String exact)
                || Instant.parse(exact).isAfter(now) || !Date.from(Instant.parse(exact)).equals(withdrawn)) throw mismatch();
        var applications = db.getCollection(ReferralRewardService.APPLICATIONS);
        Document own = new Document("environment", environment).append("applicantUserId", user);
        if (applications.countDocuments(new Document(own).append("detailEligibleAt", new Document("$gt", Date.from(now)))) > 0) return 0;
        // Idempotent steps while writers are isolated; interruption never permits code reassignment.
        db.getCollection(ReferralRewardService.CODES).updateMany(new Document("environment", environment).append("ownerUserId", user),
                new Document("$set", new Document("ownerUserId", null).append("accountId", null)));
        applications.updateMany(new Document("environment", environment).append("detail.referrerUserId", user),
                new Document("$set", new Document("detail.referrerUserId", null)));
        return applications.deleteMany(own).getDeletedCount();
    }
    private static IllegalStateException mismatch() { return new IllegalStateException("Referral reward schema/maintenance prerequisite mismatch"); }
}
