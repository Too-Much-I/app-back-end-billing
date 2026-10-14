package web.tosunsaeng.billing.domain.payment.provider;

import java.time.Instant;

/** No upstream body, URL, credential or nested exception is exposed. */
public final class RevenueCatFailure extends RuntimeException {
    public enum Kind { AUTH, NOT_FOUND, RETRY, CONTRACT, REVIEW }
    private final Kind kind;
    private final Instant retryAt;
    public RevenueCatFailure(Kind kind, Instant retryAt) { super("RevenueCat " + kind.name()); this.kind = kind; this.retryAt = retryAt; }
    public Kind kind() { return kind; }
    public Instant retryAt() { return retryAt; }
    static RevenueCatFailure contract() { return new RevenueCatFailure(Kind.CONTRACT, null); }
}
