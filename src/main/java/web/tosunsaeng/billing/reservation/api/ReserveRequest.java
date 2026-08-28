package web.tosunsaeng.billing.reservation.api;

public record ReserveRequest(
        String userId,
        String sessionId,
        String mockExamId
) {
    @Override
    public String toString() {
        return "ReserveRequest[sensitiveFields=[REDACTED]]";
    }
}
