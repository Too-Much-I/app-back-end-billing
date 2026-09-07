package web.tosunsaeng.billing.domain.entitlement.exception;

public final class EntitlementQueryException extends RuntimeException {
    public enum Failure { INVARIANT, LIMIT, STORAGE, LEGACY_SESSION_ATTRIBUTION_MISSING }
    private final Failure failure;
    public EntitlementQueryException(Failure failure) { super(failure.name()); this.failure = failure; }
    public Failure failure() { return failure; }
    public static void require(boolean condition) {
        if (!condition) { throw new EntitlementQueryException(Failure.INVARIANT); }
    }
}
