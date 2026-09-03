package web.tosunsaeng.billing.domain.reservation.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReserveResponse(
        String operationId,
        String reservationId,
        Reservation.Kind reservationKind,
        Reservation.Status reservationStatus,
        String attemptGroupId,
        String sessionId,
        String mockExamId,
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        Instant expiresAt
) {
}
