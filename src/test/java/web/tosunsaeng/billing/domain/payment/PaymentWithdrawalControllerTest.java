package web.tosunsaeng.billing.domain.payment;

import java.time.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.billing.domain.payment.api.*;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalService;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentWithdrawalException;
import web.tosunsaeng.billing.global.config.security.*;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = PaymentWithdrawalController.class, properties = {
        "billing.internal-ingress.mode=test", "billing.trial-eligibility.expected-consumer-scope-id=fake-scope",
        "billing.payment.withdrawal-enabled=true", "billing.payment.environment=SANDBOX",
        "billing.mongodb.initialize-indexes=true", "billing.mongodb.require-transactions=true"
})
@ActiveProfiles("test")
@Import({SecurityConfig.class, PaymentWithdrawalDecoder.class, PaymentWithdrawalControllerTest.Beans.class})
class PaymentWithdrawalControllerTest {
    @TestConfiguration static class Beans {
        @Bean Clock clock() { return Clock.fixed(PaymentWithdrawalDecoderTest.NOW, ZoneOffset.UTC); }
        @Bean SimpleMeterRegistry metrics() { return new SimpleMeterRegistry(); }
    }
    @Autowired MockMvc mvc;
    @Autowired PaymentProperties properties;
    @MockitoBean PaymentWithdrawalService service;
    final String path = PaymentWithdrawalController.PATH;
    final String body = PaymentWithdrawalDecoderTest.payload(PaymentWithdrawalDecoderTest.NOW.toString());
    @BeforeEach void enabled() { properties.setWithdrawalEnabled(true); }
    @Test void appliedDuplicateAndNoopReturnOnly204() throws Exception {
        when(service.process(any())).thenReturn(PaymentWithdrawalService.Outcome.APPLIED,
                PaymentWithdrawalService.Outcome.DUPLICATE, PaymentWithdrawalService.Outcome.NOOP);
        for (int i=0;i<3;i++) mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent()).andExpect(content().string(""));
    }
    @Test void unsignedWrongRoleWrongMethodAndDisabledDoNotReachService() throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("learning").roles("LEARNING_CORE_WORKLOAD")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("identity").roles("IDENTITY_WORKLOAD"))).andExpect(status().isForbidden());
        properties.setWithdrawalEnabled(false);
        mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }
    @Test void strictDecoderAndBoundedRequestRunBeforeService() throws Exception {
        for (String invalid : new String[]{"", "{}", body+"{}", " ".repeat(16385)}) {
            mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.TEXT_PLAIN).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path+"?userId=untrusted").with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTRACT"));
        verifyNoInteractions(service);
    }
    @Test void conflictAndFailureUseInternalEnvelopeAndSanitizeUnexpectedError() throws Exception {
        when(service.process(any())).thenThrow(PaymentWithdrawalException.conflict("EVENT_ID_CONFLICT"))
                .thenThrow(PaymentWithdrawalException.conflict("WITHDRAWAL_STATE_CONFLICT"))
                .thenThrow(new IllegalStateException("private-payload"));
        for (String code : new String[]{"EVENT_ID_CONFLICT", "WITHDRAWAL_STATE_CONFLICT"})
            mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.retryable").value(false)).andExpect(jsonPath("$.correlationId").isString())
                    .andExpect(jsonPath("$.isSuccess").doesNotExist());
        mvc.perform(post(path).with(user("identity").roles("IDENTITY_WORKLOAD")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.retryable").value(true)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-payload"))));
    }
    @Test void publicConnectorGuardRejectsInternalPathEvenWithSpoofedHeaders() throws Exception {
        var p = new EntitlementQueryProperties(); p.setPublicConnectorEnabled(true); p.setPublicPort(9090);
        var writer = new PublicApiWriter(new com.fasterxml.jackson.databind.ObjectMapper(), org.mockito.Mockito.mock(web.tosunsaeng.billing.global.observability.TraceCorrelation.class));
        var filter = new PublicIngressFilter(p, writer, properties);
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", path);
        request.setLocalPort(9090); request.addHeader("Authorization", "fake"); request.addHeader("X-Forwarded-Port", "8082");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request, response, chain);
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(chain, service);
    }
}
