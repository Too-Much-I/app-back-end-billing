package web.tosunsaeng.billing.domain.reservation.dto.response;

import java.time.Instant;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record ReserveResponse(
        String operationId,
        String reservationId,
        Reservation.Kind reservationKind,
        Reservation.Status reservationStatus,
        String attemptGroupId,
        String sessionId,
        String mockExamId,
        Instant expiresAt
) {
}
