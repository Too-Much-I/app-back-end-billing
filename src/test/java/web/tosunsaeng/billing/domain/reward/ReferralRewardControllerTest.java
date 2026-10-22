package web.tosunsaeng.billing.domain.reward;

import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.global.config.security.*;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReferralRewardController.class, properties = {
        "app.rewards.referral.enabled=true", "billing.payment.environment=SANDBOX",
        "billing.entitlement-query.public-connector-enabled=true", "billing.entitlement-query.issuer=https://identity.test",
        "billing.entitlement-query.jwks-uri=https://identity.test/jwks"
})
@Import({PublicSecurityConfig.class, SecurityConfig.class, PublicApiWriter.class, TraceCorrelation.class, ReferralRewardControllerTest.Beans.class})
class ReferralRewardControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ReferralRewardProperties properties;
    @MockitoBean ReferralRewardService rewards;
    @MockitoBean PaymentRateLimiter limiter;
    @MockitoBean JwtDecoder identityUserJwtDecoder;
    static final String USER = "11111111-1111-4111-8111-111111111111", KEY = "22222222-2222-4222-8222-222222222222";
    @TestConfiguration static class Beans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean io.micrometer.core.instrument.MeterRegistry metrics() { return new io.micrometer.core.instrument.simple.SimpleMeterRegistry(); }
    }
    @BeforeEach void setup() {
        properties.setEnabled(true);
        when(identityUserJwtDecoder.decode("reader")).thenReturn(token("MEMBER", "billing:read"));
        when(identityUserJwtDecoder.decode("writer")).thenReturn(token("MEMBER", "billing:purchase"));
        when(identityUserJwtDecoder.decode("guest")).thenReturn(token("GUEST", "billing:purchase"));
        when(rewards.issue(any())).thenReturn(new ReferralRewardService.Code("ABCDEFGHJK"));
        when(rewards.apply(any(), eq(KEY), eq("ABCDEFGHJK"))).thenReturn(new ReferralRewardService.Applied(KEY, true, 1, 1));
    }
    static Jwt token(String type, String scope) {
        return Jwt.withTokenValue("fake").header("alg", "RS256").issuer("https://identity.test")
                .audience(List.of("tosunsaeng-billing")).subject(USER).claim("account_type", type).claim("scope", scope)
                .issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(60)).build();
    }
    MockHttpServletRequestBuilder issue() { return request(PublicIngressFilter.REFERRAL_CODE, "writer"); }
    MockHttpServletRequestBuilder apply() { return request(PublicIngressFilter.REFERRALS, "writer").header("Idempotency-Key", KEY)
            .contentType("application/json").content("{\"code\":\"ABCDEFGHJK\"}"); }
    MockHttpServletRequestBuilder request(String path, String token) {
        return post(path).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer " + token);
    }
    @Test void exactEnvelopesAndNormalizedCodeUseAuthenticatedSubject() throws Exception {
        mvc.perform(issue()).andExpect(status().isOk()).andExpect(jsonPath("result.code").value("ABCDEFGHJK"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().exists("X-Trace-Id"));
        mvc.perform(apply().content("{\"code\":\"  abcdefghjk  \"}").header("X-User-Id", "forged"))
                .andExpect(status().isOk()).andExpect(jsonPath("result.applicationId").value(KEY))
                .andExpect(jsonPath("result.applied").value(true)).andExpect(jsonPath("result.applicantAwardedQuantity").value(1))
                .andExpect(jsonPath("result.referrerAwardedQuantity").value(1));
        var capture = org.mockito.ArgumentCaptor.forClass(VerifiedMemberEvidence.class);
        verify(rewards).apply(capture.capture(), eq(KEY), eq("ABCDEFGHJK"));
        org.assertj.core.api.Assertions.assertThat(capture.getValue().userId()).isEqualTo(USER);
        verify(limiter).acquire(PaymentRateLimiter.Route.REFERRAL_CODE, USER);
        verify(limiter).acquire(PaymentRateLimiter.Route.REFERRAL_APPLY, USER);
    }
    @Test void onlyTwoPostRoutesAndPublicConnector() throws Exception {
        for (String path : List.of(PublicIngressFilter.REFERRALS, PublicIngressFilter.REFERRAL_CODE))
            mvc.perform(get(path).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "POST"));
        mvc.perform(get(PublicIngressFilter.REFERRALS + "/me").with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isNotFound());
        mvc.perform(apply().with(r -> { r.setLocalPort(8082); return r; }).header("X-Forwarded-Port", "8083")).andExpect(status().isNotFound());
        properties.setEnabled(false);
        mvc.perform(issue()).andExpect(status().isNotFound()); mvc.perform(apply()).andExpect(status().isNotFound());
        verifyNoInteractions(rewards);
    }
    @Test void memberScopeAndAuthenticationRequired() throws Exception {
        for (String path : List.of(PublicIngressFilter.REFERRALS, PublicIngressFilter.REFERRAL_CODE)) {
            mvc.perform(request(path, "reader")).andExpect(status().isForbidden())
                    .andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"billing:purchase\""));
            mvc.perform(request(path, "guest")).andExpect(status().isForbidden());
            mvc.perform(post(path).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isUnauthorized());
        }
        when(identityUserJwtDecoder.decode("writer")).thenThrow(new IdentityUserJwtDecoder.KeysUnavailable());
        mvc.perform(issue()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("BILLING_TEMPORARILY_UNAVAILABLE"));
        verifyNoInteractions(rewards);
    }
    @Test void strictBodyAndQuery() throws Exception {
        for (String body : List.of("{}", "null", "[]", "{\"code\":null}", "{\"code\":42}",
                "{\"code\":\"ABCDEFGHJK\",\"code\":\"ABCDEFGHJK\"}", "{\"code\":\"ABCDEFGHJK\",\"userId\":\"x\"}",
                "{\"code\":\"ABCDEFGHJK\"} {}", "{\"code\":\"ＡBCDEFGHJK\"}", "{\"code\":\"ABCD EFGHJK\"}",
                "{\"code\":\"ABCDEFGHIJ\"}", "{\"code\":\"abc\"}"))
            mvc.perform(apply().content(body)).andExpect(status().isBadRequest());
        for (String body : List.of("{}", " ", "null")) mvc.perform(issue().content(body)).andExpect(status().isBadRequest());
        mvc.perform(apply().queryParam("userId", USER)).andExpect(status().isBadRequest());
        mvc.perform(issue().with(r -> { r.setQueryString(""); return r; })).andExpect(status().isBadRequest());
        mvc.perform(apply().content(new byte[]{(byte) 0xff})).andExpect(status().isBadRequest());
        mvc.perform(apply().content("x".repeat(1025))).andExpect(status().isPayloadTooLarge());
        mvc.perform(apply().contentType("text/plain")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(apply().contentType("application/json;charset=UTF-16")).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(rewards);
    }
    @Test void exactSingleUuidKey() throws Exception {
        for (String key : List.of("", "bad", KEY + "," + KEY, "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"))
            mvc.perform(request(PublicIngressFilter.REFERRALS, "writer").header("Idempotency-Key", key)
                    .contentType("application/json").content("{\"code\":\"ABCDEFGHJK\"}")).andExpect(status().isBadRequest());
        mvc.perform(apply().header("Idempotency-Key", KEY)).andExpect(status().isBadRequest());
        mvc.perform(request(PublicIngressFilter.REFERRALS, "writer")).andExpect(status().isBadRequest());
        verifyNoInteractions(rewards);
    }
    @Test void businessErrorsRetryAndNoExceptionLeak() throws Exception {
        for (String code : List.of("SELF_REFERRAL_NOT_ALLOWED", "REFERRAL_ALREADY_APPLIED", "IDEMPOTENCY_KEY_CONFLICT", "PURCHASE_ACCOUNT_REQUIRED")) {
            doThrow(new PaymentException(409, code)).when(rewards).apply(any(), any(), any());
            mvc.perform(apply()).andExpect(status().isConflict()).andExpect(jsonPath("code").value(code)).andExpect(jsonPath("result").isEmpty());
        }
        doThrow(new PaymentException(422, "REFERRAL_CODE_UNAVAILABLE")).when(rewards).apply(any(), any(), any());
        mvc.perform(apply()).andExpect(status().isUnprocessableEntity());
        when(rewards.issue(any())).thenThrow(new PaymentException(403, "PAYMENT_ACCOUNT_WITHDRAWN"));
        mvc.perform(issue()).andExpect(status().isForbidden()).andExpect(header().doesNotExist("WWW-Authenticate"));
        doThrow(new IllegalStateException("SECRET_MUST_NOT_LEAK")).when(rewards).apply(any(), any(), any());
        mvc.perform(apply()).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
        when(limiter.acquire(PaymentRateLimiter.Route.REFERRAL_APPLY, USER)).thenReturn(12L);
        mvc.perform(apply()).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "12"));
    }
}
