package web.tosunsaeng.billing.domain.payment;

import java.time.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.billing.domain.payment.api.*;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalService;
import web.tosunsaeng.billing.global.config.security.*;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=PaymentWithdrawalController.class, properties={
        "billing.workload-jwt.receiver-enabled=true", "billing.workload-jwt.port=8444", "billing.workload-jwt.environment=test",
        "billing.workload-jwt.tls-key-store=/fake/not-opened-in-mvc.p12", "billing.workload-jwt.tls-key-store-password=fake",
        "billing.internal-ingress.mode=disabled", "billing.payment.environment=SANDBOX", "billing.payment.withdrawal-enabled=true",
        "billing.trial-eligibility.expected-consumer-scope-id=fake", "billing.mongodb.initialize-indexes=true", "billing.mongodb.require-transactions=true"
})
@Import({SecurityConfig.class, WorkloadSecurityConfiguration.class, PaymentWithdrawalDecoder.class, PaymentWithdrawalJwtControllerTest.Beans.class})
class PaymentWithdrawalJwtControllerTest {
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) { registry.add("billing.workload-jwt.identity-public-jwk-set", () -> publicSet(KEY)); }
    @TestConfiguration static class Beans {
        @Bean Clock clock() { return CLOCK; }
        @Bean SimpleMeterRegistry metrics() { return new SimpleMeterRegistry(); }
    }
    @Autowired MockMvc mvc;
    @MockitoBean PaymentWithdrawalService service;
    final String path=PaymentWithdrawalController.PATH;
    final String payload=PaymentWithdrawalDecoderTest.payload(NOW.toString());
    @Test void realControllerPreserves204AndVerifiesJwtBeforeDecoder() throws Exception {
        when(service.process(any())).thenReturn(PaymentWithdrawalService.Outcome.APPLIED,PaymentWithdrawalService.Outcome.DUPLICATE);
        for(int i=0;i<2;i++) mvc.perform(post(path).with(r->{r.setLocalPort(8444);return r;})
                .header("Authorization","Bearer "+sign(claims())).contentType("application/json").content(payload))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service,times(2)).process(any());
        mvc.perform(post(path).with(r->{r.setLocalPort(8444);return r;}).content("not-json")).andExpect(status().isUnauthorized());
        verifyNoMoreInteractions(service);
    }
    @Test void publicPortAndWrongAudienceNeverReachActualService() throws Exception {
        mvc.perform(post(path).with(r->{r.setLocalPort(8083);return r;}).header("Authorization","Bearer "+sign(claims()))
                .contentType("application/json").content(payload)).andExpect(status().isNotFound());
        mvc.perform(post(path).with(r->{r.setLocalPort(8444);return r;}).header("Authorization","Bearer "+sign(claims().replace(AUD,"other")))
                .contentType("application/json").content(payload)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
