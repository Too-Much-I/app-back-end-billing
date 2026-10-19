package web.tosunsaeng.billing.domain.payment.domain;

public enum PaymentOffer {
    PREMIUM_1D(86400, 9000), PREMIUM_3D(259200, 19000), PREMIUM_7D(604800, 29000),
    PREMIUM_14D(1209600, 49000), PREMIUM_28D(2419200, 69000);
    public final long durationSeconds;
    public final long referencePriceKrw;
    PaymentOffer(long durationSeconds, long referencePriceKrw) {
        this.durationSeconds = durationSeconds; this.referencePriceKrw = referencePriceKrw;
    }
}
