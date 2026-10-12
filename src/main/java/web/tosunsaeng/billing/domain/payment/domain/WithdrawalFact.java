package web.tosunsaeng.billing.domain.payment.domain;

import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/** Minimal snapshot fact, not evidence that a historical event was received. */
public record WithdrawalFact(String userId, Instant withdrawnAt) {
    public WithdrawalFact {
        if (userId == null || !UUID.fromString(userId).toString().equals(userId) || withdrawnAt == null)
            throw new IllegalArgumentException("Invalid withdrawal fact");
        Date.from(withdrawnAt.plus(Duration.ofDays(15)));
        Date.from(withdrawnAt);
    }
    @Override public String toString() { return "WithdrawalFact[REDACTED]"; }
}
