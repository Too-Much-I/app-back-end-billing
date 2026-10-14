package web.tosunsaeng.billing.domain.payment.provider;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import static org.assertj.core.api.Assertions.*;

class RevenueCatAdapterTest {
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    static final String REF = "11111111-1111-4111-8111-111111111111";
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final ObjectMapper JSON = new ObjectMapper();
    final PurchaseLookupKey lookup = new PurchaseLookupKey(new byte[32], "test-v1");
    final RevenueCatPurchaseDecoder decoder = new RevenueCatPurchaseDecoder("project", "SANDBOX", lookup,
            Map.of("prod", new RevenueCatPurchaseDecoder.Product("app", PurchaseObservation.Store.PLAY_STORE, "premium_1d")), CLOCK);
    ObjectNode purchase() {
        ObjectNode n = JSON.createObjectNode(); n.put("object", "purchase"); n.put("id", "purc-test");
        n.put("customer_id", REF); n.put("original_customer_id", REF); n.put("product_id", "prod");
        n.put("store_purchase_identifier", "GPA.fake-transaction"); n.put("environment", "sandbox"); n.put("store", "play_store");
        n.put("ownership", "purchased"); n.put("status", "owned"); n.put("quantity", 1); n.put("purchased_at", NOW.minusSeconds(100).toEpochMilli()); return n;
    }
    byte[] bytes(JsonNode n) { return n.toString().getBytes(StandardCharsets.UTF_8); }
    byte[] page(String next) {
        ObjectNode root = JSON.createObjectNode().put("object", "list"); root.putArray("items").add(purchase()); root.put("next_page", next); return bytes(root);
    }
    RevenueCatReadClient.Response response(int status, Map<String, List<String>> headers, byte[] b) { return new RevenueCatReadClient.Response(status, headers, b); }
    RevenueCatReadClient client(RevenueCatReadClient.Transport transport) { return new RevenueCatReadClient("project", "SANDBOX", "fake-secret", transport, decoder, CLOCK); }
    @Test void apiFactsNormalizeWithoutUsingUsdRevenueAndAreStable() {
        var n = purchase(); n.putObject("revenue_in_usd").put("gross", 99);
        var a = decoder.purchase(bytes(n), REF); n.put("extra", "ignored");
        var b = decoder.purchase(bytes(n), REF);
        assertThat(a.observation()).isEqualTo(b.observation()); assertThat(a.observation().money()).isNull();
        assertThat(a.observation().transactionKey()).isEqualTo(lookup.transaction("SANDBOX", "PLAY_STORE", "app", "GPA.fake-transaction"));
        assertThat(a.observation().providerEventKey()).isNotEqualTo(lookup.event("SANDBOX", "project", "purc-test"));
        n.put("status", "refunded"); var refund = decoder.purchase(bytes(n), REF);
        assertThat(refund.observation().kind()).isEqualTo(PurchaseObservation.Kind.REFUND);
        assertThat(refund.observation().providerEventKey()).isNotEqualTo(a.observation().providerEventKey());
        assertThat(a.toString()).doesNotContain(REF, "GPA", "purc-test");
    }
    @Test void appleUsesItsOwnCatalogAndStoreTransactionNamespace() {
        var d = new RevenueCatPurchaseDecoder("project", "SANDBOX", lookup,
                Map.of("prod", new RevenueCatPurchaseDecoder.Product("ios-app", PurchaseObservation.Store.APP_STORE, "premium_1d")), CLOCK);
        var item = d.purchase(bytes(purchase().put("store", "app_store").put("store_purchase_identifier", "100000001")), REF);
        assertThat(item.observation().store()).isEqualTo(PurchaseObservation.Store.APP_STORE);
        assertThat(item.observation().transactionKey()).isEqualTo(lookup.transaction("SANDBOX", "APP_STORE", "ios-app", "100000001"));
    }
    @Test void malformedLastItemNeverReturnsPartialPage() {
        ObjectNode root = JSON.createObjectNode().put("object", "list").putNull("next_page");
        root.putArray("items").add(purchase()).add(purchase().put("id", "other").put("quantity", "1"));
        assertThatThrownBy(() -> decoder.page(bytes(root), REF)).isInstanceOf(RevenueCatFailure.class);
        root.putArray("items"); for (int i = 0; i < 101; i++) root.withArray("items").add(purchase().put("id", "id" + i));
        assertThatThrownBy(() -> decoder.page(bytes(root), REF)).isInstanceOf(RevenueCatFailure.class);
    }
    @ParameterizedTest @CsvSource({"environment,production", "customer_id,other", "original_customer_id,other", "ownership,family_shared", "store,app_store", "status,future", "product_id,unknown"})
    void unsupportedEvidenceIsReviewNotPurchase(String field, String value) {
        var n = purchase().put(field, value); var result = decoder.purchase(bytes(n), REF);
        assertThat(result.observation()).isNull(); assertThat(result.reviewReason()).isNotBlank();
    }
    @ParameterizedTest @ValueSource(strings = {"quantity", "purchased_at", "store_purchase_identifier", "id", "customer_id", "status", "ownership", "product_id"})
    void missingRequiredFieldIsContractError(String field) {
        var n = purchase(); n.remove(field); assertThatThrownBy(() -> decoder.purchase(bytes(n), REF)).isInstanceOf(RevenueCatFailure.class);
    }
    @Test void strictNumbersFutureTimeAndQuantity() {
        var n = purchase().put("quantity", "1"); assertThatThrownBy(() -> decoder.purchase(bytes(n), REF)).isInstanceOf(RevenueCatFailure.class);
        final var floatTime = purchase().put("purchased_at", 1.0);
        assertThatThrownBy(() -> decoder.purchase(bytes(floatTime), REF)).isInstanceOf(RevenueCatFailure.class);
        assertThat(decoder.purchase(bytes(purchase().put("quantity", 2)), REF).reviewReason()).isEqualTo("QUANTITY_UNSUPPORTED");
        assertThat(decoder.purchase(bytes(purchase().put("purchased_at", NOW.plusMillis(1).toEpochMilli())), REF).reviewReason()).isEqualTo("PURCHASE_TIME_INVALID");
        final var numericId = purchase().put("store_purchase_identifier", 123L);
        assertThatThrownBy(() -> decoder.purchase(bytes(numericId), REF)).isInstanceOf(RevenueCatFailure.class);
    }
    @Test void duplicateTrailingOversizeAndDuplicatePageItemRejected() {
        for (String s : List.of("{\"object\":\"purchase\",\"object\":\"purchase\"}", purchase() + " {}", "[]"))
            assertThatThrownBy(() -> decoder.purchase(s.getBytes(StandardCharsets.UTF_8), REF)).isInstanceOf(RevenueCatFailure.class);
        assertThatThrownBy(() -> decoder.page(new byte[RevenueCatPurchaseDecoder.MAX_BYTES + 1], REF)).isInstanceOf(RevenueCatFailure.class);
        ObjectNode root = JSON.createObjectNode().put("object", "list").putNull("next_page"); root.putArray("items").add(purchase()).add(purchase());
        assertThatThrownBy(() -> decoder.page(bytes(root), REF)).isInstanceOf(RevenueCatFailure.class);
    }
    @Test void customerPageBuildsOnlyFixedUrlAndDecodesCursor() {
        AtomicReference<java.net.URI> uri = new AtomicReference<>();
        var client = client((u, b) -> { uri.set(u); assertThat(b).isEqualTo("fake-secret"); return response(200, Map.of("Content-Type", List.of("application/json")),
                page("/v2/projects/project/customers/" + REF + "/purchases?starting_after=next%2Fid&environment=sandbox&limit=100")); });
        assertThat(client.purchases(REF, null).nextCursor()).isEqualTo("next/id");
        assertThat(uri.get().toString()).isEqualTo("https://api.revenuecat.com/v2/projects/project/customers/" + REF + "/purchases?environment=sandbox&limit=100");
        assertThat(client.toString()).doesNotContain("fake-secret");
    }
    static Stream<String> badNext() {
        String path = "/v2/projects/project/customers/" + REF + "/purchases";
        return Stream.of("https://evil.invalid" + path + "?starting_after=x", "//evil.invalid" + path + "?starting_after=x",
                path + "?starting_after=a&starting_after=b", path + "?starting_after=x&environment=production", path + "?starting_after=x&limit=200",
                path + "?starting_after=x&extra=1", path + "?starting_after=x#fragment", path + "?environment=sandbox",
                "/v2/projects/other/customers/" + REF + "/purchases?starting_after=x");
    }
    @ParameterizedTest @MethodSource("badNext")
    void paginationCannotChangeTrustContext(String next) {
        var c = client((u, b) -> response(200, Map.of("Content-Type", List.of("application/json")), page(next)));
        assertThatThrownBy(() -> c.purchases(REF, null)).isInstanceOf(RevenueCatFailure.class);
    }
    @ParameterizedTest @CsvSource({"401,AUTH", "403,AUTH", "404,NOT_FOUND", "423,RETRY", "429,RETRY", "503,RETRY", "500,RETRY", "302,CONTRACT", "204,CONTRACT", "400,CONTRACT"})
    void statusesAreSafeAndNotEmptySuccess(int status, RevenueCatFailure.Kind kind) {
        var c = client((u, b) -> response(status, Map.of(), "sensitive upstream body".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> c.purchases(REF, null)).isInstanceOfSatisfying(RevenueCatFailure.class, e -> {
            assertThat(e.kind()).isEqualTo(kind); assertThat(e.getCause()).isNull(); assertThat(e.getMessage()).doesNotContain("sensitive");
        });
    }
    @Test void retryAfterDateSecondsAndInvalidValues() {
        for (String value : List.of("12", DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.plusSeconds(12).atZone(ZoneOffset.UTC)))) {
            var c = client((u,b) -> response(429, Map.of("Retry-After", List.of(value)), new byte[0]));
            assertThatThrownBy(() -> c.purchases(REF, null)).isInstanceOfSatisfying(RevenueCatFailure.class,
                    e -> assertThat(e.retryAt()).isEqualTo(NOW.plusSeconds(12)));
        }
        for (List<String> values : List.of(List.of("bad"), List.of("86401"), List.of("1", "2"))) {
            var c = client((u,b) -> response(503, Map.of("Retry-After", values), new byte[0]));
            assertThatThrownBy(() -> c.purchases(REF, null)).isInstanceOfSatisfying(RevenueCatFailure.class,
                    e -> assertThat(e.kind()).isEqualTo(RevenueCatFailure.Kind.REVIEW));
        }
    }
    @Test void mismatchedConfigurationAndResourceAreRejected() {
        assertThatThrownBy(() -> new RevenueCatReadClient("other", "SANDBOX", "fake", (u,b) -> null, decoder, CLOCK)).isInstanceOf(RevenueCatFailure.class);
        var c = client((u,b) -> response(200, Map.of("Content-Type", List.of("application/json")), bytes(purchase())));
        assertThatThrownBy(() -> c.purchase(REF, "other")).isInstanceOf(RevenueCatFailure.class);
    }
    @Test void bodySubscriberCancelsOversizeWithoutKeepingRawError() {
        var body = new RevenueCatHttpTransport.BoundedBody(); AtomicBoolean canceled = new AtomicBoolean();
        body.onSubscribe(new Flow.Subscription() { public void request(long n) {} public void cancel() { canceled.set(true); } });
        body.onNext(List.of(ByteBuffer.wrap(new byte[RevenueCatPurchaseDecoder.MAX_BYTES + 1])));
        assertThat(canceled).isTrue(); assertThatThrownBy(() -> body.getBody().toCompletableFuture().join()).hasCauseInstanceOf(RevenueCatFailure.class);
    }
    @Test void transportRejectsOtherHostWithoutNetwork() {
        try (var transport = new RevenueCatHttpTransport()) {
            assertThatThrownBy(() -> transport.get(java.net.URI.create("https://evil.invalid/v2/projects/project"), "fake")).isInstanceOf(RevenueCatFailure.class);
        }
    }
    @Test void encryptedReferenceHasRandomNonceAndBoundContext() {
        var cipher = new PurchaseReferenceCipher("fake-key", new byte[32]);
        var context = new PurchaseReferenceCipher.Context("SANDBOX", "project", "app", "a".repeat(64));
        var a = cipher.encrypt(context, "purc-secret"); var b = cipher.encrypt(context, "purc-secret");
        assertThat(a.nonce()).isNotEqualTo(b.nonce()); assertThat(cipher.decrypt(context, a)).isEqualTo("purc-secret");
        assertThat(a.toString()).doesNotContain("purc-secret", a.ciphertext());
        var other = new PurchaseReferenceCipher.Context("PRODUCTION", "project", "app", "a".repeat(64));
        assertThatThrownBy(() -> cipher.decrypt(other, a)).isInstanceOf(RevenueCatFailure.class);
        byte[] tampered = Base64.getDecoder().decode(a.ciphertext()); tampered[0] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(context, new PurchaseReferenceCipher.Envelope(1, a.keyId(), a.nonce(), Base64.getEncoder().encodeToString(tampered))))
                .isInstanceOf(RevenueCatFailure.class);
    }
}
