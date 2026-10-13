package web.tosunsaeng.billing.domain.payment.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PurchaseObservationTest {
    final PurchaseLookupKey key = new PurchaseLookupKey(new byte[32], "test-key-1");
    PurchaseObservation observation(PurchaseObservation.Money money) {
        return new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app", "a".repeat(64),
                key.version(), "b".repeat(64), PurchaseObservation.Kind.PURCHASE, UUID.randomUUID().toString(),
                "premium_1d", Instant.parse("2026-10-13T00:00:00Z"), money);
    }
    @Test void digestRoundTripAndMoneyCanonicalization() {
        var first = observation(new PurchaseObservation.Money(new BigDecimal("9000.00"), "KRW"));
        var second = PurchaseObservation.read(first.providerEventKey(), first.body());
        assertThat(second).isEqualTo(first);
        assertThat(second.digest()).isEqualTo(first.digest());
        assertThat(first.toString()).isEqualTo("PurchaseObservation[REDACTED]");
        assertThat(first.money().toString()).isEqualTo("Money[REDACTED]");
        assertThat(key.toString()).isEqualTo("PurchaseLookupKey[REDACTED]");
        assertThat(observation(null).body()).containsEntry("money", null);
        assertThat(observation(new PurchaseObservation.Money(BigDecimal.ZERO, "KRW")).money().amount()).isZero();
    }
    @Test void hashUsesUnambiguousDomainSeparatedTupleAndCopiesSecret() {
        byte[] secret = new byte[32];
        var copy = new PurchaseLookupKey(secret, "test-key-1");
        Arrays.fill(secret, (byte) 1);
        assertThat(copy.transaction("SANDBOX", "PLAY_STORE", "a", "bc"))
                .isEqualTo(key.transaction("SANDBOX", "PLAY_STORE", "a", "bc"))
                .isNotEqualTo(key.transaction("SANDBOX", "PLAY_STORE", "ab", "c"))
                .isNotEqualTo(key.transaction("PRODUCTION", "PLAY_STORE", "a", "bc"))
                .isNotEqualTo(key.transaction("SANDBOX", "APP_STORE", "a", "bc"))
                .isNotEqualTo(key.event("SANDBOX", "a", "bc"));
    }
    @ParameterizedTest @ValueSource(strings = {"krw", "ZZZ", "KRW\n", "", "KRWW"})
    void invalidCurrencies(String currency) {
        assertThatThrownBy(() -> new PurchaseObservation.Money(BigDecimal.ONE, currency)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidNumbersAndScalars() {
        assertThatThrownBy(() -> new PurchaseObservation.Money(BigDecimal.valueOf(-1), "KRW")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseObservation.Money(new BigDecimal("1E+7000"), "KRW")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseLookupKey(new byte[31], "v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PurchaseObservation.hex("a".repeat(64) + "\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PurchaseObservation.uuid("11111111-1111-1111-8111-111111111111")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PurchaseObservation.text("secret\n")).isInstanceOf(IllegalArgumentException.class);
        var o = observation(null);
        assertThatThrownBy(() -> new PurchaseObservation(o.environment(), o.store(), o.appId(), o.transactionKey(), o.lookupKeyVersion(),
                o.providerEventKey(), o.kind(), o.ownerRefId(), o.storeProductId(), o.purchasedAt().plusNanos(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
