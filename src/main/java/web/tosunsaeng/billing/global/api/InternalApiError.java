package web.tosunsaeng.billing.global.api;

public record InternalApiError(
        String code,
        String message,
        boolean retryable,
        String correlationId
) {
}
