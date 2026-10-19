package web.tosunsaeng.billing.domain.payment.purchase;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.bson.types.Decimal128;

/** Only a verified adapter may construct this internal observation. This is NOT a public request DTO. */
public record PurchaseObservation(String environment, Store store, String appId, String transactionKey,
                                  String lookupKeyVersion, String providerEventKey, Kind kind,
                                  String ownerRefId, String storeProductId, Instant purchasedAt, Money money,
                                  RefundEvidence refundEvidence) {
    public PurchaseObservation(String environment, Store store, String appId, String transactionKey,
                               String lookupKeyVersion, String providerEventKey, Kind kind, String ownerRefId,
                               String storeProductId, Instant purchasedAt, Money money) {
        this(environment, store, appId, transactionKey, lookupKeyVersion, providerEventKey, kind,
                ownerRefId, storeProductId, purchasedAt, money, null);
    }
    public enum Store { APP_STORE, PLAY_STORE }
    public enum Kind { PURCHASE, REFUND, REVOKE }
    public record Money(BigDecimal amount, String currency) {
        public Money {
            if (amount == null || amount.signum() < 0) throw invalid();
            try {
                if (currency == null || !currency.matches("[A-Z]{3}")) throw invalid();
                Currency.getInstance(currency);
                amount = new Decimal128(amount.stripTrailingZeros()).bigDecimalValue();
            } catch (RuntimeException invalid) { throw PurchaseObservation.invalid(); }
        }
        @Override public String toString() { return "Money[REDACTED]"; }
    }
    public PurchaseObservation {
        if (!Set.of("SANDBOX", "PRODUCTION").contains(Objects.requireNonNull(environment)) || store == null || kind == null) throw invalid();
        text(appId); hex(transactionKey); hex(providerEventKey); label(lookupKeyVersion);
        if (refundEvidence != null && (kind == Kind.PURCHASE
                || (refundEvidence.source() == RefundEvidence.Source.GOOGLE_ORDERS && store != Store.PLAY_STORE))) throw invalid();
        if (ownerRefId != null) uuid(ownerRefId);
        if (storeProductId != null) text(storeProductId);
        if (purchasedAt != null) {
            try { if (!Instant.ofEpochMilli(purchasedAt.toEpochMilli()).equals(purchasedAt)) throw invalid(); }
            catch (RuntimeException invalid) { throw PurchaseObservation.invalid(); }
        }
        // Incomplete verified evidence is retained for review; it cannot grant access.
    }
    Document body() {
        Document d = legacyBody();
        if (refundEvidence != null) d.append("refundEvidence", refundEvidence.body());
        return d;
    }
    private Document legacyBody() {
        return new Document("environment", environment).append("store", store.name()).append("appId", appId)
                .append("transactionKey", transactionKey).append("lookupKeyVersion", lookupKeyVersion)
                .append("kind", kind.name()).append("ownerRefId", ownerRefId).append("storeProductId", storeProductId)
                .append("purchasedAt", purchasedAt == null ? null : Date.from(purchasedAt))
                .append("money", money == null ? null : new Document("amount", new Decimal128(money.amount()))
                        .append("currency", money.currency()).append("basis", "STORE_PAID"));
    }
    String digest() {
        return digest(digestVersion());
    }
    long digestVersion() { return refundEvidence == null ? 1L : 2L; }
    String digest(long version) {
        if (version != 1L && version != 2L) throw invalid();
        if (version == 2L && refundEvidence == null) throw invalid();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((version == 1L ? legacyBody() : body()).toJson().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Observation digest unavailable"); }
    }
    static PurchaseObservation read(String eventKey, Document d) {
        Document m = d.get("money", Document.class);
        if (m != null && !"STORE_PAID".equals(m.getString("basis"))) throw invalid();
        return new PurchaseObservation(d.getString("environment"), Store.valueOf(d.getString("store")), d.getString("appId"),
                d.getString("transactionKey"), d.getString("lookupKeyVersion"), eventKey, Kind.valueOf(d.getString("kind")),
                d.getString("ownerRefId"), d.getString("storeProductId"),
                d.getDate("purchasedAt") == null ? null : d.getDate("purchasedAt").toInstant(),
                m == null ? null : new Money(m.get("amount", Decimal128.class).bigDecimalValue(), m.getString("currency")),
                d.containsKey("refundEvidence") ? RefundEvidence.read(d.get("refundEvidence", Document.class)) : null);
    }
    static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
    }
    static void label(String value) { if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) throw invalid(); }
    static void hex(String value) { if (value == null || !value.matches("[a-f0-9]{64}")) throw invalid(); }
    static void uuid(String value) {
        try { var id = UUID.fromString(value); if (!id.toString().equals(value) || id.version() != 4 || id.variant() != 2) throw invalid(); }
        catch (RuntimeException invalid) { throw PurchaseObservation.invalid(); }
    }
    static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid normalized purchase observation"); }
    @Override public String toString() { return "PurchaseObservation[REDACTED]"; }
}
