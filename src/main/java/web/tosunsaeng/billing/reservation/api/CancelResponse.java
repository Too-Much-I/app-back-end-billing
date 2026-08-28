package web.tosunsaeng.billing.reservation.api;

import java.time.Instant;

import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;

public record CancelResponse(
        String operationId,
        String reservationId,
        Reservation.Status reservationStatus,
        Instant canceledAt
) {
    public static CancelResponse from(IdempotencyCommand.LifecycleResponseSnapshot snapshot) {
        return new CancelResponse(
                snapshot.operationId(), snapshot.reservationId(),
                snapshot.reservationStatus(), snapshot.terminalAt()
        );
    }
}
