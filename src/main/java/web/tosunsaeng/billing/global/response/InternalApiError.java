package web.tosunsaeng.billing.global.response;

public record InternalApiError(
        String code,
        String message,
        boolean retryable,
        String correlationId
) {
}
