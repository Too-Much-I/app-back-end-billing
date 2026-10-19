package web.tosunsaeng.billing.domain.payment.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.bson.Document;
import org.bson.types.Decimal128;

/** Final Store evidence from a verified adapter, never a public DTO or operator-entered amount. */
public record RefundEvidence(Source source, String evidenceKey, Instant providerConfirmedAt,
                             BigDecimal amount, String currency) {
    public enum Source { REVENUECAT_API, REVENUECAT_WEBHOOK, GOOGLE_ORDERS }
    public RefundEvidence {
        if (source == null) throw PurchaseObservation.invalid();
        PurchaseObservation.hex(evidenceKey);
        if (providerConfirmedAt != null && !Instant.ofEpochMilli(providerConfirmedAt.toEpochMilli()).equals(providerConfirmedAt))
            throw PurchaseObservation.invalid();
        if (amount == null) { if (currency != null) throw PurchaseObservation.invalid(); }
        else {
            var money = new PurchaseObservation.Money(amount, currency);
            amount = money.amount(); currency = money.currency();
        }
    }
    Document body() {
        return new Document("revision", 1L).append("source", source.name()).append("evidenceKey", evidenceKey)
                .append("providerConfirmedAt", providerConfirmedAt == null ? null : Date.from(providerConfirmedAt))
                .append("amountMode", amount == null ? "UNKNOWN" : "CUMULATIVE")
                .append("amount", amount == null ? null : new Decimal128(amount)).append("currency", currency)
                .append("basis", amount == null ? null : "STORE_REFUND_GROSS");
    }
    static RefundEvidence read(Document d) {
        if (!d.keySet().equals(Set.of("revision", "source", "evidenceKey", "providerConfirmedAt", "amountMode", "amount", "currency", "basis"))
                || !Long.valueOf(1).equals(d.get("revision"))) throw PurchaseObservation.invalid();
        boolean unknown = "UNKNOWN".equals(d.getString("amountMode"));
        if (unknown ? d.get("amount") != null || d.get("currency") != null || d.get("basis") != null
                : !"CUMULATIVE".equals(d.getString("amountMode")) || !(d.get("amount") instanceof Decimal128)
                    || !"STORE_REFUND_GROSS".equals(d.getString("basis"))) throw PurchaseObservation.invalid();
        return new RefundEvidence(Source.valueOf(d.getString("source")), d.getString("evidenceKey"),
                d.getDate("providerConfirmedAt") == null ? null : d.getDate("providerConfirmedAt").toInstant(),
                unknown ? null : d.get("amount", Decimal128.class).bigDecimalValue(), d.getString("currency"));
    }
    @Override public String toString() { return "RefundEvidence[REDACTED]"; }
}
