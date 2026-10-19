package web.tosunsaeng.billing.domain.payment;

import java.util.*;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import web.tosunsaeng.billing.domain.payment.application.PaymentCatalogService;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.PaymentOffer;
import static org.assertj.core.api.Assertions.*;

class PaymentCatalogTest {
    private PaymentCatalogService catalog() {
        var p = new PaymentProperties(); p.setEnvironment("SANDBOX");
        return new PaymentCatalogService(null, p);
    }
    @Test void fiveOffersHaveApprovedDurationsAndStoreOrdering() {
        List<Document> rows = new ArrayList<>();
        for (var offer : PaymentOffer.values()) {
            for (String platform : List.of("PLAY_STORE", "APP_STORE")) {
                var row = PaymentFoundationIntegrationTest.product();
                row.put("offerCode", offer.name()); row.put("durationSeconds", offer.durationSeconds);
                row.put("referencePriceKrw", offer.referencePriceKrw); row.put("store", platform);
                row.put("storeProductId", offer.name()); row.put("rcProductId", platform + offer.name());
                rows.add(row);
            }
        }
        Collections.reverse(rows);
        var response = catalog().convert(rows);
        assertThat(response.products()).extracting(p -> p.durationSeconds()).containsExactly(86400L, 259200L, 604800L, 1209600L, 2419200L);
        response.products().forEach(product -> {
            assertThat(product.autoRenew()).isFalse();
            assertThat(product.storeProducts()).extracting(p -> p.store()).containsExactly("APP_STORE", "PLAY_STORE");
        });
    }
    static Stream<String> fields() {
        return Stream.of("environment", "saleEnabled", "offerCode", "catalogVersion", "durationSeconds", "referencePriceKrw",
                "referenceCurrency", "store", "appId", "storeProductId", "rcProductId", "packageId");
    }
    @ParameterizedTest @MethodSource("fields")
    void incompleteActiveMappingCannotBecomeEmptySuccess(String field) {
        var row = PaymentFoundationIntegrationTest.product(); row.remove(field);
        assertThatThrownBy(() -> catalog().convert(List.of(row))).isInstanceOf(RuntimeException.class);
    }
    @Test void wrongDurationVersionAndDuplicateMappingFailClosed() {
        for (String field : List.of("catalogVersion", "durationSeconds", "referencePriceKrw")) {
            var row = PaymentFoundationIntegrationTest.product(); row.put(field, 123456L);
            assertThatThrownBy(() -> catalog().convert(List.of(row))).isInstanceOf(RuntimeException.class);
        }
        var row = PaymentFoundationIntegrationTest.product();
        assertThatThrownBy(() -> catalog().convert(List.of(row, row))).isInstanceOf(RuntimeException.class);
    }
    @Test void productionSubsetMatchesAcceptedFixtureWithoutPrematureWithdrawalCollections() throws Exception {
        var json = new ObjectMapper();
        try (var production = getClass().getResourceAsStream("/payment-foundation-indexes.json");
             var fixture = getClass().getResourceAsStream("/contracts/payment-lifecycle/v1/billing-indexes.json")) {
            var actual = json.readTree(production).get("indexes");
            var expected = json.createArrayNode();
            json.readTree(fixture).get("indexes").forEach(index -> {
                if (!Set.of("payment_withdrawal_inbox", "payment_account_cleanup_jobs").contains(index.get("collection").asText())) expected.add(index);
            });
            assertThat(actual).isEqualTo(expected);
            assertThat(actual.size()).isEqualTo(12);
        }
    }
    @Test void withdrawalSubsetMatchesRemainingFourAcceptedIndexes() throws Exception {
        var json = new ObjectMapper();
        try (var production = getClass().getResourceAsStream("/payment-withdrawal-indexes.json");
             var fixture = getClass().getResourceAsStream("/contracts/payment-lifecycle/v1/billing-indexes.json")) {
            var actual = json.readTree(production).get("indexes");
            var expected = json.createArrayNode();
            json.readTree(fixture).get("indexes").forEach(index -> {
                if (Set.of("payment_withdrawal_inbox", "payment_account_cleanup_jobs").contains(index.get("collection").asText())) expected.add(index);
            });
            assertThat(actual).isEqualTo(expected);
            assertThat(actual.size()).isEqualTo(4);
        }
    }
}
