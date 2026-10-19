package web.tosunsaeng.billing.domain.reservation.dto.response;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record PhoneContinuationResponse(
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        String attemptGroupId,
        String mockExamId
) {
}
