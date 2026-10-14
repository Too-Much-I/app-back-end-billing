package web.tosunsaeng.billing.domain.reservation.dto.request;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record ReserveRequest(
        String userId,
        String sessionId,
        String mockExamId,
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        String expectedAttemptGroupId,
        web.tosunsaeng.billing.domain.reservation.paid.RestartContext restartContext
) {
    public ReserveRequest(String userId, String sessionId, String mockExamId, Reservation.ContinuationReason continuationReason,
            String continuationId, String expectedAttemptGroupId) {
        this(userId, sessionId, mockExamId, continuationReason, continuationId, expectedAttemptGroupId, null);
    }
    public ReserveRequest(String userId, String sessionId, String mockExamId) {
        this(userId, sessionId, mockExamId, null, null, null);
    }

    @Override
    public String toString() {
        return "ReserveRequest[sensitiveFields=[REDACTED]]";
    }
}
