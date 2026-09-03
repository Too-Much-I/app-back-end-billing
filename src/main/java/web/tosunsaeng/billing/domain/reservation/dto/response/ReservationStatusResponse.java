package web.tosunsaeng.billing.domain.reservation.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationStatusResponse(
        String operationId,
        String reservationId,
        Reservation.Kind reservationKind,
        Reservation.Status reservationStatus,
        String attemptGroupId,
        AttemptGroup.Status attemptGroupStatus,
        String sessionId,
        String mockExamId,
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        Instant expiresAt,
        Instant terminalAt
) {
}
