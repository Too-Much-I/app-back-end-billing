package web.tosunsaeng.billing.domain.reservation.application;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record PhoneContinuationResult(
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        String attemptGroupId,
        String mockExamId
) {
    @Override
    public String toString() {
        return "PhoneContinuationResult[reason=" + continuationReason
                + ", sensitiveFields=[REDACTED]]";
    }
}
