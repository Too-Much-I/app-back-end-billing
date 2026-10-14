package web.tosunsaeng.billing.global.config.security;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

class RevenueCatWebhookChainTest {
    static final String AUTH = "Bearer fake-provider-auth", SECRET = "fake-webhook-signing-secret-32-bytes";
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity @EnableWebMvc
    static class Harness {
        @Bean EntitlementQueryProperties pub() { var p = new EntitlementQueryProperties(); p.setPublicConnectorEnabled(true); return p; }
        @Bean AtomicInteger received() { return new AtomicInteger(); }
        @Bean RevenueCatWebhookFilter testHandler(AtomicInteger received) {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var decoder = new RevenueCatWebhookDecoder("SANDBOX", "fake", Map.of("fake-app",
                    new RevenueCatWebhookDecoder.App(PurchaseObservation.Store.APP_STORE, Set.of("premium"))), new PurchaseLookupKey(new byte[32], "test"), clock);
            return new RevenueCatWebhookFilter(new RevenueCatWebhookAuthentication(AUTH, SECRET, clock), decoder, r -> received.incrementAndGet());
        }
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe { @RequestMapping("/**") void handle() { throw new AssertionError("Provider request must not reach controllers"); } }
    @Test void actualSecurityChainUsesProviderNotUserAuthentication() throws Exception {
        byte[] body = "{\"api_version\":\"1.0\",\"event\":{\"id\":\"fake\",\"type\":\"TEST\"}}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((NOW.getEpochSecond() + ".").getBytes(StandardCharsets.US_ASCII));
        String signature = "t=" + NOW.getEpochSecond() + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
        new WebApplicationContextRunner().withUserConfiguration(Harness.class, RevenueCatWebhookConfiguration.class)
                .withPropertyValues("billing.payment.provider.host=billing.example.test").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(RevenueCatWebhookProperties.class);
            var pub = context.getBean(EntitlementQueryProperties.class);
            var guard = context.getBean("revenueCatIngressGuard", FilterRegistrationBean.class).getFilter();
            var publicGuard = new PublicIngressFilter(pub, org.mockito.Mockito.mock(PublicApiWriter.class), new PaymentProperties());
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).addFilters(guard, publicGuard).apply(springSecurity()).build();
            var request = MockMvcRequestBuilders.post(RevenueCatWebhookFilter.PATH).header("Host", "billing.example.test")
                    .header("Authorization", AUTH).header("X-RevenueCat-Webhook-Signature", signature).contentType("application/json").content(body)
                    .with(r -> { r.setLocalPort(8083); return r; });
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(404);
            // Test-only sink; production ON must construct/verify its real Mongo-backed core.
            properties.setIngressEnabled(true);
            var response = mvc.perform(request).andReturn().getResponse(); assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).isEmpty(); assertThat(context.getBean(AtomicInteger.class)).hasValue(1);
            var wrong = MockMvcRequestBuilders.post(RevenueCatWebhookFilter.PATH).header("Host", "billing.example.test")
                    .header("Authorization", "Bearer user-token").contentType("application/json").content(body)
                    .with(r -> { r.setLocalPort(8083); return r; });
            assertThat(mvc.perform(wrong).andReturn().getResponse().getStatus()).isEqualTo(401);
            assertThat(context.getBean(AtomicInteger.class)).hasValue(1);
        });
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class OffHarness { @Bean EntitlementQueryProperties pub() { return new EntitlementQueryProperties(); } }
    @Test void offHasNoCredentialOrDatabaseDependencyAndNoHandler() {
        new WebApplicationContextRunner().withUserConfiguration(OffHarness.class, RevenueCatWebhookConfiguration.class).run(c -> {
            assertThat(c).hasNotFailed(); assertThat(c).doesNotHaveBean(RevenueCatWebhookFilter.class);
        });
    }
    @Test void onCannotStartWithoutRealReceiverDependencies() {
        new WebApplicationContextRunner().withUserConfiguration(OffHarness.class, RevenueCatWebhookConfiguration.class)
                .withPropertyValues("billing.payment.provider.ingress-enabled=true").run(c -> assertThat(c).hasFailed());
    }
}
