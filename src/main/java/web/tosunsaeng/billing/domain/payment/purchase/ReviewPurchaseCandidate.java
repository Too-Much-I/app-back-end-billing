package web.tosunsaeng.billing.domain.payment.purchase;

import java.time.Instant;
import java.util.Date;
import org.bson.Document;

/** Matching hint only. Never accepted as purchase evidence or used to grant access. */
public record ReviewPurchaseCandidate(String environment, PurchaseObservation.Store store, String appId,
        String transactionKey, String lookupKeyVersion, String ownerRefId, String storeProductId, Instant purchasedAt) {
    public ReviewPurchaseCandidate {
        new PurchaseObservation(environment, store, appId, transactionKey, lookupKeyVersion, "0".repeat(64),
                PurchaseObservation.Kind.PURCHASE, ownerRefId, storeProductId, purchasedAt, null);
        java.util.Objects.requireNonNull(purchasedAt);
    }
    public Document body() {
        return new Document("environment", environment).append("store", store.name()).append("appId", appId)
                .append("transactionKey", transactionKey).append("lookupKeyVersion", lookupKeyVersion)
                .append("ownerRefId", ownerRefId).append("storeProductId", storeProductId)
                .append("purchasedAt", Date.from(purchasedAt));
    }
    @Override public String toString() { return "ReviewPurchaseCandidate[REDACTED]"; }
}
