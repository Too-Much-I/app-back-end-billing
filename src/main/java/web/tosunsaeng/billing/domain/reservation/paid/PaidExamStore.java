package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;

/** Session-bound persistence primitives. Callers must validate caller/owner/lifecycle before access. */
public final class PaidExamStore {
    public static final String GUARDS = "exam_owner_guards";
    public static final String RECEIPTS = "reservation_operation_receipts";
    public static final Duration DETAIL_RETENTION = Duration.ofDays(120);
    public enum State { WAITING_MEMBER_EVIDENCE, RESERVED, CONFIRMED, CANCELED, EXPIRED, FAILED_TERMINAL }
    public record Key(String environment, String userId, String operationId, String commandType) {
        public Key {
            if (!Set.of("test", "staging", "production").contains(environment)) throw new IllegalArgumentException("Invalid environment");
            if (userId == null || !userId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    || operationId == null || !operationId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                    || !Set.of("RESERVE", "CONFIRM", "CANCEL").contains(commandType)) throw new IllegalArgumentException("Invalid receipt key");
        }
        @Override public String toString() { return "ReceiptKey[REDACTED]"; }
    }
    private final MongoOperations mongo;
    PaidExamStore(MongoOperations mongo) { this.mongo = mongo; }

    public Document insertWaiting(Key key, String digest, Document owner, Instant receivedAt) {
        if (!"RESERVE".equals(key.commandType())) throw new IllegalArgumentException("Only reserve waits for evidence");
        Document row = keyDocument(key).append("_id", UUID.randomUUID().toString()).append("schemaVersion", 1L)
                .append("payloadHash", digest).append("owner", owner).append("state", State.WAITING_MEMBER_EVIDENCE.name())
                .append("version", 1L).append("receivedAt", Date.from(receivedAt)).append("waitingSince", Date.from(receivedAt))
                .append("reservationId", null).append("source", null).append("responseSnapshot", null)
                .append("errorCode", "MEMBER_EVIDENCE_PENDING").append("terminalAt", null).append("detailEligibleAt", null);
        return mongo.insert(row, RECEIPTS);
    }
    public Optional<Document> receipt(Key key, String digest) {
        Document found = mongo.findOne(new BasicQuery(keyDocument(key)), Document.class, RECEIPTS);
        if (found != null && !Objects.equals(digest, found.getString("payloadHash")))
            throw new IllegalStateException("IDEMPOTENCY_KEY_CONFLICT");
        return Optional.ofNullable(found);
    }
    public boolean reserveWaiting(Key key, String digest, long version, String reservationId,
            EntitlementSourceSnapshot source, Document response) {
        Objects.requireNonNull(source); Objects.requireNonNull(response);
        var query = keyDocument(key).append("payloadHash", digest).append("version", version)
                .append("state", State.WAITING_MEMBER_EVIDENCE.name());
        return mongo.updateFirst(new BasicQuery(query), new Update().set("state", State.RESERVED.name())
                .set("reservationId", reservationId).set("source", source.toDocument()).set("responseSnapshot", response)
                .set("errorCode", null).inc("version", 1), RECEIPTS).getModifiedCount() == 1;
    }
    public Document createGuard(String environment, String userId, Document owner) {
        // Reuse key validation, without introducing a command for the guard.
        new Key(environment, userId, "00000000-0000-4000-8000-000000000000", "RESERVE");
        return mongo.insert(new Document("_id", UUID.randomUUID().toString()).append("schemaVersion", 1L)
                .append("environment", environment).append("userId", userId).append("owner", owner)
                .append("version", 1L).append("nextGrantOrder", 1L)
                .append("currentGroupId", null).append("currentSessionId", null)
                .append("pendingOperationId", null).append("pendingReservationId", null).append("pendingGroupId", null), GUARDS);
    }
    public boolean acquire(String environment, String userId, long version, String operationId, String reservationId, String groupId) {
        new Key(environment, userId, operationId, "RESERVE");
        required(reservationId); required(groupId);
        return mongo.updateFirst(new BasicQuery(guardKey(environment, userId).append("version", version).append("pendingOperationId", null)),
                new Update().set("pendingOperationId", operationId).set("pendingReservationId", reservationId)
                        .set("pendingGroupId", groupId).inc("version", 1), GUARDS).getModifiedCount() == 1;
    }
    public boolean release(String environment, String userId, long version, String operationId, String reservationId) {
        required(operationId); required(reservationId);
        return mongo.updateFirst(new BasicQuery(guardKey(environment, userId).append("version", version)
                        .append("pendingOperationId", operationId).append("pendingReservationId", reservationId)),
                new Update().set("pendingOperationId", null).set("pendingReservationId", null).set("pendingGroupId", null)
                        .inc("version", 1), GUARDS).getModifiedCount() == 1;
    }
    public boolean confirmGuard(String environment, String userId, long version, String operationId,
            String reservationId, String groupId, String sessionId) {
        required(operationId); required(reservationId); required(groupId); required(sessionId);
        return mongo.updateFirst(new BasicQuery(guardKey(environment, userId).append("version", version)
                        .append("pendingOperationId", operationId).append("pendingReservationId", reservationId).append("pendingGroupId", groupId)),
                new Update().set("currentGroupId", groupId).set("currentSessionId", sessionId)
                        .set("pendingOperationId", null).set("pendingReservationId", null).set("pendingGroupId", null)
                        .inc("version", 1), GUARDS).getModifiedCount() == 1;
    }
    public boolean completeCurrent(String environment, String userId, long version, String groupId, String sessionId) {
        required(groupId); required(sessionId);
        return mongo.updateFirst(new BasicQuery(guardKey(environment, userId).append("version", version)
                        .append("currentGroupId", groupId).append("currentSessionId", sessionId).append("pendingOperationId", null)),
                new Update().set("currentGroupId", null).set("currentSessionId", null).inc("version", 1), GUARDS).getModifiedCount() == 1;
    }
    public long nextGrantOrder(String environment, String userId, long version) {
        Document old = mongo.findAndModify(new BasicQuery(guardKey(environment, userId).append("version", version)),
                new Update().inc("nextGrantOrder", 1).inc("version", 1), FindAndModifyOptions.options(), Document.class, GUARDS);
        if (old == null) throw new IllegalStateException("Guard version conflict");
        return old.getLong("nextGrantOrder");
    }
    public static boolean withinGuarantee(Instant lcFirstAcceptedAt, Instant now) {
        return !now.isBefore(lcFirstAcceptedAt) && now.isBefore(lcFirstAcceptedAt.plus(DETAIL_RETENTION));
    }
    private static Document keyDocument(Key k) {
        return guardKey(k.environment(), k.userId()).append("callerService", "LEARNING_CORE")
                .append("operationId", k.operationId()).append("commandType", k.commandType());
    }
    private static Document guardKey(String environment, String userId) { return new Document("environment", environment).append("userId", userId); }
    private static void required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing guard reference"); }
}
