package web.tosunsaeng.billing.global.config.security;

import java.time.Clock;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RevenueCatWebhookProperties.class)
public class RevenueCatWebhookConfiguration {
    /** Executes before the user ingress guard; no forwarded header selects a port or host. */
    public static final class Guard extends OncePerRequestFilter {
        private final RevenueCatWebhookProperties p;
        private final EntitlementQueryProperties pub;
        public Guard(RevenueCatWebhookProperties p, EntitlementQueryProperties pub) { this.p = p; this.pub = pub; }
        @Override protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain) throws IOException, ServletException {
            if (RevenueCatWebhookFilter.PATH.equals(q.getRequestURI())) {
                r.setHeader("Cache-Control", "no-store");
                List<String> hosts = Collections.list(q.getHeaders("Host"));
                boolean host = hosts.size() == 1 && (p.getHost().equalsIgnoreCase(hosts.getFirst())
                        || (p.getHost() + ":443").equalsIgnoreCase(hosts.getFirst()));
                if (!p.isIngressEnabled() || !pub.isPublicConnectorEnabled() || q.getLocalPort() != pub.getPublicPort() || !host) { r.setStatus(404); return; }
                if (!"POST".equals(q.getMethod())) { r.setStatus(405); return; }
                if (q.getQueryString() != null) { r.setStatus(400); return; }
            }
            chain.doFilter(q, r);
        }
    }
    @Bean FilterRegistrationBean<Guard> revenueCatIngressGuard(RevenueCatWebhookProperties p, EntitlementQueryProperties pub) {
        var registration = new FilterRegistrationBean<>(new Guard(p, pub)); registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 15); return registration;
    }
    @Bean
    @ConditionalOnProperty(prefix = "billing.payment.provider", name = "ingress-enabled", havingValue = "true")
    RevenueCatWebhookFilter revenueCatWebhookFilter(RevenueCatWebhookProperties p, EntitlementQueryProperties pub,
            PaymentProperties payments, BillingMongoProperties mongo, PaymentStore store, Clock clock) {
        if (!pub.isPublicConnectorEnabled() || !mongo.isRequireTransactions() || !mongo.isInitializeIndexes()
                || !payments.getRecovery().isFenceRequired() || p.getHost() == null
                || !p.getHost().matches("(?=.{1,253}\\z)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))
            throw new IllegalStateException("Webhook ingress requires trusted configuration and transactional schema");
        PurchaseLookupKey lookup;
        try { lookup = new PurchaseLookupKey(Base64.getDecoder().decode(p.getLookupKey()), p.getLookupKeyVersion()); }
        catch (RuntimeException e) { throw new IllegalStateException("Invalid provider lookup configuration"); }
        Map<String, RevenueCatWebhookDecoder.App> apps = new LinkedHashMap<>();
        Map<PurchaseObservation.Store, String> coreApps = new EnumMap<>(PurchaseObservation.Store.class);
        p.getApps().forEach((id, app) -> {
            if (coreApps.putIfAbsent(app.getStore(), id) != null || app.getProducts().isEmpty()) throw new IllegalStateException("Ambiguous provider app mapping");
            apps.put(id, new RevenueCatWebhookDecoder.App(app.getStore(), app.getProducts()));
        });
        var auth = new RevenueCatWebhookAuthentication(p.getAuthorization(), p.getSigningSecret(), clock);
        var decoder = new RevenueCatWebhookDecoder(payments.getEnvironment(), p.getProject(), apps, lookup, clock, payments.isSandboxPurchasesEnabled());
        var core = new PurchaseCore(store, payments, clock, coreApps, lookup.version());
        return new RevenueCatWebhookFilter(auth, decoder, core::receiveWebhook);
    }
    // The handler belongs exclusively to its security chain, not the servlet container's global filters.
    @Bean @ConditionalOnProperty(prefix = "billing.payment.provider", name = "ingress-enabled", havingValue = "true")
    FilterRegistrationBean<RevenueCatWebhookFilter> disableGlobalRevenueCatFilter(RevenueCatWebhookFilter filter) {
        var registration = new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
    @Bean @Order(-1) SecurityFilterChain revenueCatSecurityChain(HttpSecurity http,
            org.springframework.beans.factory.ObjectProvider<RevenueCatWebhookFilter> filter) throws Exception {
        http.securityMatcher(q -> RevenueCatWebhookFilter.PATH.equals(q.getRequestURI()))
                .csrf(c -> c.disable()).cors(c -> c.disable()).httpBasic(c -> c.disable()).formLogin(c -> c.disable()).logout(c -> c.disable())
                .requestCache(c -> c.disable()).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.anyRequest().denyAll())
                .exceptionHandling(c -> c.authenticationEntryPoint((q, r, e) -> r.setStatus(404)).accessDeniedHandler((q, r, e) -> r.setStatus(404)));
        RevenueCatWebhookFilter handler = filter.getIfAvailable(); if (handler != null) http.addFilterBefore(handler, AuthorizationFilter.class);
        return http.build();
    }
}
