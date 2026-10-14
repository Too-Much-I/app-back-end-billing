package web.tosunsaeng.billing.domain.payment.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.config.*;
import static org.assertj.core.api.Assertions.*;

class RefundEvidenceTest {
    @Test void unknownAndZeroAreDifferentAndRoundTripCanonical() {
        var unknown = new RefundEvidence(RefundEvidence.Source.REVENUECAT_API, "a".repeat(64), null, null, null);
        var zero = new RefundEvidence(RefundEvidence.Source.REVENUECAT_API, "a".repeat(64), null, BigDecimal.ZERO, "KRW");
        assertThat(RefundEvidence.read(unknown.body())).isEqualTo(unknown);
        assertThat(RefundEvidence.read(zero.body())).isEqualTo(zero);
        assertThat(unknown.body()).isNotEqualTo(zero.body());
    }
    @Test void strictMoneyAndInstantBoundaries() {
        assertThatThrownBy(() -> new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"a".repeat(64),null,null,"KRW")).isInstanceOf(IllegalArgumentException.class);
        for (BigDecimal amount : new BigDecimal[]{new BigDecimal("-1"),new BigDecimal("1e7000")})
            assertThatThrownBy(() -> new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"a".repeat(64),null,amount,"KRW")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"a".repeat(64),Instant.EPOCH.plusNanos(1),null,null)).isInstanceOf(IllegalArgumentException.class);
        var body = new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"a".repeat(64),null,null,null).body();
        body.put("revision",1);
        assertThatThrownBy(()->RefundEvidence.read(body)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompletePipelineCannotActivateEvenWithBothFlags() {
        var properties = new PaymentProperties(); PaymentRefundConfiguration.validate(properties);
        properties.getRefund().setPublisherEnabled(true);
        assertThatThrownBy(()->PaymentRefundConfiguration.validate(properties)).hasMessageContaining("requires processing");
        properties.getRefund().setProcessingEnabled(true);
        assertThatThrownBy(()->PaymentRefundConfiguration.validate(properties)).hasMessageContaining("pipeline incomplete");
        properties.getRefund().setPublisherEnabled(false);
        assertThatThrownBy(()->PaymentRefundConfiguration.validate(properties)).hasMessageContaining("pipeline incomplete");
    }
    @Test void legacyDigestUsesExactOldBytesAndEvidenceCannotBePurchaseOrAppleGoogle() throws Exception {
        var old = new PurchaseObservation("SANDBOX",PurchaseObservation.Store.PLAY_STORE,"fake.app","a".repeat(64),"fake-v1",
                "b".repeat(64),PurchaseObservation.Kind.REFUND,null,null,null,null);
        String bytes = "{\"environment\": \"SANDBOX\", \"store\": \"PLAY_STORE\", \"appId\": \"fake.app\", \"transactionKey\": \""
                + "a".repeat(64) + "\", \"lookupKeyVersion\": \"fake-v1\", \"kind\": \"REFUND\", \"ownerRefId\": null, \"storeProductId\": null, \"purchasedAt\": null, \"money\": null}";
        assertThat(old.body().toJson()).isEqualTo(bytes);
        assertThat(old.digest()).isEqualTo(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var evidence = new RefundEvidence(RefundEvidence.Source.GOOGLE_ORDERS,"c".repeat(64),null,null,null);
        assertThatThrownBy(()->new PurchaseObservation(old.environment(),old.store(),old.appId(),old.transactionKey(),old.lookupKeyVersion(),old.providerEventKey(),
                PurchaseObservation.Kind.PURCHASE,null,null,null,null,evidence)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PurchaseObservation(old.environment(),PurchaseObservation.Store.APP_STORE,old.appId(),old.transactionKey(),old.lookupKeyVersion(),old.providerEventKey(),
                old.kind(),null,null,null,null,evidence)).isInstanceOf(IllegalArgumentException.class);
    }
}
