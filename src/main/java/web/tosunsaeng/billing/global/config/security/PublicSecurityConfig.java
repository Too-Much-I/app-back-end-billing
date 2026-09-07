package web.tosunsaeng.billing.global.config.security;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;

@Configuration
@EnableConfigurationProperties(EntitlementQueryProperties.class)
public class PublicSecurityConfig {
    @Bean
    JwtDecoder identityUserJwtDecoder(EntitlementQueryProperties p, Clock clock) { return new IdentityUserJwtDecoder(p, clock); }

    @Bean
    FilterRegistrationBean<PublicIngressFilter> publicIngressGuard(EntitlementQueryProperties p, PublicApiWriter writer) {
        var registration = new FilterRegistrationBean<>(new PublicIngressFilter(p, writer));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> publicConnector(EntitlementQueryProperties p,
            TrialEligibilityProperties eligibility, BillingMongoProperties mongo, Environment environment) {
        if (p.isPublicConnectorEnabled() && (p.getPublicPort() < 1024 || p.getPublicPort() > 65535
                || p.getPublicPort() == environment.getProperty("server.port", Integer.class, 8082))) {
            throw new IllegalStateException("Public connector must use a separate port");
        }
        if (p.isEnabled()) {
            if (!p.isPublicConnectorEnabled() || !p.isLegacyAttributionVerified()
                    || !mongo.isRequireTransactions() || !mongo.isInitializeIndexes()
                    || eligibility.getExpectedConsumerScopeId().isBlank()
                    || p.getClockSkew() == null || p.getClockSkew().isNegative()
                    || p.getClockSkew().compareTo(Duration.ofSeconds(60)) > 0
                    || !https(p.getIssuer()) || !https(p.getJwksUri())) {
                throw new IllegalStateException("Public reader requires trusted JWT, isolated ingress, transactional schema and attribution coverage");
            }
        }
        return factory -> {
            if (p.isPublicConnectorEnabled()) {
                Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connector.setPort(p.getPublicPort());
                factory.addAdditionalTomcatConnectors(connector);
            }
        };
    }
    private static boolean https(String value) {
        try { var uri = URI.create(value); return "https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getFragment() == null; }
        catch (RuntimeException e) { return false; }
    }
    @Bean @Order(1)
    SecurityFilterChain publicSecurityChain(HttpSecurity http, JwtDecoder identityUserJwtDecoder,
            EntitlementQueryProperties p, PublicApiWriter writer) throws Exception {
        return http.securityMatcher(request -> request.getLocalPort() == p.getPublicPort()
                        || request.getRequestURI().startsWith("/api/"))
                .csrf(c -> c.disable()).cors(c -> c.disable()).httpBasic(c -> c.disable())
                .formLogin(c -> c.disable()).logout(c -> c.disable()).requestCache(c -> c.disable())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.requestMatchers(PublicIngressFilter.PATH).hasAuthority("SCOPE_billing:read")
                        .anyRequest().denyAll())
                .exceptionHandling(c -> c.authenticationEntryPoint((req, res, e) -> writer.error(res, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, e) -> writer.error(res, 403, "FORBIDDEN")))
                .oauth2ResourceServer(c -> c.withObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter>() {
                            @Override public <O extends org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                // Default Spring handler rethrows AuthenticationServiceException instead of serializing it.
                                filter.setAuthenticationFailureHandler((req, res, error) -> {
                                    Throwable cause = error;
                                    while (cause != null && !(cause instanceof IdentityUserJwtDecoder.KeysUnavailable)) { cause = cause.getCause(); }
                                    writer.error(res, cause == null ? 401 : 503,
                                            cause == null ? "UNAUTHENTICATED" : "AUTHENTICATION_TEMPORARILY_UNAVAILABLE");
                                });
                                return filter;
                            }
                        }).jwt(jwt -> jwt.decoder(identityUserJwtDecoder)
                                .jwtAuthenticationConverter(token -> {
                                    String scope = token.getClaimAsString("scope");
                                    var authorities = Arrays.stream((scope == null ? "" : scope).split("\\s+"))
                                            .filter(v -> !v.isBlank()).map(v -> new SimpleGrantedAuthority("SCOPE_" + v)).toList();
                                    return new JwtAuthenticationToken(token, authorities, token.getSubject());
                                }))
                        .authenticationEntryPoint((req, res, e) -> {
                            Throwable cause = e;
                            while (cause != null && !(cause instanceof IdentityUserJwtDecoder.KeysUnavailable)) { cause = cause.getCause(); }
                            writer.error(res, cause == null ? 401 : 503,
                                    cause == null ? "UNAUTHENTICATED" : "AUTHENTICATION_TEMPORARILY_UNAVAILABLE");
                        }))
                .build();
    }
}
