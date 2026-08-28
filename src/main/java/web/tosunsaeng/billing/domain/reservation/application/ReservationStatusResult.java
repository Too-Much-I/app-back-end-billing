package web.tosunsaeng.billing.domain.reservation.application;

import java.time.Instant;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

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
