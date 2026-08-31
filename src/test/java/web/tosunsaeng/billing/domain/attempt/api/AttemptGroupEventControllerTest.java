package web.tosunsaeng.billing.domain.attempt.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
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

import org.junit.jupiter.api.BeforeEach;
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

import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventMetrics;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventService;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.exception.AttemptGroupEventException;
import web.tosunsaeng.billing.global.config.security.SecurityConfig;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@WebMvcTest(AttemptGroupEventController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1",
        "billing.attempt-group-events.enabled=true"
})
class AttemptGroupEventControllerTest {

    private static final String PATH = "/internal/v1/attempt-group-events";
    private static final byte[] JSON = "{\"schemaVersion\":1}".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttemptGroupEventDecoder decoder;
    @MockitoBean
    private AttemptGroupEventService service;
    @MockitoBean
    private AttemptGroupEventMetrics metrics;
    @MockitoBean
    private TraceCorrelation traceCorrelation;

    @BeforeEach
    void traceContext() {
        when(traceCorrelation.classify(nullable(String.class)))
                .thenReturn(TraceCorrelation.TraceparentStatus.MISSING);
    }

    @Test
    void learningCoreRoleReceivesBodyless204() throws Exception {
        AttemptGroupStatusEvent event = event();
        when(decoder.decode(any())).thenReturn(event);
        when(service.process(event)).thenReturn(AttemptGroupEventOutcome.APPLIED);

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).process(event);
        verify(metrics).recordTraceContext("missing");
    }

    @Test
    void identityAndUnsignedRequestsAreDeniedBeforeDecode() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(decoder, service);
    }

    @Test
    void projectionNotReadyHasStableRetryContract() throws Exception {
        when(decoder.decode(any())).thenReturn(event());
        when(service.process(any())).thenThrow(AttemptGroupEventException.projectionNotReady());

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.code").value("ATTEMPT_PROJECTION_NOT_READY"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ex_a1b2")
                )));
    }

    @Test
    void targetConflictIsNonRetryable409() throws Exception {
        when(decoder.decode(any())).thenReturn(event());
        when(service.process(any())).thenThrow(AttemptGroupEventException.targetConflict());

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_TARGET_CONFLICT"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void oversizedAndNonJsonRequestsAreInvalid() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES + 1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private static AttemptGroupStatusEvent event() {
        return new AttemptGroupStatusEvent(
                "8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442",
                "AttemptGroupStatusChanged",
                1,
                "learning-core",
                Instant.parse("2026-08-31T11:59:00Z"),
                "e8b37a41-bae6-47f1-a770-052e6c5786d4",
                "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
                "ex_a1b2c3d4e5_0826_1530",
                AttemptGroupEventTarget.GRADING,
                null,
                null,
                "digest"
        );
    }
}
