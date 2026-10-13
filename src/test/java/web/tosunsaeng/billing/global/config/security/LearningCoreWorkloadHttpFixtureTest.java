package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;
import static org.assertj.core.api.Assertions.*;

/** Exact frozen HTTP artifacts against production ingress/auth filters; MVC tests cover actual controllers/chain. */
class LearningCoreWorkloadHttpFixtureTest {
    static final String ROOT = "/contracts/lc-billing-workload-auth-r1/";
    static final ObjectMapper JSON = new ObjectMapper();
    static JsonNode fixture(String name) throws Exception {
        try (var in = LearningCoreWorkloadHttpFixtureTest.class.getResourceAsStream(ROOT + name)) { return JSON.readTree(in); }
    }
    static WorkloadJwtProperties properties(boolean identity, boolean lc) throws Exception {
        var p = new WorkloadJwtProperties(); p.setReceiverEnabled(identity); p.setLearningCoreReceiverEnabled(lc);
        p.setPort(8444); p.setEnvironment("test");
        var keys = fixture("public-jwks.json");
        p.setIdentityPublicJwkSet(identity ? keys.get("identity").toString() : "malformed-off-key");
        p.setLearningCorePublicJwkSet(lc ? keys.get("lc").toString() : "malformed-off-key");
        return p;
    }
    @TestFactory java.util.stream.Stream<DynamicTest> frozenHttpCases() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (var c : fixture("http-cases.json").get("cases")) tests.add(DynamicTest.dynamicTest(c.get("id").asText(), () -> {
            var p = properties(c.get("identityEnabled").asBoolean(), c.get("lcEnabled").asBoolean());
            if (c.get("expectedStage").asText().equals("NO_HTTP_RESPONSE")) {
                var factory = new TomcatServletWebServerFactory();
                new WorkloadSecurityConfiguration().workloadConnector(p, new PaymentProperties(), new EntitlementQueryProperties(),
                        new BillingMongoProperties(), new TrialEligibilityProperties(), new MockEnvironment()).customize(factory);
                assertThat(factory.getAdditionalTomcatConnectors()).isEmpty(); return;
            }
            var owners = new OwnerRebindProperties(); owners.setEnabled(true);
            var events = new AttemptGroupEventProperties(); events.setEnabled(true);
            var payments = new PaymentProperties(); payments.setWithdrawalEnabled(true);
            var request = new MockHttpServletRequest(c.get("method").asText(), c.get("path").asText());
            request.setLocalPort(c.get("localPort").asInt());
            if (!c.get("queryString").isNull()) request.setQueryString(c.get("queryString").asText());
            for (var value : c.get("authorization")) request.addHeader(c.get("authorizationHeaderName").asText(), value.asText());
            var response = new MockHttpServletResponse(); var invoked = new AtomicInteger();
            var verifier = new WorkloadJwtVerifierRegistry(p, Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
            new WorkloadIngressFilter(p, payments, owners, events).doFilter(request, response, (q, r) ->
                    new WorkloadAuthenticationFilter(p, verifier).doFilter(q, r, (rq, rs) -> {
                        invoked.incrementAndGet(); ((jakarta.servlet.http.HttpServletResponse) rs).setStatus(204);
                    }));
            int expected = c.get("expectedStatus").isNull() ? 204 : c.get("expectedStatus").asInt();
            assertThat(response.getStatus()).isEqualTo(expected);
            assertThat(invoked.get()).isEqualTo(expected == 204 ? 1 : 0);
            assertThat(response.getContentAsByteArray()).isEmpty();
            if (expected != 204) assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }));
        return tests.stream();
    }
    @Test void sameKidDifferentKeysNeverFallsBackAndOffKeysAreNotParsed() throws Exception {
        String lcToken = fixture("token-cases.json").get("cases").get(0).get("token").asText();
        var p = properties(true, false);
        var registry = new WorkloadJwtVerifierRegistry(p, Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> registry.authenticate(lcToken)).isInstanceOf(WorkloadJwtCodec.InvalidToken.class);
        p = properties(false, true); p.setLearningCorePublicJwkSet(fixture("public-jwks.json").get("identity").toString());
        var wrongKey = new WorkloadJwtVerifierRegistry(p, Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> wrongKey.authenticate(lcToken)).isInstanceOf(WorkloadJwtCodec.InvalidToken.class);
    }
    @Test void enabledKeyConfigurationFailsBeforeServingRequests() throws Exception {
        for (boolean identity : new boolean[]{false, true}) {
            var p = properties(identity, !identity);
            for (String invalid : List.of("", "malformed", "{\"keys\":[]}")) {
                if (identity) p.setIdentityPublicJwkSet(invalid); else p.setLearningCorePublicJwkSet(invalid);
                assertThatThrownBy(() -> new WorkloadJwtVerifierRegistry(p, Clock.systemUTC()))
                        .isInstanceOf(IllegalStateException.class).hasMessage("Invalid workload public key configuration").hasNoCause();
            }
        }
    }
    @Test void lifecyclePathsAreSingleExactSegmentsAndBusinessOffWinsOverAuthentication() throws Exception {
        var p = properties(false, true);
        var payments = new PaymentProperties(); var owners = new OwnerRebindProperties(); var events = new AttemptGroupEventProperties();
        for (String path : List.of("/internal/v1/attempt-group-events", "/internal/v1/reservations/continuations/phone")) {
            var req = new MockHttpServletRequest("POST", path); req.setLocalPort(8444); req.setQueryString("x=1");
            var response = new MockHttpServletResponse(); var called = new AtomicInteger();
            new WorkloadIngressFilter(p, payments, owners, events).doFilter(req, response, (q,r) -> called.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(404); assertThat(called).hasValue(0);
        }
        for (String path : List.of("/internal/v1/reservations/a/b/confirm", "/internal/v1/reservations/a%2Fb/confirm",
                "/internal/v1/reservations/a/confirm/", "/internal/v1/reservations/a;ignored/confirm"))
            assertThat(WorkloadIngressFilter.learningCorePurpose(path)).isNull();
    }
}
