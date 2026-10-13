package web.tosunsaeng.billing.domain.payment.sync;

import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import web.tosunsaeng.billing.domain.payment.api.PaymentSyncController;
import web.tosunsaeng.billing.domain.payment.application.PaymentRateLimiter;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.global.config.security.*;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentSyncController.class, properties = {
        "billing.entitlement-query.public-connector-enabled=true", "billing.entitlement-query.issuer=https://identity.test",
        "billing.entitlement-query.jwks-uri=https://identity.test/jwks"})
@Import({PublicSecurityConfig.class, SecurityConfig.class, PublicApiWriter.class, TraceCorrelation.class, PaymentSyncDecoder.class, PaymentSyncControllerTest.Beans.class})
class PaymentSyncControllerTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentProperties properties;
    @MockitoBean PaymentSyncService service;
    @MockitoBean PaymentRateLimiter limiter;
    @MockitoBean JwtDecoder identityUserJwtDecoder;
    static final String USER = "11111111-1111-1111-8111-111111111111";
    static final String OP = "22222222-2222-4222-8222-222222222222";
    @TestConfiguration static class Beans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean io.micrometer.core.instrument.MeterRegistry metrics() { return new io.micrometer.core.instrument.simple.SimpleMeterRegistry(); }
    }
    @BeforeEach void setup() {
        // Test-only after-startup flag change; production configuration refuses ON without a real worker.
        properties.setSyncEnabled(true);
        when(identityUserJwtDecoder.decode("member")).thenReturn(token("MEMBER", "billing:purchase"));
        when(identityUserJwtDecoder.decode("guest")).thenReturn(token("GUEST", "billing:purchase"));
        when(identityUserJwtDecoder.decode("read")).thenReturn(token("MEMBER", "billing:read"));
        when(service.sync(eq(USER), any(), any())).thenReturn(PaymentSyncResult.pending(OP, false));
    }
    MockHttpServletRequestBuilder request() {
        return post(PublicIngressFilter.SYNC).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer member")
                .header("Idempotency-Key", OP).contentType("application/json").content("{\"store\":\"APP_STORE\",\"transactionId\":\"fake-transaction\"}");
    }
    @Test void durablePendingAndAlreadyAppliedHaveExactStatusAndNoStore() throws Exception {
        mvc.perform(request().header("X-User-Id", "forged")).andExpect(status().isAccepted()).andExpect(jsonPath("result.status").value("PENDING"))
                .andExpect(jsonPath("result.purchaseId").isEmpty()).andExpect(header().string("Retry-After", "5")).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).sync(eq(USER), any(), argThat(r -> OP.equals(r.operationId())));
        when(service.sync(eq(USER), any(), any())).thenReturn(new PaymentSyncResult(OP, PaymentSyncResult.Status.APPLIED, OP, "VERIFIED", null));
        mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("result.status").value("APPLIED")).andExpect(header().doesNotExist("Retry-After"));
    }
    @Test void authGuestAndWrongScopeNeverCallBusiness() throws Exception {
        mvc.perform(request().with(r -> { r.removeHeader("Authorization"); return r; })).andExpect(status().isUnauthorized());
        for (String token : new String[]{"guest", "read"}) mvc.perform(request().with(r -> { r.removeHeader("Authorization"); r.addHeader("Authorization", "Bearer " + token); return r; }))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void offWrongPortAndWrongMethod() throws Exception {
        mvc.perform(request().with(r -> { r.setLocalPort(8082); return r; }).header("X-Forwarded-Port", "8083")).andExpect(status().isNotFound());
        mvc.perform(get(PublicIngressFilter.SYNC).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"));
        properties.setSyncEnabled(false); mvc.perform(request()).andExpect(status().isNotFound()); verifyNoInteractions(service);
    }
    @Test void rejectsOversizeTypeQueryAndDuplicateKey() throws Exception {
        mvc.perform(request().content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
        mvc.perform(request().contentType("text/plain")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(request().queryParam("userId", "fake")).andExpect(status().isBadRequest());
        mvc.perform(request().header("Idempotency-Key", OP)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rateLimitRemainingSecondsAndStorageFailureAreNotPending() throws Exception {
        when(limiter.acquire(PaymentRateLimiter.Route.SYNC, USER)).thenReturn(27L);
        mvc.perform(request()).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "27"));
        verifyNoInteractions(service);
        when(limiter.acquire(PaymentRateLimiter.Route.SYNC, USER)).thenReturn(0L);
        when(service.sync(eq(USER), any(), any())).thenThrow(PaymentException.unavailable());
        mvc.perform(request()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("result").isEmpty());
    }
    @Test void withdrawnAndMissingAccountAreExplicitErrors() throws Exception {
        when(service.sync(eq(USER), any(), any())).thenThrow(new PaymentException(403, "PAYMENT_ACCOUNT_WITHDRAWN"));
        mvc.perform(request()).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PAYMENT_ACCOUNT_WITHDRAWN"));
        when(service.sync(eq(USER), any(), any())).thenThrow(new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED"));
        mvc.perform(request()).andExpect(status().isConflict()).andExpect(jsonPath("code").value("PURCHASE_ACCOUNT_REQUIRED"));
    }
    static Jwt token(String type, String scope) { return Jwt.withTokenValue("fake").header("alg", "RS256").subject(USER).claim("account_type", type)
            .claim("scope", scope).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(); }
}
