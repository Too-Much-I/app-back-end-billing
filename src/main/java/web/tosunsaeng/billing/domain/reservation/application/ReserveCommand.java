package web.tosunsaeng.billing.domain.reservation.application;

public record ReserveCommand(
        String operationId,
        String userId,
        String sessionId,
        String mockExamId,
        String payloadHash
) {
    @Override
    public String toString() {
        return "ReserveCommand[sensitiveFields=[REDACTED]]";
    }
}
