package web.tosunsaeng.billing.global.api;

import org.springframework.http.HttpStatus;

public class InternalApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    public InternalApiException(HttpStatus status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, null);
    }

    public InternalApiException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Integer retryAfterSeconds
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static InternalApiException invalidRequest() {
        return new InternalApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request is invalid.",
                false
        );
    }

    public static InternalApiException unsupportedContract() {
        return new InternalApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_CONTRACT",
                "The request contract is not supported.",
                false
        );
    }

    public static InternalApiException eventConflict() {
        return new InternalApiException(
                HttpStatus.CONFLICT,
                "EVENT_ID_CONFLICT",
                "The event conflicts with an existing event.",
                false
        );
    }

    public static InternalApiException temporarilyUnavailable() {
        return new InternalApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_TEMPORARILY_UNAVAILABLE",
                "Billing is temporarily unavailable.",
                true,
                1
        );
    }
}
