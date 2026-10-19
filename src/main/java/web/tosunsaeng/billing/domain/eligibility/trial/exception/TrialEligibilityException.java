package web.tosunsaeng.billing.domain.eligibility.trial.exception;

import org.springframework.http.HttpStatus;

import web.tosunsaeng.billing.global.exception.InternalApiException;

public final class TrialEligibilityException extends InternalApiException {

    private TrialEligibilityException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Integer retryAfterSeconds
    ) {
        super(status, code, message, retryable, retryAfterSeconds);
    }

    public static TrialEligibilityException unsupportedContract() {
        return new TrialEligibilityException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_CONTRACT",
                "The request contract is not supported.",
                false,
                null
        );
    }

    public static TrialEligibilityException eventConflict() {
        return new TrialEligibilityException(
                HttpStatus.CONFLICT,
                "EVENT_ID_CONFLICT",
                "The event conflicts with an existing event.",
                false,
                null
        );
    }

    public static TrialEligibilityException temporarilyUnavailable() {
        return new TrialEligibilityException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_TEMPORARILY_UNAVAILABLE",
                "Billing is temporarily unavailable.",
                true,
                1
        );
    }
}
