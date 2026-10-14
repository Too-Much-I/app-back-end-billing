package web.tosunsaeng.billing.domain.reservation.paid;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;

/** Recovery r2 retains r1 rows; explicit maintenance upgrade only, never startup mutation or TTL. */
public final class PaidExamSchema {
    public static final String VERSION = "paid-exam-recovery-r2";
    private PaidExamSchema() {}
    record Index(String name, Document keys, Document partial) {}
    static Document type(String type) { return new Document("bsonType", type); }
    static Document nullable(String type) { return new Document("bsonType", List.of(type, "null")); }
    static Document ref() { return type("string").append("minLength", 1).append("maxLength", 128); }
    static Document positive() { return type("long").append("minimum", 1L); }
    static Document values(String... values) { return type("string").append("enum", List.of(values)); }
    static Document hash() { return type("string").append("pattern", "^[0-9a-f]{64}(?![\\s\\S])"); }
    static Document uuid(boolean v4) {
        return type("string").append("pattern", "^[0-9a-f]{8}-[0-9a-f]{4}-" + (v4 ? "4[0-9a-f]{3}-[89ab]" : "[0-9a-f]{4}-[0-9a-f]") + "[0-9a-f]{3}-[0-9a-f]{12}(?![\\s\\S])");
    }
    static Document object(Document properties) {
        return type("object").append("additionalProperties", false).append("required", new ArrayList<>(properties.keySet())).append("properties", properties);
    }
    public static Document sourceSchema() {
        Document fields = new Document("type", values("EXAM_PASS", "SUBSCRIPTION"))
                .append("grantId", nullable("string")).append("consumptionId", nullable("string")).append("trialClaimId", nullable("string"))
                .append("accountId", nullable("string")).append("purchaseId", nullable("string")).append("entitlementId", nullable("string"))
                .append("selectedAt", type("date")).append("startsAt", nullable("date")).append("endsAt", nullable("date"));
        return object(fields).append("oneOf", List.of(
                new Document("properties", new Document("type", values("EXAM_PASS")).append("grantId", ref())
                        .append("accountId", type("null")).append("purchaseId", type("null")).append("entitlementId", type("null"))
                        .append("startsAt", type("null")).append("endsAt", type("null"))),
                new Document("properties", new Document("type", values("SUBSCRIPTION")).append("grantId", type("null"))
                        .append("consumptionId", type("null")).append("trialClaimId", type("null"))
                        .append("accountId", ref()).append("purchaseId", ref()).append("entitlementId", ref())
                        .append("startsAt", type("date")).append("endsAt", type("date")))));
    }
    static Document owner() {
        return object(new Document("kind", values("TRIAL_SUBJECT", "MEMBER_ACCOUNT"))
                .append("reference", ref()).append("version", positive()).append("epoch", nullable("long")))
                .append("oneOf", List.of(
                        new Document("properties", new Document("kind", values("TRIAL_SUBJECT")).append("epoch", positive())),
                        new Document("properties", new Document("kind", values("MEMBER_ACCOUNT")).append("epoch", type("null")))));
    }
    static Map<String, Document> foundationValidators() {
        Document guard = new Document("_id", uuid(true)).append("schemaVersion", type("long").append("enum", List.of(1L)))
                .append("environment", values("test", "staging", "production")).append("userId", uuid(false)).append("owner", owner())
                .append("version", positive()).append("nextGrantOrder", positive());
        for (String f : List.of("currentGroupId", "currentSessionId", "pendingOperationId", "pendingReservationId", "pendingGroupId")) guard.append(f, nullable("string"));
        Document guardValidator = new Document("$jsonSchema", object(guard)).append("$and", List.of(
                new Document("$or", List.of(new Document("currentGroupId", null).append("currentSessionId", null),
                        new Document("currentGroupId", new Document("$type", "string")).append("currentSessionId", new Document("$type", "string")))),
                new Document("$or", List.of(new Document("pendingOperationId", null).append("pendingReservationId", null).append("pendingGroupId", null),
                        new Document("pendingOperationId", new Document("$type", "string")).append("pendingReservationId", new Document("$type", "string"))
                                .append("pendingGroupId", new Document("$type", "string"))))));
        Document receipt = new Document("_id", uuid(true)).append("schemaVersion", type("long").append("enum", List.of(1L)))
                .append("environment", values("test", "staging", "production")).append("userId", uuid(false))
                .append("callerService", values("LEARNING_CORE")).append("operationId", uuid(true))
                .append("commandType", values("RESERVE", "CONFIRM", "CANCEL")).append("payloadHash", hash()).append("owner", owner())
                .append("state", values("WAITING_MEMBER_EVIDENCE", "RESERVED", "CONFIRMED", "CANCELED", "EXPIRED", "FAILED_TERMINAL"))
                .append("version", positive()).append("receivedAt", type("date")).append("waitingSince", nullable("date"))
                .append("reservationId", nullable("string")).append("source", new Document("anyOf", List.of(type("null"), sourceSchema())))
                .append("responseSnapshot", nullable("object")).append("errorCode", nullable("string"))
                .append("terminalAt", nullable("date")).append("detailEligibleAt", nullable("date"));
        Document receiptValidator = new Document("$jsonSchema", object(receipt)).append("$or", List.of(
                new Document("state", "WAITING_MEMBER_EVIDENCE").append("commandType", "RESERVE").append("waitingSince", new Document("$type", "date"))
                        .append("reservationId", null).append("source", null).append("responseSnapshot", null)
                        .append("errorCode", "MEMBER_EVIDENCE_PENDING").append("terminalAt", null).append("detailEligibleAt", null),
                new Document("state", "RESERVED").append("commandType", "RESERVE").append("reservationId", new Document("$type", "string"))
                        .append("source", new Document("$type", "object")).append("responseSnapshot", new Document("$type", "object"))
                        .append("errorCode", null).append("terminalAt", null).append("detailEligibleAt", null),
                new Document("state", new Document("$in", List.of("CONFIRMED", "CANCELED", "EXPIRED")))
                        .append("reservationId", new Document("$type", "string")).append("source", new Document("$type", "object"))
                        .append("responseSnapshot", new Document("$type", "object"))
                        .append("terminalAt", new Document("$type", "date")).append("detailEligibleAt", new Document("$type", "date")),
                new Document("state", "FAILED_TERMINAL").append("commandType", "RESERVE").append("reservationId", null).append("source", null)
                        .append("responseSnapshot", null).append("errorCode", "TRANSITION_CONTEXT_CONFLICT")
                        .append("terminalAt", new Document("$type", "date")).append("detailEligibleAt", new Document("$type", "date"))));
        receiptValidator.append("$and", List.of(
                new Document("$or", List.of(new Document("terminalAt", null), new Document("$expr", new Document("$eq",
                        List.of("$detailEligibleAt", new Document("$add", List.of("$terminalAt", 10_368_000_000L))))))),
                new Document("$or", List.of(new Document("state", new Document("$nin", List.of("CONFIRMED", "EXPIRED"))),
                        new Document("errorCode", null))),
                new Document("$or", List.of(new Document("state", new Document("$ne", "CANCELED")),
                        new Document("errorCode", new Document("$in", Arrays.asList(null, "ENTITLEMENT_REVOKED")))))));
        return Map.of(PaidExamStore.GUARDS, guardValidator, PaidExamStore.RECEIPTS, receiptValidator);
    }
    public static Map<String, Document> validators() {
        var result = new HashMap<>(foundationValidators());
        Document validator = result.get(PaidExamStore.RECEIPTS);
        Document v1 = validator.get("$jsonSchema", Document.class);
        Document v2 = Document.parse(v1.toJson());
        Document fields = v2.get("properties", Document.class);
        fields.put("schemaVersion", type("long").append("enum", List.of(2L)));
        fields.put("requestSnapshot", object(new Document("userId", uuid(false)).append("sessionId", ref()).append("mockExamId", ref())
                .append("continuationReason", new Document("enum", Arrays.asList(null, "PHONE_REJOIN")))
                .append("continuationId", nullable("string")).append("expectedAttemptGroupId", nullable("string"))));
        v2.getList("required", String.class).add("requestSnapshot");
        validator.put("$jsonSchema", new Document("anyOf", List.of(v1, v2)));
        return result;
    }
    /** Explicit upgrade, old request snapshots are never inferred from hashes. Writers must be isolated. */
    public static void upgradeRecoveryUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        if (!writersIsolated) throw mismatch();
        verify(db, foundationValidators());
        Document validator = validators().get(PaidExamStore.RECEIPTS);
        db.runCommand(new Document("collMod", PaidExamStore.RECEIPTS).append("validator", validator)
                .append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
    static Map<String, List<Index>> indexes() {
        return Map.of(PaidExamStore.GUARDS, List.of(new Index("ux_exam_owner", new Document("environment", 1).append("userId", 1), null)),
                PaidExamStore.RECEIPTS, List.of(new Index("ux_reservation_operation_receipt", new Document("environment", 1).append("callerService", 1)
                        .append("userId", 1).append("operationId", 1).append("commandType", 1), null)),
                "entitlement_grants", List.of(
                        new Index("ux_restoration_consumption", new Document("environment", 1).append("sourceType", 1).append("sourceId", 1),
                                new Document("sourceType", "TRIAL_PAID_RESTORATION")),
                        new Index("ux_member_grant_order", new Document("environment", 1).append("ownerUserId", 1).append("grantOrder", 1),
                                new Document("ownerUserId", new Document("$type", "string")).append("grantOrder", new Document("$type", "long")))));
    }
    public static void installUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        if (!writersIsolated) throw mismatch();
        // Never change an existing validator; inspect all existing collections before creating anything.
        validators().forEach((name, validator) -> {
            if (db.listCollections().filter(new Document("name", name)).first() != null) verifyValidator(db, name, validator);
        });
        if (db.listCollections().filter(new Document("name", "entitlement_grants")).first() == null) throw mismatch();
        validators().forEach((name, validator) -> {
            if (db.listCollections().filter(new Document("name", name)).first() == null)
                db.createCollection(name, new CreateCollectionOptions().validationOptions(new ValidationOptions().validator(validator)
                        .validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
        });
        indexes().forEach((name, definitions) -> definitions.forEach(index -> {
            IndexOptions options = new IndexOptions().name(index.name()).unique(true);
            if (index.partial() != null) options.partialFilterExpression(index.partial());
            db.getCollection(name).createIndex(index.keys(), options);
        }));
        verify(db);
    }
    public static void verify(MongoDatabase db) { verify(db, validators()); }
    private static void verify(MongoDatabase db, Map<String, Document> expectedValidators) {
        expectedValidators.forEach((name, validator) -> verifyValidator(db, name, validator));
        indexes().forEach((name, definitions) -> {
            var actual = db.getCollection(name).listIndexes().into(new ArrayList<>());
            if (!name.equals("entitlement_grants") && actual.size() != definitions.size() + 1) throw mismatch();
            for (Index expected : definitions) {
                Document index = actual.stream().filter(i -> expected.name().equals(i.getString("name"))).findFirst().orElseThrow(PaidExamSchema::mismatch);
                if (!expected.keys().equals(index.get("key")) || !index.getBoolean("unique", false)
                        || !Objects.equals(expected.partial(), index.get("partialFilterExpression"))
                        || index.containsKey("expireAfterSeconds") || index.getBoolean("sparse", false) || index.getBoolean("hidden", false)) throw mismatch();
                simple(index);
            }
            // No TTL on grants (consumption dedupe) or unfinished receipts/guards.
            if (actual.stream().anyMatch(i -> i.containsKey("expireAfterSeconds"))) throw mismatch();
        });
    }
    private static void verifyValidator(MongoDatabase db, String name, Document validator) {
        Document meta = db.listCollections().filter(new Document("name", name)).first();
        if (meta == null || !"collection".equals(meta.getString("type"))) throw mismatch();
        Document options = meta.get("options", Document.class);
        if (!validator.equals(options.get("validator")) || !"strict".equals(options.getString("validationLevel"))
                || !"error".equals(options.getString("validationAction")) || options.getBoolean("capped", false)) throw mismatch();
        simple(options);
    }
    private static void simple(Document d) {
        if (d.containsKey("collation") && !"simple".equals(d.get("collation", Document.class).getString("locale"))) throw mismatch();
    }
    private static IllegalStateException mismatch() { return new IllegalStateException("Paid exam schema mismatch; explicit maintenance required"); }
}
