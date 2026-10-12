package web.tosunsaeng.billing.domain.payment.domain;

import java.time.Instant;

/** Validated internal command; never log this record or its source payload. */
public record PaymentWithdrawal(String eventId, String userId, Instant withdrawnAt, String digest) {
    @Override public String toString() { return "PaymentWithdrawal[REDACTED]"; }
}
