package web.tosunsaeng.billing.domain.attempt.exception;

import org.springframework.http.HttpStatus;

import web.tosunsaeng.billing.global.exception.InternalApiException;

public final class AttemptGroupEventException extends InternalApiException {

    private AttemptGroupEventException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Integer retryAfterSeconds
    ) {
        super(status, code, message, retryable, retryAfterSeconds);
    }

    public static AttemptGroupEventException unsupportedContract() {
        return new AttemptGroupEventException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_CONTRACT",
                "The request contract is not supported.",
                false,
                null
        );
    }

    public static AttemptGroupEventException eventIdConflict() {
        return new AttemptGroupEventException(
                HttpStatus.CONFLICT,
                "EVENT_ID_CONFLICT",
                "The event conflicts with an existing event.",
                false,
                null
        );
    }

    public static AttemptGroupEventException targetConflict() {
        return new AttemptGroupEventException(
                HttpStatus.CONFLICT,
                "EVENT_TARGET_CONFLICT",
                "The event target conflicts with the current projection.",
                false,
                null
        );
    }

    public static AttemptGroupEventException projectionNotReady() {
        return new AttemptGroupEventException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ATTEMPT_PROJECTION_NOT_READY",
                "The attempt projection is not ready.",
                true,
                5
        );
    }

    public static AttemptGroupEventException temporarilyUnavailable() {
        return new AttemptGroupEventException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_TEMPORARILY_UNAVAILABLE",
                "Billing is temporarily unavailable.",
                true,
                1
        );
    }
}
