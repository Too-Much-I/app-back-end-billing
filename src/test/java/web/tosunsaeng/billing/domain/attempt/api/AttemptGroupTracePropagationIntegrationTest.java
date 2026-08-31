package web.tosunsaeng.billing.domain.attempt.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventService;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.tracing.enabled=true",
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1",
        "billing.attempt-group-events.enabled=true"
})
class AttemptGroupTracePropagationIntegrationTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String TRACEPARENT = "00-" + TRACE_ID
            + "-0123456789abcdef-01";

    @Autowired
    private Tracer tracer;
    @Autowired
    private Propagator propagator;
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttemptGroupEventDecoder decoder;
    @MockitoBean
    private AttemptGroupEventService service;

    @Test
    void configuredPropagatorContinuesValidInboundW3cTraceparent() {
        assertThat(propagator.fields())
                .as(propagator.getClass().getName())
                .contains("traceparent");
        Map<String, String> carrier = Map.of("traceparent", TRACEPARENT);
        Span span = propagator.extract(carrier, Map::get)
                .name("attempt_group_event_consume")
                .kind(Span.Kind.CONSUMER)
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            assertThat(tracer.currentSpan().context().traceId()).isEqualTo(TRACE_ID);
        } finally {
            span.end();
        }
    }

    @Test
    void httpConsumerContinuesInboundTraceparent() throws Exception {
        AttemptGroupStatusEvent event = event();
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        when(decoder.decode(any())).thenReturn(event);
        when(service.process(event)).thenAnswer(invocation -> {
            observedTraceId.set(tracer.currentSpan().context().traceId());
            return AttemptGroupEventOutcome.APPLIED;
        });

        mockMvc.perform(post("/internal/v1/attempt-group-events")
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .header("traceparent", TRACEPARENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\":1}"))
                .andExpect(status().isNoContent());

        assertThat(observedTraceId).hasValue(TRACE_ID);
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
