package web.tosunsaeng.billing.global.config.security;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicInteger;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;

class WorkloadIngressTest {
    final WorkloadJwtProperties p = new WorkloadJwtProperties();
    final PaymentProperties payment = new PaymentProperties();
    final OwnerRebindProperties owner = new OwnerRebindProperties();
    @BeforeEach void setup() { p.setReceiverEnabled(true); p.setPort(8444); p.setEnvironment("test"); payment.setWithdrawalEnabled(true); owner.setEnabled(true); }
    MockHttpServletRequest request(String path, int port) { var r = new MockHttpServletRequest("POST", path); r.setLocalPort(port); return r; }
    @Test void exactFourPurposesAcceptedAndCrossPurposeDenied() throws Exception {
        for (var route : WorkloadIngressFilter.ROUTES.entrySet()) {
            var r = request(route.getKey(), 8444);
            r.addHeader("Authorization", "Bearer " + sign(claims().replace(AUD, "tosunsaeng-billing:" + route.getValue() + ":test")));
            assertThat(run(r)).isEqualTo(204);
            var wrong = request(route.getKey(), 8444); wrong.addHeader("Authorization", "Bearer " + sign(claims().replace(AUD, "wrong")));
            assertThat(run(wrong)).isEqualTo(403);
        }
    }
    @Test void duplicateAbsentMalformedOrUserTokenNeverReachesHandler() throws Exception {
        var r = request("/internal/v1/payments/accounts/withdrawal/events", 8444);
        assertThat(run(r)).isEqualTo(401);
        r.addHeader("Authorization", "Bearer " + sign(claims())); r.addHeader("Authorization", "Bearer " + sign(claims()));
        assertThat(run(r)).isEqualTo(401);
        r = request("/internal/v1/payments/accounts/withdrawal/events", 8444); r.addHeader("Authorization", "Bearer " + sign(claims().replace(ISSUER, "user-issuer")));
        assertThat(run(r)).isEqualTo(401);
    }
    @Test void wrongPortsOffLcRoutesAndForwardingCannotBypass() throws Exception {
        String path = "/internal/v1/payments/accounts/withdrawal/events";
        for (int port : new int[]{8082,8083,8444}) {
            var r = request(port == 8444 ? "/internal/v1/reservations" : path, port);
            r.addHeader("X-Forwarded-Port", "8444"); r.addHeader("Authorization", "Bearer " + sign(claims()));
            assertThat(run(r)).isEqualTo(404);
        }
        p.setReceiverEnabled(false); assertThat(run(request(path,8444))).isEqualTo(404);
    }
    @Test void disabledBusinessRouteAndWrongMethodAreSeparated() throws Exception {
        var r = request("/internal/v1/payments/accounts/withdrawal/events",8444); r.addHeader("Authorization", "Bearer " + sign(claims()));
        r.setMethod("GET"); assertThat(run(r)).isEqualTo(403);
        payment.setWithdrawalEnabled(false); assertThat(run(r)).isEqualTo(404);
    }
    @Test void offRejectsAllIdentityRoutesOnEveryPortRegardlessOfLegacyHeaders() throws Exception {
        p.setReceiverEnabled(false);
        for (int configuredPort : new int[]{0, 8444}) {
            p.setPort(configuredPort);
            for (String path : WorkloadIngressFilter.ROUTES.keySet()) {
                for (int port : new int[]{8082, 8083, 8444}) {
                    var r = request(path, port);
                    r.addHeader("Authorization", "AWS4-HMAC-SHA256 fake");
                    r.addHeader("X-Forwarded-Port", "8444");
                    assertThat(run(r)).isEqualTo(404);
                }
            }
        }
    }
    @Test void oldAuthenticationNeverAuthenticatesAndLcOffBlocksLegacyIngress() throws Exception {
        for (String path : WorkloadIngressFilter.ROUTES.keySet()) {
            var r = request(path, 8444);
            r.addHeader("Authorization", "AWS4-HMAC-SHA256 fake");
            assertThat(run(r)).isEqualTo(401);
        }
        for (boolean enabled : new boolean[]{true, false}) {
            p.setReceiverEnabled(enabled);
            var calls = new AtomicInteger();
            new WorkloadIngressFilter(p, payment, owner, new web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties()).doFilter(
                    request("/internal/v1/reservations", 8082), new MockHttpServletResponse(),
                    (q, s) -> calls.incrementAndGet());
            assertThat(calls).hasValue(0);
        }
    }
    WorkloadJwtVerifierRegistry identityVerifier() { p.setIdentityPublicJwkSet(publicSet(KEY)); return new WorkloadJwtVerifierRegistry(p, CLOCK); }
    int run(MockHttpServletRequest r) throws Exception {
        var response = new MockHttpServletResponse(); AtomicInteger called = new AtomicInteger();
        new WorkloadIngressFilter(p,payment,owner,new web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties()).doFilter(r,response,(rq,rs) -> {
            new WorkloadAuthenticationFilter(p,identityVerifier()).doFilter(rq,rs,(q,s) -> { called.incrementAndGet(); ((jakarta.servlet.http.HttpServletResponse)s).setStatus(204); });
        });
        assertThat(called.get()).isEqualTo(response.getStatus() == 204 ? 1 : 0); return response.getStatus();
    }
}
