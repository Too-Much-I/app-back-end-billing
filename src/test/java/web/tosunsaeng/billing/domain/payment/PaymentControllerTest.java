package web.tosunsaeng.billing.domain.payment;

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
import web.tosunsaeng.billing.domain.payment.api.PaymentController;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.dto.response.*;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.global.config.security.*;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentController.class, properties = {
        "billing.payment.catalog-enabled=true", "billing.payment.purchase-account-enabled=true",
        "billing.payment.environment=SANDBOX", "billing.payment.limiter-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "billing.entitlement-query.enabled=false", "billing.entitlement-query.public-connector-enabled=true",
        "billing.entitlement-query.issuer=https://identity.test", "billing.entitlement-query.jwks-uri=https://identity.test/jwks",
        "billing.mongodb.require-transactions=true", "billing.mongodb.initialize-indexes=true"
})
@Import({PublicSecurityConfig.class, SecurityConfig.class, PublicApiWriter.class, TraceCorrelation.class, PaymentControllerTest.Beans.class})
class PaymentControllerTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentProperties properties;
    @Autowired EntitlementQueryProperties reader;
    @MockitoBean PaymentCatalogService catalog;
    @MockitoBean PaymentAccountService accounts;
    @MockitoBean PaymentRateLimiter limiter;
    @MockitoBean JwtDecoder identityUserJwtDecoder;
    static final String USER = "11111111-1111-4111-8111-111111111111";
    static final String REF = "22222222-2222-4222-8222-222222222222";
    @TestConfiguration static class Beans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean io.micrometer.core.instrument.MeterRegistry metrics() { return new io.micrometer.core.instrument.simple.SimpleMeterRegistry(); }
    }
    @BeforeEach void setup() {
        properties.setMemberEvidenceEnabled(false); properties.setCatalogEnabled(true); properties.setPurchaseAccountEnabled(true); reader.setEnabled(false);
        when(identityUserJwtDecoder.decode("member")).thenReturn(token("MEMBER", "billing:purchase"));
        when(identityUserJwtDecoder.decode("guest")).thenReturn(token("GUEST", "billing:read"));
        when(identityUserJwtDecoder.decode("guest-purchase")).thenReturn(token("GUEST", "billing:purchase"));
        when(catalog.read()).thenReturn(new PaymentProductsResponse(1, List.of()));
        when(accounts.getOrCreate(eq(USER), any())).thenReturn(new PurchaseAccountResponse(REF));
    }
    @Test void evidenceWriterUsesVerifiedPrincipalAndTrustedSettings() throws Exception {
        properties.setMemberEvidenceEnabled(true);
        when(identityUserJwtDecoder.decode("member-evidence")).thenReturn(Jwt.withTokenValue("fake").header("alg", "RS256")
                .subject(USER).issuer("https://identity.test").audience(List.of("tosunsaeng-billing"))
                .claim("account_type", "MEMBER").claim("scope", "billing:purchase")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        when(accounts.getOrCreate(any(VerifiedMemberEvidence.class))).thenReturn(new PurchaseAccountResponse(REF));
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; })
                        .header("Authorization", "Bearer member-evidence").header("X-User-Id", "forged"))
                .andExpect(status().isOk()).andExpect(jsonPath("result.purchaseAccountRefId").value(REF));
        var captor = org.mockito.ArgumentCaptor.forClass(VerifiedMemberEvidence.class);
        verify(accounts).getOrCreate(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo(USER);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().environment()).isEqualTo("SANDBOX");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().document().getString("issuer")).isEqualTo("https://identity.test");
    }
    @Test void guestCatalogWorksWithFreeReaderOff() throws Exception {
        mvc.perform(get(PublicIngressFilter.PRODUCTS).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer guest"))
                .andExpect(status().isOk()).andExpect(jsonPath("result.products").isArray())
                .andExpect(jsonPath("result.products").isEmpty()).andExpect(header().string("Cache-Control", "no-store"));
        verify(limiter).acquire(false, USER);
    }
    @Test void purchaseOnlyScopeAndVerifiedSubjectAreUsed() throws Exception {
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; })
                        .header("Authorization", "Bearer member").header("X-User-Id", "forged"))
                .andExpect(status().isOk()).andExpect(jsonPath("result.purchaseAccountRefId").value(REF));
        verify(accounts).getOrCreate(eq(USER), any());
    }
    @Test void scopeDenialAndMemberDenialHaveDifferentHeaders() throws Exception {
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer guest"))
                .andExpect(status().isForbidden()).andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"billing:purchase\""));
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer guest-purchase"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("WWW-Authenticate"));
        verifyNoInteractions(accounts, limiter);
    }
    @Test void missingUnknownOrNonStringAccountTypeCannotPurchase() throws Exception {
        for (Object type : List.of("UNKNOWN", 1, List.of("MEMBER"))) {
            when(identityUserJwtDecoder.decode("bad-type")).thenReturn(token(type, "billing:purchase"));
            mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer bad-type"))
                    .andExpect(status().isForbidden());
        }
        when(identityUserJwtDecoder.decode("bad-type")).thenReturn(Jwt.withTokenValue("x").header("alg", "RS256")
                .subject(USER).claim("scope", "billing:purchase").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer bad-type"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts, limiter);
    }
    @Test void bodyQueryAndOversizeAreRejectedBeforeWrites() throws Exception {
        for (String body : List.of("{}", "null", " ", "x".repeat(16385))) {
            mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; })
                            .header("Authorization", "Bearer member").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PublicIngressFilter.ACCOUNT + "?userId=x").with(r -> { r.setLocalPort(8083); return r; })
                        .header("Authorization", "Bearer member")).andExpect(status().isBadRequest());
        verifyNoInteractions(accounts, limiter);
    }
    @Test void methodFlagsAndConnectorRemainIndependent() throws Exception {
        mvc.perform(get(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; }))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"));
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8082); return r; }).header("X-Forwarded-Port", "8083"))
                .andExpect(status().isNotFound());
        properties.setPurchaseAccountEnabled(false);
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isNotFound());
        mvc.perform(get(PublicIngressFilter.PRODUCTS).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer guest"))
                .andExpect(status().isOk());
        for (String path : List.of("/api/v1/payments/sync", "/api/v1/payments/entitlement", "/internal/v1/payments/accounts/withdrawal/events"))
            mvc.perform(post(path).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isNotFound());
    }
    @Test void rateLimitReturnsExactWaitWithoutCallingService() throws Exception {
        when(limiter.acquire(true, USER)).thenReturn(15L);
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer member"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "15"))
                .andExpect(jsonPath("code").value("RATE_LIMITED"));
        verifyNoInteractions(accounts);
    }
    @Test void storageAndKeysFailureAreErrorsNotEmptyProducts() throws Exception {
        when(catalog.read()).thenThrow(new IllegalStateException("secret-must-not-escape"));
        mvc.perform(get(PublicIngressFilter.PRODUCTS).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer guest"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("BILLING_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("result").isEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-must-not-escape"))));
        when(identityUserJwtDecoder.decode("outage")).thenThrow(new IdentityUserJwtDecoder.KeysUnavailable());
        mvc.perform(get(PublicIngressFilter.PRODUCTS).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer outage"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("AUTHENTICATION_TEMPORARILY_UNAVAILABLE"));
    }
    @Test void noTokenAndInvalidTokenFailClosed() throws Exception {
        mvc.perform(get(PublicIngressFilter.PRODUCTS).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isUnauthorized());
        when(identityUserJwtDecoder.decode("bad")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(get(PublicIngressFilter.PRODUCTS).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }
    @Test void longLivedPurchaseTokenCannotReachAccountWriter() throws Exception {
        when(identityUserJwtDecoder.decode("long")).thenReturn(Jwt.withTokenValue("fake").header("alg", "RS256")
                .subject(USER).claim("account_type", "MEMBER").claim("scope", "billing:purchase")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1801)).build());
        mvc.perform(post(PublicIngressFilter.ACCOUNT).with(r -> { r.setLocalPort(8083); return r; })
                .header("Authorization", "Bearer long")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("UNAUTHENTICATED"));
        verifyNoInteractions(accounts, limiter);
    }
    static Jwt token(Object type, String scope) {
        return Jwt.withTokenValue("fake").header("alg", "RS256").subject(USER).claim("account_type", type)
                .claim("scope", scope).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
