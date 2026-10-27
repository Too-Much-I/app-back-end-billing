package web.tosunsaeng.billing.global.config.security;

import java.time.Clock;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WorkloadJwtProperties.class, EntitlementQueryProperties.class,
        web.tosunsaeng.billing.domain.examdeletion.DeletionProperties.class,
        web.tosunsaeng.billing.domain.reanswer.ReanswerProperties.class,
        web.tosunsaeng.billing.domain.partlearning.PartLearningProperties.class})
public class WorkloadSecurityConfiguration {
    @Bean FilterRegistrationBean<WorkloadIngressFilter> workloadIngressGuard(WorkloadJwtProperties p, PaymentProperties payments, OwnerRebindProperties owners, AttemptGroupEventProperties attempts,
            web.tosunsaeng.billing.domain.examdeletion.DeletionProperties deletions,
            web.tosunsaeng.billing.domain.reanswer.ReanswerProperties reanswer,
            web.tosunsaeng.billing.domain.partlearning.PartLearningProperties partLearning) {
        var bean = new FilterRegistrationBean<>(new WorkloadIngressFilter(p, payments, owners, attempts, deletions, reanswer, partLearning)); bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); return bean;
    }
    @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> workloadConnector(WorkloadJwtProperties p,
            PaymentProperties payments, EntitlementQueryProperties publicProperties, BillingMongoProperties mongo,
            TrialEligibilityProperties eligibility, Environment environment) {
        if (p.getPort() != 0 && (p.getPort() < 1024 || p.getPort() > 65535 || p.getPort() == publicProperties.getPublicPort()
                || p.getPort() == environment.getProperty("server.port", Integer.class, 8082))) throw new IllegalStateException("Invalid workload port");
        if (p.anyReceiverEnabled()) {
            p.validateEnvironment(payments.getEnvironment());
            if (p.getPort() == 0 || p.getTlsKeyStore().isBlank() || p.getTlsKeyStorePassword().isBlank()
                    || !mongo.isRequireTransactions() || !mongo.isInitializeIndexes()
                    || eligibility.getExpectedConsumerScopeId().isBlank()) throw new IllegalStateException("Workload ingress requires isolated port and transactional schema");
        }
        return factory -> {
            if (!p.anyReceiverEnabled()) return;
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(p.getPort()); connector.setScheme("https"); connector.setSecure(true); connector.setProperty("SSLEnabled", "true");
            SSLHostConfig tls = new SSLHostConfig(); tls.setProtocols("TLSv1.2,TLSv1.3");
            SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(tls, SSLHostConfigCertificate.Type.RSA);
            certificate.setCertificateKeystoreFile(p.getTlsKeyStore()); certificate.setCertificateKeystorePassword(p.getTlsKeyStorePassword());
            certificate.setCertificateKeystoreType("PKCS12"); tls.addCertificate(certificate); connector.addSslHostConfig(tls);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
    @Bean @Order(0) SecurityFilterChain workloadSecurityChain(HttpSecurity http, WorkloadJwtProperties p, Clock clock) throws Exception {
        http.securityMatcher(request -> p.getPort() > 0 && request.getLocalPort() == p.getPort())
                .csrf(c -> c.disable()).cors(c -> c.disable()).httpBasic(c -> c.disable()).formLogin(c -> c.disable()).logout(c -> c.disable())
                .requestCache(c -> c.disable()).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.anyRequest().hasAnyRole("IDENTITY_WORKLOAD", "LEARNING_CORE_WORKLOAD"))
                .exceptionHandling(c -> c.authenticationEntryPoint((q, r, e) -> WorkloadIngressFilter.reject(r, 401))
                        .accessDeniedHandler((q, r, e) -> WorkloadIngressFilter.reject(r, 403)));
        if (p.anyReceiverEnabled()) http.addFilterBefore(new WorkloadAuthenticationFilter(p,
                new WorkloadJwtVerifierRegistry(p, clock)), AuthorizationFilter.class);
        return http.build();
    }
}
