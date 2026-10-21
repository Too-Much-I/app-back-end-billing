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

@WebMvcTest(controllers = InviteRewardController.class, properties = {
        "app.rewards.invite-share.enabled=true", "billing.payment.environment=SANDBOX",
        "billing.entitlement-query.public-connector-enabled=true", "billing.entitlement-query.issuer=https://identity.test",
        "billing.entitlement-query.jwks-uri=https://identity.test/jwks"
})
@Import({PublicSecurityConfig.class, SecurityConfig.class, PublicApiWriter.class, TraceCorrelation.class, InviteRewardControllerTest.Beans.class})
class InviteRewardControllerTest {
    @Autowired MockMvc mvc;
    @Autowired InviteRewardProperties properties;
    @MockitoBean InviteRewardService rewards;
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
        when(identityUserJwtDecoder.decode("guest")).thenReturn(token("GUEST", "billing:read billing:purchase"));
        when(rewards.read(USER)).thenReturn(new InviteRewardService.Quota(1, 3, 2));
        when(rewards.award(any(), eq(KEY))).thenReturn(new InviteRewardService.Award(KEY, 1, 2, 3, 1));
    }
    static Jwt token(String type, String scope) {
        return Jwt.withTokenValue("fake").header("alg", "RS256").issuer("https://identity.test")
                .audience(List.of("tosunsaeng-billing")).subject(USER).claim("account_type", type).claim("scope", scope)
                .issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(60)).build();
    }
    MockHttpServletRequestBuilder read() { return get(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer reader"); }
    MockHttpServletRequestBuilder write() { return post(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer writer").header("Idempotency-Key", KEY); }
    @Test void exactEnvelopeAndAuthenticatedSubject() throws Exception {
        mvc.perform(read()).andExpect(status().isOk()).andExpect(jsonPath("isSuccess").value(true))
                .andExpect(jsonPath("result.awardedCount").value(1)).andExpect(jsonPath("result.remainingAwards").value(2))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().exists("X-Trace-Id"));
        mvc.perform(write().header("X-User-Id", "forged")).andExpect(status().isOk()).andExpect(jsonPath("result.operationId").value(KEY))
                .andExpect(jsonPath("result.awardedQuantity").value(1)).andExpect(jsonPath("result.remainingAwards").value(1));
        var captor = org.mockito.ArgumentCaptor.forClass(VerifiedMemberEvidence.class);
        verify(rewards).award(captor.capture(), eq(KEY));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo(USER);
        verify(limiter).acquire(PaymentRateLimiter.Route.INVITE_QUERY, USER);
        verify(limiter).acquire(PaymentRateLimiter.Route.INVITE_AWARD, USER);
    }
    @Test void methodSpecificScopeAndGuestDenial() throws Exception {
        mvc.perform(post(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer reader"))
                .andExpect(status().isForbidden()).andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"billing:purchase\""));
        mvc.perform(get(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer writer"))
                .andExpect(status().isForbidden()).andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"billing:read\""));
        for (String method : List.of("GET", "POST")) mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), PublicIngressFilter.INVITE)
                        .with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer guest"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("WWW-Authenticate"));
        verifyNoInteractions(rewards, limiter);
    }
    @Test void authenticationAndKeysUnavailable() throws Exception {
        mvc.perform(get(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        when(identityUserJwtDecoder.decode("reader")).thenThrow(new IdentityUserJwtDecoder.KeysUnavailable());
        mvc.perform(read()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("BILLING_TEMPORARILY_UNAVAILABLE"))
                .andExpect(header().string("Retry-After", "5"));
        verifyNoInteractions(rewards);
    }
    @Test void strictBodyQueryAndSingleUuidKey() throws Exception {
        for (String body : List.of("{}", " ", "null", "x".repeat(17000))) {
            mvc.perform(write().content(body)).andExpect(status().isBadRequest());
            mvc.perform(read().content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(write().with(r -> { r.setQueryString(""); return r; })).andExpect(status().isBadRequest());
        mvc.perform(read().queryParam("userId", USER)).andExpect(status().isBadRequest());
        for (String key : List.of("", "invalid", KEY + "," + KEY, "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"))
            mvc.perform(post(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer writer")
                    .header("Idempotency-Key", key)).andExpect(status().isBadRequest());
        mvc.perform(write().header("Idempotency-Key", KEY)).andExpect(status().isBadRequest());
        mvc.perform(post(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer writer"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rewards, limiter);
    }
    @Test void flagConnectorAndMethods() throws Exception {
        mvc.perform(delete(PublicIngressFilter.INVITE).with(r -> { r.setLocalPort(8083); return r; }))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET, POST"));
        mvc.perform(write().with(r -> { r.setLocalPort(8082); return r; }).header("X-Forwarded-Port", "8083")).andExpect(status().isNotFound());
        properties.setEnabled(false);
        mvc.perform(read()).andExpect(status().isNotFound()); mvc.perform(write()).andExpect(status().isNotFound());
        verifyNoInteractions(rewards, limiter);
    }
    @Test void exactBusinessErrorsAndNoScopeChallengeForWithdrawal() throws Exception {
        doThrow(new PaymentException(403, "PAYMENT_ACCOUNT_WITHDRAWN")).when(rewards).award(any(), any());
        mvc.perform(write()).andExpect(status().isForbidden()).andExpect(jsonPath("message").value("탈퇴한 계정은 초대 보상을 받을 수 없습니다."))
                .andExpect(header().doesNotExist("WWW-Authenticate")).andExpect(jsonPath("result").isEmpty());
        doThrow(new PaymentException(409, "INVITE_REWARD_LIMIT_REACHED")).when(rewards).award(any(), any());
        mvc.perform(write()).andExpect(status().isConflict()).andExpect(jsonPath("message").value("초대 보상은 계정당 최대 3회까지 받을 수 있습니다."));
        doThrow(new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED")).when(rewards).award(any(), any());
        mvc.perform(write()).andExpect(status().isConflict()).andExpect(jsonPath("message").value("구매 계정 연결을 먼저 완료해 주세요."));
    }
    @Test void rateLimitAndStorageErrors() throws Exception {
        when(limiter.acquire(PaymentRateLimiter.Route.INVITE_AWARD, USER)).thenReturn(12L);
        mvc.perform(write()).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "12"));
        verifyNoInteractions(rewards);
        when(rewards.read(USER)).thenThrow(new IllegalStateException("do-not-leak"));
        mvc.perform(read()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("message").value("초대 보상 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(header().string("Retry-After", "5"));
    }
}
