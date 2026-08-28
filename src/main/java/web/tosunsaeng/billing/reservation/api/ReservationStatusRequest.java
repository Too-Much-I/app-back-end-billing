package web.tosunsaeng.billing.reservation.api;

public record ReservationStatusRequest(String userId, String operationId) {
    @Override
    public String toString() {
        return "ReservationStatusRequest[sensitiveFields=[REDACTED]]";
    }
}
