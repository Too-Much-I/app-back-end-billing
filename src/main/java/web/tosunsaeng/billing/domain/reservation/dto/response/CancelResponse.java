package web.tosunsaeng.billing.domain.reservation.dto.response;

import java.time.Instant;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record CancelResponse(
        String operationId,
        String reservationId,
        Reservation.Status reservationStatus,
        Instant canceledAt
) {
}
