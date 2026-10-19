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
        Reservation.ContinuationReason continuationReason,
        String continuationId,
        Instant expiresAt,
        Instant terminalAt,
        web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot.Type entitlementSource,
        web.tosunsaeng.billing.domain.reservation.paid.ExamTransition transition,
        ConfirmRejection confirmRejection
) {
    public record ConfirmRejection(String code) { }
    public ReservationStatusResult(String operationId, String reservationId, Reservation.Kind reservationKind,
            Reservation.Status reservationStatus, String attemptGroupId, AttemptGroup.Status attemptGroupStatus,
            String sessionId, String mockExamId, Reservation.ContinuationReason continuationReason, String continuationId,
            Instant expiresAt, Instant terminalAt, web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot.Type entitlementSource) {
        this(operationId, reservationId, reservationKind, reservationStatus, attemptGroupId, attemptGroupStatus,
                sessionId, mockExamId, continuationReason, continuationId, expiresAt, terminalAt, entitlementSource, null, null);
    }
    public ReservationStatusResult(String operationId, String reservationId, Reservation.Kind reservationKind,
            Reservation.Status reservationStatus, String attemptGroupId, AttemptGroup.Status attemptGroupStatus,
            String sessionId, String mockExamId, Reservation.ContinuationReason continuationReason, String continuationId,
            Instant expiresAt, Instant terminalAt) {
        this(operationId, reservationId, reservationKind, reservationStatus, attemptGroupId, attemptGroupStatus,
                sessionId, mockExamId, continuationReason, continuationId, expiresAt, terminalAt, null);
    }
    public ReservationStatusResult(
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
        this(
                operationId, reservationId, reservationKind, reservationStatus,
                attemptGroupId, attemptGroupStatus, sessionId, mockExamId,
                null, null, expiresAt, terminalAt
        );
    }
}
