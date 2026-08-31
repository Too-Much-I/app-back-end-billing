package web.tosunsaeng.billing.domain.reservation.dto.request;

public record ReservationStatusRequest(String userId, String operationId) {
    @Override
    public String toString() {
        return "ReservationStatusRequest[sensitiveFields=[REDACTED]]";
    }
}
