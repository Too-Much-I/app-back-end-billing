package web.tosunsaeng.billing.domain.reservation.exception;

import org.springframework.http.HttpStatus;

import web.tosunsaeng.billing.global.exception.InternalApiException;

public final class ReservationException extends InternalApiException {

    private ReservationException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Integer retryAfterSeconds
    ) {
        super(status, code, message, retryable, retryAfterSeconds);
    }

    public static ReservationException memberEvidencePending() {
        return new ReservationException(HttpStatus.SERVICE_UNAVAILABLE, "MEMBER_EVIDENCE_PENDING",
                "The operation is waiting for member evidence and request resumption.", true, 1);
    }
    public static ReservationException operationOwnerMismatch() {
        return new ReservationException(HttpStatus.CONFLICT, "OPERATION_OWNER_MISMATCH",
                "The operation owner does not match the current owner.", false, null);
    }
    public static ReservationException rejectedBeforeCreation() {
        return new ReservationException(HttpStatus.CONFLICT, "RESERVE_REJECTED_BEFORE_CREATION",
                "The reserve operation was rejected before reservation creation.", false, null);
    }
    public static ReservationException transitionContextConflict() {
        return new ReservationException(HttpStatus.CONFLICT, "TRANSITION_CONTEXT_CONFLICT",
                "The transition context conflicts with the current exam.", false, null);
    }

    public static ReservationException invalidIdempotencyKey() {
        return new ReservationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY",
                "The idempotency key is invalid.",
                false,
                null
        );
    }

    public static ReservationException entitlementInsufficient() {
        return new ReservationException(
                HttpStatus.PAYMENT_REQUIRED,
                "ENTITLEMENT_INSUFFICIENT",
                "An eligible entitlement is not available.",
                false,
                null
        );
    }

    public static ReservationException commandProcessing() {
        return new ReservationException(
                HttpStatus.CONFLICT,
                "COMMAND_PROCESSING",
                "The operation is still processing.",
                true,
                1
        );
    }

    public static ReservationException idempotencyConflict() {
        return new ReservationException(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_CONFLICT",
                "The idempotency key conflicts with an existing command.",
                false,
                null
        );
    }

    public static ReservationException stateConflict() {
        return new ReservationException(
                HttpStatus.CONFLICT,
                "RESERVATION_STATE_CONFLICT",
                "The reservation state conflicts with the request.",
                false,
                null
        );
    }

    public static ReservationException operationNotFound() {
        return new ReservationException(
                HttpStatus.NOT_FOUND,
                "OPERATION_NOT_FOUND",
                "The operation was not found.",
                false,
                null
        );
    }

    public static ReservationException temporarilyUnavailable() {
        return new ReservationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_TEMPORARILY_UNAVAILABLE",
                "Billing is temporarily unavailable.",
                true,
                1
        );
    }
}
