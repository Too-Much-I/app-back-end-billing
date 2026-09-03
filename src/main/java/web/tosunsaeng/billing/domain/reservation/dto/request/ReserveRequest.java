package web.tosunsaeng.billing.domain.reservation.dto.request;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record ReserveRequest(
        String userId,
        String sessionId,
        String mockExamId,
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        String expectedAttemptGroupId
) {
    public ReserveRequest(String userId, String sessionId, String mockExamId) {
        this(userId, sessionId, mockExamId, null, null, null);
    }

    @Override
    public String toString() {
        return "ReserveRequest[sensitiveFields=[REDACTED]]";
    }
}
