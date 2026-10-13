package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

/** Fresh actual production Spring security chains for each receiver/key/port fixture combination. */
class LearningCoreWorkloadChainTest {
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity @EnableWebMvc
    static class Harness {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC); }
        @Bean AtomicInteger reached() { return new AtomicInteger(); }
        @Bean Probe probe(AtomicInteger reached) { return new Probe(reached); }
    }
    @RestController static class Probe {
        private final AtomicInteger reached;
        Probe(AtomicInteger reached) { this.reached = reached; }
        @RequestMapping("/**") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
        void handle() { reached.incrementAndGet(); }
    }
    static WebApplicationContextRunner runner(boolean identity, boolean lc) throws Exception {
        var keys = LearningCoreWorkloadHttpFixtureTest.fixture("public-jwks.json");
        return new WebApplicationContextRunner().withUserConfiguration(Harness.class, SecurityConfig.class, WorkloadSecurityConfiguration.class)
                .withPropertyValues("billing.workload-jwt.receiver-enabled=" + identity,
                        "billing.workload-jwt.learning-core-receiver-enabled=" + lc,
                        "billing.workload-jwt.identity-public-jwk-set=" + (identity ? keys.get("identity") : "invalid-off-key"),
                        "billing.workload-jwt.learning-core-public-jwk-set=" + (lc ? keys.get("lc") : "invalid-off-key"),
                        "billing.workload-jwt.environment=test", "billing.workload-jwt.port=8444",
                        "billing.workload-jwt.tls-key-store=/fake/mvc-only.p12", "billing.workload-jwt.tls-key-store-password=fake",
                        "billing.payment.environment=SANDBOX", "billing.payment.withdrawal-enabled=true",
                        "billing.owner-rebind.enabled=true", "billing.attempt-group-events.enabled=true",
                        "billing.trial-eligibility.expected-consumer-scope-id=fake",
                        "billing.mongodb.initialize-indexes=true", "billing.mongodb.require-transactions=true");
    }
    @TestFactory java.util.stream.Stream<DynamicTest> http27ActualSecurityChains() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (var c : LearningCoreWorkloadHttpFixtureTest.fixture("http-cases.json").get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("id").asText(), () -> runner(c.get("identityEnabled").asBoolean(), c.get("lcEnabled").asBoolean()).run(context -> {
                assertThat(context).hasNotFailed();
                var config = context.getBean(WorkloadJwtProperties.class);
                if (c.get("expectedStage").asText().equals("NO_HTTP_RESPONSE")) {
                    var factory = new TomcatServletWebServerFactory();
                    @SuppressWarnings("unchecked") var customizer = (WebServerFactoryCustomizer<TomcatServletWebServerFactory>) context.getBean("workloadConnector");
                    customizer.customize(factory); assertThat(factory.getAdditionalTomcatConnectors()).isEmpty(); return;
                }
                var guard = context.getBean("workloadIngressGuard", org.springframework.boot.web.servlet.FilterRegistrationBean.class).getFilter();
                var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).addFilters(guard).apply(springSecurity()).build();
                var req = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(c.get("method").asText()), c.get("path").asText())
                        .with(r -> { r.setLocalPort(c.get("localPort").asInt());
                            if (!c.get("queryString").isNull()) r.setQueryString(c.get("queryString").asText()); return r; });
                for (var value : c.get("authorization")) req.header(c.get("authorizationHeaderName").asText(), value.asText());
                var response = mvc.perform(req).andReturn().getResponse();
                int expected = c.get("expectedStatus").isNull() ? 204 : c.get("expectedStatus").asInt();
                assertThat(response.getStatus()).isEqualTo(expected);
                assertThat(context.getBean(AtomicInteger.class)).hasValue(expected == 204 ? 1 : 0);
                assertThat(response.getContentAsByteArray()).isEmpty();
                if (expected != 204) assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            })));
        }
        return tests.stream();
    }
    @Test void retiredIamAndTestRoleDoNotBypassGuard() throws Exception {
        for (String mode : List.of("test", "lattice_aws_iam")) {
            runner(false, false).withPropertyValues("spring.profiles.active=test", "billing.internal-ingress.mode=" + mode,
                    "billing.internal-ingress.lattice-only=true", "billing.internal-ingress.environment=staging").run(context -> {
                assertThat(context).hasNotFailed();
                var guard = context.getBean("workloadIngressGuard", org.springframework.boot.web.servlet.FilterRegistrationBean.class).getFilter();
                var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).addFilters(guard).apply(springSecurity()).build();
                for (String path : List.of("/internal/v1/reservations", "/internal/v1/reservations/status", "/internal/v1/reservations/continuations/phone",
                        "/internal/v1/reservations/id/confirm", "/internal/v1/reservations/id/cancel", "/internal/v1/attempt-group-events")) {
                    for (int port : new int[]{8082,8083,8444}) {
                        var req = MockMvcRequestBuilders.post(path).header("Authorization", "AWS4-HMAC-SHA256 fake")
                                .header("X-Forwarded-Port", "8444").with(r -> { r.setLocalPort(port); return r; })
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("legacy").roles("LEARNING_CORE_WORKLOAD"));
                        assertThat(mvc.perform(req).andReturn().getResponse().getStatus()).isEqualTo(404);
                    }
                }
                assertThat(context.getBean(AtomicInteger.class)).hasValue(0);
            });
        }
    }
    @Test void missingEnabledKeyPreventsActualSecurityContextStartup() throws Exception {
        runner(false, true).withPropertyValues("billing.workload-jwt.learning-core-public-jwk-set=").run(context ->
                assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
    }
}
