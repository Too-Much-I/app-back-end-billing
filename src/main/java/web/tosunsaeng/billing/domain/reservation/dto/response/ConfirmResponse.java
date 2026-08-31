package web.tosunsaeng.billing.domain.reservation.dto.response;

import java.time.Instant;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

public record ConfirmResponse(
        String operationId,
        String reservationId,
        Reservation.Status reservationStatus,
        String attemptGroupId,
        AttemptGroup.Status attemptGroupStatus,
        String sessionId,
        Instant confirmedAt
) {
}
