package web.tosunsaeng.billing.domain.payment.purchase;

import java.util.*;
import org.bson.Document;

/** Shared read-only proof for separating amount review from access uncertainty. */
public final class RefundReviewPolicy {
    private RefundReviewPolicy() {}
    public static boolean accessEnded(Document purchase, Document ledger) {
        if (purchase == null || ledger == null || !Set.of("REFUNDED", "REVOKED").contains(purchase.getString("purchaseState"))
                || !"REVOKED".equals(purchase.getString("accessState")) || !"READY".equals(purchase.getString("processingStatus"))) return false;
        Document refund = purchase.get("refund", Document.class), detail = ledger.get("detail", Document.class);
        return refund != null && refund.getString("effectId") != null && refund.getDate("appliedAt") != null && detail != null
                && "ACCESS_REVOKED".equals(ledger.getString("type"))
                && Objects.equals(purchase.getString("_id"), ledger.getString("purchaseId"))
                && ("refund-access:" + purchase.getString("_id")).equals(ledger.getString("effectKey"))
                && refund.getString("effectId").equals(detail.getString("effectId"))
                && refund.getDate("appliedAt").equals(detail.getDate("appliedAt"));
    }
    public static boolean matches(Document facts, Document purchase) {
        if (facts == null || purchase == null || purchase.getDate("purchasedAt") == null || purchase.getString("storeProductId") == null) return false;
        String env = purchase.getString("storeEnvironment") == null ? purchase.getString("environment") : purchase.getString("storeEnvironment");
        if (!Objects.equals(env, facts.getString("environment"))) return false;
        for (String field : List.of("store", "appId", "transactionKey", "lookupKeyVersion", "ownerRefId", "storeProductId", "purchasedAt"))
            if (purchase.get(field) == null || !Objects.equals(purchase.get(field), facts.get(field))) return false;
        return true;
    }
    public static boolean amountOnly(Document review, Document purchase, Document ledger) {
        Document facts = review.get("normalizedObservation", Document.class);
        return "REFUND_AMOUNT_UNKNOWN".equals(review.getString("reason"))
                && accessEnded(purchase, ledger) && matches(facts, purchase)
                && Set.of("REFUND", "REVOKE").contains(facts.getString("kind"))
                && Objects.equals(review.getString("environment"), purchase.getString("environment"))
                && Objects.equals(review.getString("accountId"), purchase.getString("accountId"))
                && Objects.equals(review.getString("purchaseId"), purchase.getString("_id"));
    }
}
