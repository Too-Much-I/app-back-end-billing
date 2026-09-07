package web.tosunsaeng.billing.domain.entitlement.api;

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
import web.tosunsaeng.billing.domain.entitlement.application.*;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.global.config.security.*;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EntitlementQueryController.class, properties = {
        "billing.entitlement-query.enabled=true", "billing.entitlement-query.public-connector-enabled=true",
        "billing.entitlement-query.legacy-attribution-verified=true",
        "billing.entitlement-query.issuer=https://identity.test", "billing.entitlement-query.jwks-uri=https://identity.test/jwks",
        "billing.mongodb.require-transactions=true", "billing.mongodb.initialize-indexes=true",
        "billing.trial-eligibility.expected-consumer-scope-id=scope"
})
@Import({PublicSecurityConfig.class, SecurityConfig.class, PublicApiWriter.class, TraceCorrelation.class,
        EntitlementQueryRateLimiter.class, EntitlementQueryControllerTest.TestBeans.class})
class EntitlementQueryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired EntitlementQueryProperties properties;
    @MockitoBean EntitlementQueryService service;
    @MockitoBean JwtDecoder identityUserJwtDecoder;
    static final String USER = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    @TestConfiguration static class TestBeans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean io.micrometer.core.instrument.MeterRegistry metrics() { return new io.micrometer.core.instrument.simple.SimpleMeterRegistry(); }
    }
    @BeforeEach void setup() {
        properties.setEnabled(true);
        when(identityUserJwtDecoder.decode("good")).thenReturn(token("billing:read"));
        when(identityUserJwtDecoder.decode("no-scope")).thenReturn(token("billing:purchase"));
        when(service.query(USER)).thenReturn(new EntitlementQueryResponse(Instant.now(), Consistency.LOCAL_PROJECTION, Status.READY, List.of()));
    }
    @Test void onlyVerifiedSubjectIsUsedAndGuestIsAllowed() throws Exception {
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; })
                        .header("Authorization", "Bearer good").header("X-User-Id", "other").header("account_type", "MEMBER"))
                .andExpect(status().isOk()).andExpect(jsonPath("isSuccess").value(true))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).query(USER);
    }
    @Test void noTokenScopeAndJwksOutageUsePublicErrors() throws Exception {
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHENTICATED"));
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer no-scope"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("FORBIDDEN"));
        when(identityUserJwtDecoder.decode("outage")).thenThrow(new IdentityUserJwtDecoder.KeysUnavailable());
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer outage"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("AUTHENTICATION_TEMPORARILY_UNAVAILABLE"));
        verifyNoInteractions(service);
    }
    @Test void rejectsQueryAndBodyBeforeRead() throws Exception {
        mvc.perform(get(PublicIngressFilter.PATH + "?userId=other").with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer good"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer good").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void connectorIsolationFlagAndMethodAreFailClosed() throws Exception {
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8082); return r; }).header("X-Forwarded-Port", "8083"))
                .andExpect(status().isNotFound());
        for (String path : List.of("/internal/v1/reservations", "/actuator/health", "/api/v1/payments/entitlement")) {
            mvc.perform(get(path).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isNotFound());
        }
        mvc.perform(post(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; }))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
        properties.setEnabled(false);
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; })).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
    @Test void storageFailureNeverBecomesZeroOrEmptySuccess() throws Exception {
        when(service.query(USER)).thenThrow(new IllegalStateException("sensitive payload must not escape"));
        mvc.perform(get(PublicIngressFilter.PATH).with(r -> { r.setLocalPort(8083); return r; }).header("Authorization", "Bearer good"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("ENTITLEMENT_QUERY_UNAVAILABLE"))
                .andExpect(jsonPath("result").isEmpty()).andExpect(header().string("Cache-Control", "no-store"));
    }
    static Jwt token(String scope) {
        return Jwt.withTokenValue("test").header("alg", "RS256").subject(USER).claim("account_type", "GUEST")
                .claim("scope", scope).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
