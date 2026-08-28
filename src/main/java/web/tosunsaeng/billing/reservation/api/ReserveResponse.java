package web.tosunsaeng.billing.reservation.api;

import java.time.Instant;

import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;

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
    public static ReserveResponse from(IdempotencyCommand.ResponseSnapshot snapshot) {
        return new ReserveResponse(
                snapshot.operationId(), snapshot.reservationId(), snapshot.reservationKind(),
                snapshot.reservationStatus(), snapshot.attemptGroupId(), snapshot.sessionId(),
                snapshot.mockExamId(), snapshot.expiresAt()
        );
    }
}
