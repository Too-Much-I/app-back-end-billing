package web.tosunsaeng.billing.domain.payment.provider;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.global.config.security.RevenueCatWebhookConfiguration;
import static org.assertj.core.api.Assertions.*;

class RevenueCatWebhookTest {
    @Test void productionMixedIngressKeepsActualEnvironmentAndDoesNotAcceptUnknownApps() {
        var mixed = new RevenueCatWebhookDecoder("PRODUCTION", "fake-project",
                Map.of("fake-app", new RevenueCatWebhookDecoder.App(PurchaseObservation.Store.PLAY_STORE, Set.of("PREMIUM_1D"))), lookup, CLOCK, true);
        var sandbox = mixed.decode(body(event()));
        var production = mixed.decode(body(event().put("environment", "PRODUCTION")));
        assertThat(sandbox.environment()).isEqualTo("SANDBOX");
        assertThat(sandbox.observation().environment()).isEqualTo("SANDBOX");
        assertThat(sandbox.eventKey()).isNotEqualTo(production.eventKey());
        assertThat(sandbox.observation().transactionKey()).isNotEqualTo(production.observation().transactionKey());
        assertThat(mixed.decode(body(event().put("type", "CANCELLATION"))).environment()).isEqualTo("SANDBOX");
        assertThatThrownBy(() -> mixed.decode(body(event().put("app_id", "unknown")))).isInstanceOf(PaymentException.class);
        var strict = new RevenueCatWebhookDecoder("PRODUCTION", "fake-project",
                Map.of("fake-app", new RevenueCatWebhookDecoder.App(PurchaseObservation.Store.PLAY_STORE, Set.of("PREMIUM_1D"))), lookup, CLOCK);
        assertThatThrownBy(() -> strict.decode(body(event()))).isInstanceOf(PaymentException.class);
    }
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String AUTH = "Bearer fake-webhook-authorization", SECRET = "fake-webhook-signing-secret-32-bytes";
    static final String REF = "11111111-1111-4111-8111-111111111111";
    static final ObjectMapper JSON = new ObjectMapper();
    final PurchaseLookupKey lookup = new PurchaseLookupKey(new byte[32], "fake-v1");
    final RevenueCatWebhookAuthentication auth = new RevenueCatWebhookAuthentication(AUTH, SECRET, CLOCK);
    final RevenueCatWebhookDecoder decoder = new RevenueCatWebhookDecoder("SANDBOX", "fake-project",
            Map.of("fake-app", new RevenueCatWebhookDecoder.App(PurchaseObservation.Store.PLAY_STORE, Set.of("PREMIUM_1D"))), lookup, CLOCK);
    ObjectNode event() {
        return JSON.createObjectNode().put("id", "fake-event").put("type", "NON_RENEWING_PURCHASE")
                .put("environment", "SANDBOX").put("app_id", "fake-app").put("store", "PLAY_STORE")
                .put("transaction_id", "fake-order").put("product_id", "PREMIUM_1D").put("quantity", 1).put("is_family_share", false)
                .put("app_user_id", REF).put("original_app_user_id", REF).put("purchased_at_ms", NOW.minusSeconds(60).toEpochMilli())
                .put("event_timestamp_ms", NOW.minusSeconds(20).toEpochMilli());
    }
    @Test void incompletePurchaseHasMatchingHintButNoGrantEvidence() {
        var e = event(); e.remove("quantity"); e.putArray("aliases").add("$RCAnonymousID:fake");
        var receipt = decoder.decode(body(e));
        assertThat(receipt.reason()).isEqualTo("OWNER_UNRESOLVED");
        assertThat(receipt.observation()).isNull();
        assertThat(receipt.reviewCandidate()).isNotNull();
        assertThat(receipt.reviewCandidate().transactionKey()).isEqualTo(lookup.transaction("SANDBOX", "PLAY_STORE", "fake-app", "fake-order"));
        assertThat(receipt.reviewCandidate().body().toJson()).doesNotContain("fake-order", "$RCAnonymousID");
    }
    @Test void conflictingOwnerReversalAndUnsupportedQuantityNeverGetResolutionHints() {
        var owner = event().put("original_app_user_id", "22222222-2222-4222-8222-222222222222");
        assertThat(decoder.decode(body(owner)).reviewCandidate()).isNull();
        assertThat(decoder.decode(body(event().put("type", "CANCELLATION"))).reviewCandidate()).isNull();
        var quantity = event().put("quantity", 2); quantity.putArray("aliases").add("other");
        assertThat(decoder.decode(body(quantity)).reviewCandidate()).isNull();
        var family = event().put("is_family_share", true); family.remove("quantity");
        assertThat(decoder.decode(body(family)).reviewCandidate()).isNull();
    }
    byte[] body(ObjectNode e) { return JSON.createObjectNode().put("api_version", "1.0").set("event", e).toString().getBytes(StandardCharsets.UTF_8); }
    static String sign(byte[] body, long time) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((time + ".").getBytes(StandardCharsets.US_ASCII)); return "t=" + time + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
    @ParameterizedTest @ValueSource(ints = {-300, -1, 0, 1, 300}) void timeBoundaryAndResignedDeliveryAccepted(int seconds) throws Exception {
        byte[] body = body(event()); auth.authorize(List.of(AUTH)); auth.verify(List.of(sign(body, NOW.plusSeconds(seconds).getEpochSecond())), body);
    }
    @ParameterizedTest @ValueSource(ints = {-301, 301, Integer.MIN_VALUE, Integer.MAX_VALUE}) void staleOrFutureSignatureRejected(int seconds) throws Exception {
        byte[] body = body(event()); String signature = sign(body, NOW.plusSeconds(seconds).getEpochSecond());
        assertThatThrownBy(() -> auth.verify(List.of(signature), body)).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(401));
    }
    @Test void authAndSignatureAreBothRequiredAndRawBytesMatter() throws Exception {
        byte[] body = body(event()); String signature = sign(body, NOW.getEpochSecond());
        for (List<String> headers : List.of(List.<String>of(), List.of(AUTH, AUTH), List.of("Bearer user-jwt"), List.of(AUTH + " ")))
            assertThatThrownBy(() -> auth.authorize(headers)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> auth.verify(List.of(signature, signature), body)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> auth.verify(List.of(signature), (new String(body, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8))).isInstanceOf(PaymentException.class);
        assertThat(auth.toString()).doesNotContain(AUTH, SECRET);
    }
    @ParameterizedTest @ValueSource(strings = {"t=-1,v1=", "t=01,v1=", "t=9223372036854775807,v1=", "t=0,v1=", "t=0, v1=", "v1=", "t=0,t=0,v1="})
    void malformedSignatureNeverLeaksParserError(String prefix) {
        assertThatThrownBy(() -> auth.verify(List.of(prefix + "a".repeat(64)), new byte[0]))
                .isInstanceOfSatisfying(PaymentException.class, e -> { assertThat(e.status()).isEqualTo(401); assertThat(e.getCause()).isNull(); });
    }
    @Test void normalPurchaseUsesSameTransactionKeyAndDoesNotInferUsdMoney() {
        ObjectNode e = event().put("price", 100); var result = decoder.decode(body(e));
        assertThat(result.disposition()).isEqualTo(WebhookReceipt.Disposition.PURCHASE); assertThat(result.observation().money()).isNull();
        assertThat(result.observation().transactionKey()).isEqualTo(lookup.transaction("SANDBOX", "PLAY_STORE", "fake-app", "fake-order"));
        assertThat(decoder.decode(body(event().put("currency", "KRW").putNull("price_in_purchased_currency"))).observation().money()).isNull();
        e.put("currency", "KRW").put("price_in_purchased_currency", 9000);
        assertThat(decoder.decode(body(e)).observation().money().amount()).isEqualByComparingTo("9000");
        assertThat(result.toString()).doesNotContain(REF, "fake-order");
    }
    @Test void digestIgnoresUnknownAndWhitespaceButNotKnownChangesOrNull() throws Exception {
        ObjectNode e = event(); var a = decoder.decode(body(e));
        e.putObject("subscriber_attributes").put("fake-private", "not-retained"); e.put("unknown", true);
        assertThat(decoder.decode(body(e)).receptionDigest()).isEqualTo(a.receptionDigest());
        byte[] pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(JSON.readTree(body(e)));
        assertThat(decoder.decode(pretty).receptionDigest()).isEqualTo(a.receptionDigest());
        e.putNull("quantity"); assertThat(decoder.decode(body(e)).receptionDigest()).isNotEqualTo(a.receptionDigest());
        String explicitNull = decoder.decode(body(e)).receptionDigest(); e.remove("quantity");
        assertThat(decoder.decode(body(e)).receptionDigest()).isNotEqualTo(explicitNull);
    }
    @ParameterizedTest @ValueSource(strings = {"CANCELLATION", "TRANSFER", "SUBSCRIBER_ALIAS", "REFUND_REVERSED", "TEMPORARY_ENTITLEMENT_GRANT", "INITIAL_PURCHASE", "RENEWAL", "EXPIRATION"})
    void nonPurchaseFinancialEventsRequireReview(String type) {
        var r = decoder.decode(body(event().put("type", type))); assertThat(r.disposition()).isEqualTo(WebhookReceipt.Disposition.REVIEW); assertThat(r.observation()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"TEST", "NEW_UNKNOWN_TYPE"}) void unknownAndTestAllowMinimalEnvelopeWithoutGrant(String type) {
        var r = decoder.decode(body(JSON.createObjectNode().put("id", "fake-event").put("type", type)));
        assertThat(r.disposition()).isEqualTo(WebhookReceipt.Disposition.IGNORED); assertThat(r.observation()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"app_user_id", "original_app_user_id", "quantity", "is_family_share", "purchased_at_ms", "event_timestamp_ms", "transaction_id", "environment", "app_id"})
    void missingEvidenceNeverGrants(String field) {
        var e = event(); e.remove(field); assertThat(decoder.decode(body(e)).disposition()).isEqualTo(WebhookReceipt.Disposition.REVIEW);
    }
    @Test void missingQuantityRequiresLookupButExplicitMultipleQuantityRemainsUnsupported() {
        var missing = event(); missing.remove("quantity");
        var receipt = decoder.decode(body(missing));
        assertThat(receipt.reason()).isEqualTo("QUANTITY_REQUIRES_LOOKUP");
        assertThat(receipt.observation()).isNull();
        assertThat(receipt.disposition()).isEqualTo(WebhookReceipt.Disposition.REVIEW);
        assertThat(decoder.decode(body(event().putNull("quantity"))).reason()).isEqualTo("QUANTITY_REQUIRES_LOOKUP");
        assertThat(decoder.decode(body(event().put("quantity", 2))).reason()).isEqualTo("QUANTITY_UNSUPPORTED");
    }
    @Test void aliasesFamilyUnknownProductAndMoneyDoNotGrant() {
        List<ObjectNode> bad = List.of(event().put("original_app_user_id", "other"), event().put("quantity", 2), event().put("is_family_share", true),
                event().put("product_id", "foreign"), event().put("price_in_purchased_currency", 9000), event().put("price_in_purchased_currency", "9000"));
        for (var e : bad) assertThat(decoder.decode(body(e)).observation()).isNull();
        var e = event(); e.putArray("aliases").add("different"); assertThat(decoder.decode(body(e)).observation()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"{\"api_version\":\"1.0\",\"event\":{\"id\":\"x\",\"id\":\"y\",\"type\":\"TEST\"}}", "{} {}", "[]", "\uFEFF{}"})
    void strictJsonRejectsDuplicateTrailingAndBom(String json) {
        assertThatThrownBy(() -> decoder.decode(json.getBytes(StandardCharsets.UTF_8))).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(400));
    }
    @Test void numericCoercionInvalidUtf8AndWrongEnvironmentRejected() {
        assertThatThrownBy(() -> decoder.decode(body(event().put("quantity", "1")))).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> decoder.decode(new byte[]{(byte) 0xc3, 0x28})).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> decoder.decode(body(event().put("environment", "PRODUCTION")))).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(422));
    }
    MockHttpServletRequest request(byte[] body) throws Exception {
        var q = new MockHttpServletRequest("POST", RevenueCatWebhookFilter.PATH); q.setLocalPort(8083); q.addHeader("Host", "billing.example.test");
        q.addHeader("Authorization", AUTH); q.setContentType("application/json"); q.setContent(body);
        q.addHeader("X-RevenueCat-Webhook-Signature", sign(body, NOW.getEpochSecond())); return q;
    }
    @Test void httpSuccessOnlyAfterReceiverReturnsAndNeverInvokesUserController() throws Exception {
        var received = new AtomicInteger(); var filter = new RevenueCatWebhookFilter(auth, decoder, r -> received.incrementAndGet());
        var response = new MockHttpServletResponse(); filter.doFilter(request(body(event())), response, (q, r) -> { throw new AssertionError("forwarded to user route"); });
        assertThat(response.getStatus()).isEqualTo(200); assertThat(response.getContentAsByteArray()).isEmpty(); assertThat(received.get()).isEqualTo(1);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        var failing = new RevenueCatWebhookFilter(auth, decoder, r -> { throw PaymentException.unavailable(); });
        response = new MockHttpServletResponse(); failing.doFilter(request(body(event())), response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(503);
    }
    @Test void headerAndBodyFailuresNeverCallReceiver() throws Exception {
        var received = new AtomicInteger(); var filter = new RevenueCatWebhookFilter(auth, decoder, r -> received.incrementAndGet());
        for (String type : List.of("text/plain", "application/json;charset=UTF-16")) {
            var q = request(body(event())); q.setContentType(type); var r = new MockHttpServletResponse(); filter.doFilter(q, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(400);
        }
        var huge = request(new byte[RevenueCatWebhookDecoder.MAX_BYTES + 1]); var r = new MockHttpServletResponse(); filter.doFilter(huge, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(413);
        var unsigned = request(body(event())); unsigned.removeHeader("X-RevenueCat-Webhook-Signature"); r = new MockHttpServletResponse(); filter.doFilter(unsigned, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(401);
        var compressed = request(body(event())); compressed.addHeader("Content-Encoding", "gzip"); r = new MockHttpServletResponse(); filter.doFilter(compressed, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(400);
        assertThat(received.get()).isZero();
    }
    @Test void guardOffWrongPortHostMethodQueryCannotBypass() throws Exception {
        var p = new RevenueCatWebhookProperties(); p.setHost("billing.example.test");
        var pub = new EntitlementQueryProperties(); pub.setPublicConnectorEnabled(true);
        var guard = new RevenueCatWebhookConfiguration.Guard(p, pub);
        var r = new MockHttpServletResponse(); guard.doFilter(request(body(event())), r, (q, s) -> { throw new AssertionError(); }); assertThat(r.getStatus()).isEqualTo(404);
        p.setIngressEnabled(true);
        for (int port : List.of(8082, 8443)) {
            var q = request(body(event())); q.setLocalPort(port); q.addHeader("X-Forwarded-Port", "8083");
            r = new MockHttpServletResponse(); guard.doFilter(q, r, (a, b) -> { throw new AssertionError(); }); assertThat(r.getStatus()).isEqualTo(404);
        }
        var q = request(body(event())); q.removeHeader("Host"); q.addHeader("Host", "other.test"); q.addHeader("X-Forwarded-Host", p.getHost());
        r = new MockHttpServletResponse(); guard.doFilter(q, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(404);
        q = request(body(event())); q.setMethod("GET"); r = new MockHttpServletResponse(); guard.doFilter(q, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(405);
        q = request(body(event())); q.setQueryString(""); r = new MockHttpServletResponse(); guard.doFilter(q, r, new MockFilterChain()); assertThat(r.getStatus()).isEqualTo(400);
        var called = new AtomicInteger(); guard.doFilter(request(body(event())), new MockHttpServletResponse(), (a, b) -> called.incrementAndGet()); assertThat(called.get()).isEqualTo(1);
    }
}
