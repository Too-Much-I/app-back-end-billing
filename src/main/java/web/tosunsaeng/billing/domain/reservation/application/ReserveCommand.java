package web.tosunsaeng.billing.domain.reservation.application;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record ReserveCommand(
        String operationId,
        String userId,
        String sessionId,
        String mockExamId,
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        String expectedAttemptGroupId,
        String payloadHash
) {
    public ReserveCommand(
            String operationId,
            String userId,
            String sessionId,
            String mockExamId,
            String payloadHash
    ) {
        this(operationId, userId, sessionId, mockExamId, null, null, null, payloadHash);
    }

    @Override
    public String toString() {
        return "ReserveCommand[sensitiveFields=[REDACTED]]";
    }
}
