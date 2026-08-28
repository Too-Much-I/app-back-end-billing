package web.tosunsaeng.billing.reservation.application;

import java.time.Instant;

import web.tosunsaeng.billing.reservation.domain.AttemptGroup;
import web.tosunsaeng.billing.reservation.domain.Reservation;

public record ReservationStatusResult(
        String operationId,
        String reservationId,
        Reservation.Kind reservationKind,
        Reservation.Status reservationStatus,
        String attemptGroupId,
        AttemptGroup.Status attemptGroupStatus,
        String sessionId,
        String mockExamId,
        Instant expiresAt,
        Instant terminalAt
) {
}
