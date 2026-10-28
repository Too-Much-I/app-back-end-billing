package web.tosunsaeng.billing.domain.promotion;

import java.util.Date;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PromotionPurchasePolicyTest {
    Document purchase(){return new Document("purchaseState","VERIFIED").append("accessState","ALLOWED")
            .append("processingStatus","READY").append("firstVerifiedAt",new Date()).append("productSnapshot",new Document());}
    @Test void expiredButNotRefundedStillQualifies(){assertThat(PromotionPurchasePolicy.qualifies(purchase())).isTrue();}
    @Test void unresolvedAndRevokedAreDenied(){
        assertThat(PromotionPurchasePolicy.qualifies(purchase().append("processingStatus","REVIEW_REQUIRED"))).isFalse();
        assertThat(PromotionPurchasePolicy.qualifies(purchase().append("purchaseState","REVOKED"))).isFalse();
    }
    @Test void fullUnknownAndPartialRefunds(){
        Document p=purchase().append("purchaseState","REFUNDED").append("accessState","REVOKED");
        assertThat(PromotionPurchasePolicy.qualifies(p)).isFalse();
        p.append("money",new Document("basis","STORE_PAID").append("currency","KRW").append("amount",new Decimal128(100)))
                .append("refund",new Document("basis","STORE_REFUND_GROSS").append("currency","KRW").append("refundedTotal",new Decimal128(100)));
        assertThat(PromotionPurchasePolicy.qualifies(p)).isFalse();
        p.get("refund",Document.class).put("refundedTotal",new Decimal128(50));
        assertThat(PromotionPurchasePolicy.qualifies(p)).isTrue();
        assertThat(java.util.List.of(p.append("processingStatus","REVIEW_REQUIRED"),purchase()).stream().anyMatch(PromotionPurchasePolicy::qualifies)).isTrue();
    }
}
