package web.tosunsaeng.billing.domain.payment.exception;

public final class PaymentException extends RuntimeException {
    private final int status;
    private final String code;
    public PaymentException(int status, String code) {
        super(code); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static PaymentException unavailable() { return new PaymentException(503, "BILLING_TEMPORARILY_UNAVAILABLE"); }
    public static void require(boolean condition) { if (!condition) throw unavailable(); }
}
