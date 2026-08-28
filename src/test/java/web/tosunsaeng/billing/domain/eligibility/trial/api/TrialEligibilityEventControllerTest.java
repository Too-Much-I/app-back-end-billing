package web.tosunsaeng.billing.domain.eligibility.trial.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import web.tosunsaeng.billing.global.config.security.SecurityConfig;
import web.tosunsaeng.billing.domain.eligibility.trial.exception.TrialEligibilityException;
import web.tosunsaeng.billing.domain.eligibility.trial.application.TrialEligibilityEventOutcome;
import web.tosunsaeng.billing.domain.eligibility.trial.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityCandidate;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityEventType;

@WebMvcTest(TrialEligibilityEventController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1"
})
class TrialEligibilityEventControllerTest {

    private static final String PATH = "/internal/v1/eligibility/trial/events";
    private static final byte[] JSON = "{\"schemaVersion\":1}".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrialEligibilityEventDecoder decoder;

    @MockitoBean
    private TrialEligibilityEventService service;

    @Test
    void identityTestPrincipalReceivesBodyless204() throws Exception {
        TrialEligibilityEvent event = event();
        when(decoder.decode(any())).thenReturn(event);
        when(service.process(event)).thenReturn(TrialEligibilityEventOutcome.APPLIED);

        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).process(event);
    }

    @Test
    void wrongPrincipalIsDeniedBeforeController() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(decoder, service);
    }

    @Test
    void unsupportedContractUsesStablePrivacySafeEnvelope() throws Exception {
        when(decoder.decode(any())).thenThrow(TrialEligibilityException.unsupportedContract());

        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTRACT"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("opaque-scope-v1")
                )));
    }

    @Test
    void temporaryFailureIncludesRetryAfter() throws Exception {
        when(decoder.decode(any())).thenReturn(event());
        when(service.process(any())).thenThrow(TrialEligibilityException.temporarilyUnavailable());

        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("BILLING_TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    void contentLengthOver16KiBIsRejectedBeforeDecode() throws Exception {
        byte[] oversized = new byte[TrialEligibilityEventDecoder.MAX_PAYLOAD_BYTES + 1];

        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(decoder, service);
    }

    @Test
    void nonJsonContentTypeIsInvalidRequest() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private static TrialEligibilityEvent event() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new TrialEligibilityEvent(
                "00000000-0000-4000-8000-000000000001",
                TrialEligibilityEventType.VERIFIED,
                1,
                "identity",
                now,
                "opaque-scope-v1",
                "e8b37a41-bae6-47f1-a770-052e6c5786d4",
                now.minusSeconds(1),
                null,
                1,
                List.of(new TrialEligibilityCandidate(
                        "v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                )),
                "digest"
        );
    }
}
