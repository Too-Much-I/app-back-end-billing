package web.tosunsaeng.billing.domain.payment.purchase;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fixed lookup key, not a provider credential. No key rotation or runtime bean in this slice. */
public final class PurchaseLookupKey {
    private final byte[] key;
    private final String version;
    public PurchaseLookupKey(byte[] key, String version) {
        if (key == null || key.length < 32 || key.length > 64) throw new IllegalArgumentException("Invalid lookup key");
        PurchaseObservation.label(version);
        this.key = key.clone(); this.version = version;
    }
    public String version() { return version; }
    public String transaction(String environment, String store, String appId, String transactionId) {
        return hash("transaction-r1", environment, store, appId, transactionId);
    }
    public String event(String environment, String project, String eventId) {
        return hash("revenuecat-event-r1", environment, project, eventId);
    }
    public String apiObservation(String environment, String project, String purchaseId, String factsDigest) {
        PurchaseObservation.hex(factsDigest);
        return hash("revenuecat-api-observation-r1", environment, project, purchaseId, factsDigest);
    }
    private String hash(String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            for (String value : values) {
                PurchaseObservation.text(value);
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                mac.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); mac.update(bytes);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (IllegalArgumentException invalid) { throw invalid; }
        catch (Exception error) { throw new IllegalStateException("Lookup key unavailable"); }
    }
    @Override public String toString() { return "PurchaseLookupKey[REDACTED]"; }
}
