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
        return insertWaiting(key, digest, owner, receivedAt, null);
    }
    public Document insertWaiting(Key key, String digest, Document owner, Instant receivedAt, Document request) {
        if (!"RESERVE".equals(key.commandType())) throw new IllegalArgumentException("Only reserve waits for evidence");
        Document row = keyDocument(key).append("_id", UUID.randomUUID().toString()).append("schemaVersion", 1L)
                .append("payloadHash", digest).append("owner", owner).append("state", State.WAITING_MEMBER_EVIDENCE.name())
                .append("version", 1L).append("receivedAt", ceilingMillis(receivedAt)).append("waitingSince", ceilingMillis(receivedAt))
                .append("reservationId", null).append("source", null).append("responseSnapshot", null)
                .append("errorCode", "MEMBER_EVIDENCE_PENDING").append("terminalAt", null).append("detailEligibleAt", null);
        if (request != null) row.append("schemaVersion", request.containsKey("restartContext") ? 3L : 2L).append("requestSnapshot", request);
        return mongo.insert(row, RECEIPTS);
    }
    public Optional<Document> receipt(Key key, String digest) {
        Document found = receipt(key).orElse(null);
        if (found != null && !Objects.equals(digest, found.getString("payloadHash")))
            throw new IllegalStateException("IDEMPOTENCY_KEY_CONFLICT");
        return Optional.ofNullable(found);
    }
    public Optional<Document> receipt(Key key) {
        return Optional.ofNullable(mongo.findOne(new BasicQuery(keyDocument(key)), Document.class, RECEIPTS));
    }
    public boolean rejectWaiting(Key key, String digest, long version, Instant terminalAt) {
        return mongo.updateFirst(new BasicQuery(keyDocument(key).append("payloadHash", digest).append("version", version)
                        .append("state", State.WAITING_MEMBER_EVIDENCE.name())),
                new Update().set("state", State.FAILED_TERMINAL.name()).set("errorCode", "TRANSITION_CONTEXT_CONFLICT")
                        .set("terminalAt", ceilingMillis(terminalAt)).set("detailEligibleAt", ceilingMillis(terminalAt.plus(DETAIL_RETENTION)))
                        .inc("version", 1), RECEIPTS).getModifiedCount() == 1;
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
    public void finishReserve(Key key, long version, web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation.Status state, Instant now) {
        if (!Set.of("CONFIRMED", "CANCELED", "EXPIRED").contains(state.name())) throw new IllegalArgumentException("Invalid terminal state");
        if (mongo.updateFirst(new BasicQuery(keyDocument(key).append("version", version).append("state", "RESERVED")),
                new Update().set("state", state.name()).set("terminalAt", ceilingMillis(now))
                        .set("detailEligibleAt", ceilingMillis(now.plus(DETAIL_RETENTION))).inc("version", 1), RECEIPTS).getModifiedCount() != 1)
            throw new IllegalStateException("Receipt version conflict");
    }
    public void insertLifecycle(Document reserve, String action, String digest, Document result,
            web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation.Status state, String rejection, Instant now) {
        Key key = new Key(reserve.getString("environment"), reserve.getString("userId"), reserve.getString("operationId"), action);
        Document row = keyDocument(key).append("_id", UUID.randomUUID().toString()).append("schemaVersion", 1L)
                .append("payloadHash", digest).append("owner", reserve.get("owner")).append("state", state.name())
                .append("version", 1L).append("receivedAt", ceilingMillis(now)).append("waitingSince", null)
                .append("reservationId", reserve.get("reservationId")).append("source", reserve.get("source"))
                .append("responseSnapshot", result.get("responseSnapshot")).append("errorCode", rejection)
                .append("terminalAt", ceilingMillis(now)).append("detailEligibleAt", ceilingMillis(now.plus(DETAIL_RETENTION)));
        mongo.insert(row, RECEIPTS);
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
    private static Date ceilingMillis(Instant time) {
        long millis = time.toEpochMilli();
        return new Date(time.getNano() % 1_000_000 == 0 ? millis : Math.addExact(millis, 1));
    }
    private static Document keyDocument(Key k) {
        return guardKey(k.environment(), k.userId()).append("callerService", "LEARNING_CORE")
                .append("operationId", k.operationId()).append("commandType", k.commandType());
    }
    private static Document guardKey(String environment, String userId) { return new Document("environment", environment).append("userId", userId); }
    private static void required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing guard reference"); }
}
