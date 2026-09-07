package web.tosunsaeng.billing.global.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicResponse<T>(@JsonProperty("isSuccess") boolean success, String code, String message, T result) {
    public static <T> PublicResponse<T> success(T value) {
        return new PublicResponse<>(true, "SUCCESS", "요청에 성공했습니다.", value);
    }
    public static PublicResponse<Void> failure(String code) {
        String message = switch (code) {
            case "UNAUTHENTICATED" -> "인증이 필요합니다.";
            case "FORBIDDEN" -> "접근 권한이 없습니다.";
            case "NOT_FOUND" -> "요청한 경로를 찾을 수 없습니다.";
            case "INVALID_REQUEST" -> "올바르지 않은 요청입니다.";
            case "METHOD_NOT_ALLOWED" -> "허용하지 않는 요청 방식입니다.";
            case "RATE_LIMITED" -> "잠시 후 다시 시도해 주세요.";
            default -> "사용권 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.";
        };
        return new PublicResponse<>(false, code, message, null);
    }
}
