package web.tosunsaeng.billing.domain.reservation.dto.request;

import java.time.Instant;

public record ConfirmRequest(String userId, String sessionId, Instant sessionCommittedAt) {
    @Override
    public String toString() {
        return "ConfirmRequest[sensitiveFields=[REDACTED]]";
    }
}
