package web.tosunsaeng.billing.global.config.security;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import web.tosunsaeng.billing.domain.examdeletion.*;
import web.tosunsaeng.billing.domain.partlearning.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;

class PartLearningWorkloadTest {
    WorkloadJwtProperties p = new WorkloadJwtProperties();
    PartLearningProperties deletion = new PartLearningProperties();
    @BeforeEach void setup() {
        p.setPort(8444); p.setEnvironment("test"); p.setLearningCoreReceiverEnabled(true);
        p.setLearningCorePublicJwkSet(publicSet(KEY)); deletion.setEnabled(true);
    }
    String lcClaims() { return claims().replace(ISSUER, p.learningCoreIssuer()).replace("identity-service", "learning-core-service")
            .replace(AUD, "tosunsaeng-billing:part-learning-eligibility:test"); }
    MockHttpServletRequest request() {
        var r = new MockHttpServletRequest("POST", PartLearningController.ROUTE); r.setLocalPort(8444);
        r.addHeader("Authorization", "Bearer " + sign(lcClaims())); return r;
    }
    int run(MockHttpServletRequest r) throws Exception {
        var response = new MockHttpServletResponse();
        var attempts = new AttemptGroupEventProperties(); attempts.setEnabled(true);
        new WorkloadIngressFilter(p, new PaymentProperties(), new OwnerRebindProperties(), attempts, new DeletionProperties(), new web.tosunsaeng.billing.domain.reanswer.ReanswerProperties(), deletion)
                .doFilter(r, response, (q,s) -> new WorkloadAuthenticationFilter(p, new WorkloadJwtVerifierRegistry(p, CLOCK))
                        .doFilter(q, s, (rq,rs) -> ((MockHttpServletResponse) rs).setStatus(204)));
        if (response.getStatus() != 204) { assertThat(response.getContentAsByteArray()).isEmpty(); assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store"); }
        return response.getStatus();
    }
    @Test void exactPurposeAcceptedButDoesNotAuthorizeReservations() throws Exception {
        assertThat(run(request())).isEqualTo(204);
        var r = request(); r.setRequestURI("/internal/v1/reservations"); assertThat(run(r)).isEqualTo(403);
        r = request(); r.removeHeader("Authorization"); r.addHeader("Authorization", "Bearer " + sign(lcClaims().replace("part-learning-eligibility", "reservations")));
        assertThat(run(r)).isEqualTo(403);
    }
    @Test void disabledAndWrongPortWinBeforeAuthentication() throws Exception {
        deletion.setEnabled(false); var r = request(); r.removeHeader("Authorization"); assertThat(run(r)).isEqualTo(404);
        deletion.setEnabled(true);
        for (int port : new int[]{8082, 8083}) {
            r = request(); r.setLocalPort(port); r.addHeader("X-Forwarded-Port", "8444"); assertThat(run(r)).isEqualTo(404);
        }
        p.setLearningCoreReceiverEnabled(false); assertThat(run(request())).isEqualTo(404);
    }
    @Test void invalidSignatureAndQuery401ButVerifiedWrongSubjectAndMethod403() throws Exception {
        var r = request(); r.setQueryString(""); assertThat(run(r)).isEqualTo(401);
        r = request(); r.removeHeader("Authorization"); r.addHeader("Authorization", "Bearer " + sign(lcClaims(), key("wrong-key")));
        assertThat(run(r)).isEqualTo(401);
        r = request(); r.removeHeader("Authorization"); r.addHeader("Authorization", "Bearer " + sign(lcClaims().replace("learning-core-service", "wrong-service")));
        assertThat(run(r)).isEqualTo(403);
        r = request(); r.setMethod("GET"); assertThat(run(r)).isEqualTo(403);
        r = request(); r.removeHeader("Authorization"); r.addHeader("Authorization", "AWS4-HMAC-SHA256 fake");
        assertThat(run(r)).isEqualTo(401);
    }
}
