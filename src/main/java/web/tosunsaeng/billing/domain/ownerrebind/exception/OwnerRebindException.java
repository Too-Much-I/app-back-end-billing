package web.tosunsaeng.billing.domain.ownerrebind.exception;

import org.springframework.http.HttpStatus;

import web.tosunsaeng.billing.global.exception.InternalApiException;

public final class OwnerRebindException extends InternalApiException {

    private OwnerRebindException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Integer retryAfterSeconds
    ) {
        super(status, code, message, retryable, retryAfterSeconds);
    }

    public static OwnerRebindException unsupportedContract() {
        return new OwnerRebindException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_CONTRACT",
                "The request contract is not supported.",
                false,
                null
        );
    }

    public static OwnerRebindException eventIdConflict() {
        return new OwnerRebindException(
                HttpStatus.CONFLICT,
                "EVENT_ID_CONFLICT",
                "The event conflicts with an existing event.",
                false,
                null
        );
    }

    public static OwnerRebindException ownerConflict() {
        return new OwnerRebindException(
                HttpStatus.CONFLICT,
                "OWNER_REBIND_CONFLICT",
                "The owner rebind conflicts with the current state.",
                false,
                null
        );
    }

    public static OwnerRebindException pending(int retryAfterSeconds) {
        return new OwnerRebindException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OWNER_REBIND_PENDING",
                "The owner rebind prerequisites are not ready.",
                true,
                retryAfterSeconds
        );
    }

    public static OwnerRebindException temporarilyUnavailable() {
        return new OwnerRebindException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_TEMPORARILY_UNAVAILABLE",
                "Billing is temporarily unavailable.",
                true,
                1
        );
    }
}
