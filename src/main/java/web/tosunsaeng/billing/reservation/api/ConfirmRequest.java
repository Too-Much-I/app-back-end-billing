package web.tosunsaeng.billing.reservation.api;

import java.time.Instant;

public record ConfirmRequest(String userId, String sessionId, Instant sessionCommittedAt) {
    @Override
    public String toString() {
        return "ConfirmRequest[sensitiveFields=[REDACTED]]";
    }
}
