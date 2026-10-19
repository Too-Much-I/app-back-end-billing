package web.tosunsaeng.billing.domain.payment.query;

import java.time.Instant;

public record PaidQueryResponse(Status status, Boolean active, String activeOfferCode, Instant activeEndsAt,
                                Instant continuousAccessEndsAt, Instant nextStartsAt, boolean hasPendingSync) {
    public enum Status { READY, PENDING }
    public static PaidQueryResponse unknown(boolean pending) { return new PaidQueryResponse(Status.PENDING, null, null, null, null, null, pending); }
    public static PaidQueryResponse empty(boolean pending) { return new PaidQueryResponse(Status.READY, false, null, null, null, null, pending); }
}
