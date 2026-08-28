package web.tosunsaeng.billing.reservation.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import web.tosunsaeng.billing.reservation.application.ReservationStatusResult;
import web.tosunsaeng.billing.reservation.domain.AttemptGroup;
import web.tosunsaeng.billing.reservation.domain.Reservation;

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
        Instant expiresAt,
        Instant terminalAt
) {
    public static ReservationStatusResponse from(ReservationStatusResult result) {
        return new ReservationStatusResponse(
                result.operationId(), result.reservationId(), result.reservationKind(),
                result.reservationStatus(), result.attemptGroupId(), result.attemptGroupStatus(),
                result.sessionId(), result.mockExamId(), result.expiresAt(), result.terminalAt()
        );
    }
}
