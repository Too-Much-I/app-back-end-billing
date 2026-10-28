package web.tosunsaeng.billing.domain.promotion;

import org.bson.Document;
import org.bson.types.Decimal128;

/** Historical payment evidence, not current entitlement duration. Unknown facts never allow sending. */
public final class PromotionPurchasePolicy {
    private PromotionPurchasePolicy() {}
    public static boolean qualifies(Document purchase) {
        if (purchase == null || !"READY".equals(purchase.getString("processingStatus"))
                || purchase.getDate("firstVerifiedAt") == null || purchase.get("productSnapshot") == null) return false;
        String state = purchase.getString("purchaseState");
        if ("VERIFIED".equals(state))
            return "ALLOWED".equals(purchase.getString("accessState")) && purchase.get("refund") == null;
        if (!"REFUNDED".equals(state)) return false; // revoked/unknown ownership is not a normal purchase.
        Document paid = purchase.get("money", Document.class), refund = purchase.get("refund", Document.class);
        if (paid == null || refund == null || !"STORE_PAID".equals(paid.getString("basis"))
                || !"STORE_REFUND_GROSS".equals(refund.getString("basis")) || paid.getString("currency") == null
                || !paid.getString("currency").equals(refund.getString("currency"))
                || !(paid.get("amount") instanceof Decimal128 amount)
                || !(refund.get("refundedTotal") instanceof Decimal128 total)) return false;
        try { return total.bigDecimalValue().signum() >= 0 && amount.bigDecimalValue().compareTo(total.bigDecimalValue()) > 0; }
        catch (ArithmeticException invalid) { return false; }
    }
}
