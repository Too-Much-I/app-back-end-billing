package web.tosunsaeng.billing.reservation.api;

import java.time.Instant;

import web.tosunsaeng.billing.reservation.domain.AttemptGroup;
import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;

public record ConfirmResponse(
        String operationId,
        String reservationId,
        Reservation.Status reservationStatus,
        String attemptGroupId,
        AttemptGroup.Status attemptGroupStatus,
        String sessionId,
        Instant confirmedAt
) {
    public static ConfirmResponse from(IdempotencyCommand.LifecycleResponseSnapshot snapshot) {
        return new ConfirmResponse(
                snapshot.operationId(), snapshot.reservationId(), snapshot.reservationStatus(),
                snapshot.attemptGroupId(), snapshot.attemptGroupStatus(), snapshot.sessionId(),
                snapshot.terminalAt()
        );
    }
}
