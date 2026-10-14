package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.response.ReserveResponse;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;

/** Selects a writer before opening its transaction. Never falls back after a paid error. */
public final class PaidExamRouter {
    private final MongoTemplate mongo;
    private final PaidReservationService service;
    private final String paymentEnvironment, environment;
    public PaidExamRouter(MongoTemplate mongo, PaidReservationService service, String paymentEnvironment, String environment) {
        this.mongo = mongo; this.service = service; this.paymentEnvironment = paymentEnvironment; this.environment = environment;
    }
    public boolean handles(String userId, String operationId) {
        try {
            if (mongo.exists(new BasicQuery(new Document("environment", environment).append("userId", userId).append("operationId", operationId)), PaidExamStore.RECEIPTS)) return true;
            // A retained legacy command must not become a new paid operation when the user buys later.
            if (mongo.exists(new BasicQuery(new Document("callerService", "LEARNING_CORE").append("userId", userId)
                    .append("operationId", operationId)), "idempotency_commands")) return false;
            return mongo.exists(new BasicQuery(new Document("environment", paymentEnvironment).append("userId", userId)), "payment_accounts");
        } catch (org.springframework.dao.DataAccessException | com.mongodb.MongoException unavailable) {
            throw ReservationException.temporarilyUnavailable();
        }
    }
    public boolean handlesReservation(String reservationId) {
        try {
            Reservation r = mongo.findById(reservationId, Reservation.class);
            return r != null && r.getSourceSnapshot() != null;
        } catch (org.springframework.dao.DataAccessException | com.mongodb.MongoException unavailable) {
            throw ReservationException.temporarilyUnavailable();
        }
    }
    public ReserveResponse reserve(ReserveCommand command) {
        var result = service.reserve(command); Document r = result.responseSnapshot();
        Document transition = r.get("transition", Document.class);
        return new ReserveResponse(r.getString("operationId"), r.getString("reservationId"), Reservation.Kind.valueOf(r.getString("reservationKind")),
                Reservation.Status.valueOf(r.getString("reservationStatus")), r.getString("attemptGroupId"), r.getString("sessionId"), r.getString("mockExamId"),
                r.getString("continuationReason") == null ? null : Reservation.ContinuationReason.valueOf(r.getString("continuationReason")),
                r.getString("continuationId"), r.getDate("expiresAt").toInstant(), EntitlementSourceSnapshot.Type.valueOf(result.source().getString("type")),
                transition == null ? null : new ExamTransition(transition.getString("previousAttemptGroupId"), transition.getString("previousSessionId")));
    }
    public LifecycleResult confirm(ConfirmCommand command) { return service.confirm(command); }
    public LifecycleResult cancel(CancelCommand command) { return service.cancel(command); }
    public Optional<Boolean> expire(String reservationId) {
        try {
            Reservation r = mongo.findById(reservationId, Reservation.class);
            if (r == null || r.getSourceSnapshot() == null) return Optional.empty();
            List<Document> receipts = mongo.find(new BasicQuery(new Document("environment", environment).append("reservationId", reservationId)
                    .append("commandType", "RESERVE")).limit(2), Document.class, PaidExamStore.RECEIPTS);
            if (receipts.size() != 1) throw ReservationException.temporarilyUnavailable();
            return Optional.of(service.expire(receipts.getFirst().getString("userId"), r.getOperationId(), reservationId));
        } catch (org.springframework.dao.DataAccessException | com.mongodb.MongoException unavailable) {
            throw ReservationException.temporarilyUnavailable();
        }
    }
}
