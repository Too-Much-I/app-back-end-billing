package web.tosunsaeng.billing.domain.attempt.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventService;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventTracing;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.exception.AttemptGroupEventException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AttemptGroupTracePropagationIntegrationTest.SpanCaptureConfiguration.class)
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
    private CapturingSpanProcessor spanProcessor;
    @LocalServerPort
    private int port;

    @MockitoBean
    private AttemptGroupEventDecoder decoder;
    @MockitoBean
    private AttemptGroupEventService service;

    @BeforeEach
    void clearSpans() {
        spanProcessor.clear();
    }

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
        AtomicReference<String> decoderSpanId = new AtomicReference<>();
        AtomicReference<String> serviceSpanId = new AtomicReference<>();
        AtomicReference<Map<String, String>> observedBaggage = new AtomicReference<>();
        when(decoder.decode(any())).thenAnswer(invocation -> {
            decoderSpanId.set(tracer.currentSpan().context().spanId());
            return event;
        });
        when(service.process(event)).thenAnswer(invocation -> {
            serviceSpanId.set(tracer.currentSpan().context().spanId());
            observedBaggage.set(tracer.getAllBaggage());
            return AttemptGroupEventOutcome.APPLIED;
        });

        HttpResponse<Void> response = sendEvent(true);

        assertThat(response.statusCode()).isEqualTo(204);

        SpanData consumeSpan = consumeSpan();
        SpanData serverSpan = spanProcessor.finishedSpans().stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .filter(span -> span.getTraceId().equals(consumeSpan.getTraceId()))
                .findFirst()
                .orElseThrow();

        assertThat(consumeSpan.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(consumeSpan.getName()).isEqualTo(AttemptGroupEventTracing.SPAN_NAME);
        assertThat(consumeSpan.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(consumeSpan.hasEnded()).isTrue();
        assertThat(serverSpan.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(serverSpan.getTraceId()).isEqualTo(consumeSpan.getTraceId());
        assertThat(serverSpan.getSpanId()).isNotEqualTo(consumeSpan.getSpanId());
        assertThat(isDescendantOf(
                consumeSpan, serverSpan, spanProcessor.finishedSpans()
        )).isTrue();
        assertThat(decoderSpanId).hasValue(consumeSpan.getSpanId());
        assertThat(serviceSpanId).hasValue(consumeSpan.getSpanId());
        assertThat(observedBaggage.get()).isEmpty();
        assertNoSensitiveAttributes(consumeSpan);
    }

    @Test
    void productionControllerEndsFailedConsumeSpanWithoutSensitiveAttributes() throws Exception {
        AttemptGroupStatusEvent event = event();
        when(decoder.decode(any())).thenReturn(event);
        when(service.process(event)).thenThrow(AttemptGroupEventException.targetConflict());

        HttpResponse<Void> response = sendEvent(false);

        assertThat(response.statusCode()).isEqualTo(409);

        SpanData consumeSpan = consumeSpan();
        assertThat(consumeSpan.hasEnded()).isTrue();
        assertThat(consumeSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertNoSensitiveAttributes(consumeSpan);
    }

    private SpanData consumeSpan() {
        return spanProcessor.finishedSpans().stream()
                .filter(span -> AttemptGroupEventTracing.SPAN_NAME.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<Void> sendEvent(boolean includeBaggage) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port
                        + "/internal/v1/attempt-group-events"))
                .header("Content-Type", "application/json")
                .header("traceparent", TRACEPARENT)
                .POST(HttpRequest.BodyPublishers.ofString("{\"schemaVersion\":1}"));
        if (includeBaggage) {
            request.header("baggage", "private-key=must-not-propagate");
        }
        return HttpClient.newHttpClient().send(
                request.build(), HttpResponse.BodyHandlers.discarding()
        );
    }

    private static void assertNoSensitiveAttributes(SpanData span) {
        Set<String> attributeNames = span.getAttributes().asMap().keySet().stream()
                .map(key -> key.getKey())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(attributeNames).doesNotContain(
                "eventId", "userId", "sessionId", "attemptGroupId", "payload", "digest",
                "Authorization", "traceparent", "SigV4", "credential"
        );
    }

    private static boolean isDescendantOf(
            SpanData descendant,
            SpanData ancestor,
            List<SpanData> spans
    ) {
        String parentSpanId = descendant.getParentSpanId();
        while (!parentSpanId.equals("0000000000000000")) {
            if (parentSpanId.equals(ancestor.getSpanId())) {
                return true;
            }
            String currentParentSpanId = parentSpanId;
            parentSpanId = spans.stream()
                    .filter(span -> span.getSpanId().equals(currentParentSpanId))
                    .map(SpanData::getParentSpanId)
                    .findFirst()
                    .orElse("0000000000000000");
        }
        return false;
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

    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCaptureConfiguration {

        @Bean
        CapturingSpanProcessor capturingSpanProcessor() {
            return new CapturingSpanProcessor();
        }

        @Bean
        @Order(0)
        SecurityFilterChain traceEndpointSecurity(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/internal/v1/attempt-group-events")
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }

    static final class CapturingSpanProcessor implements SpanProcessor {

        private final ConcurrentLinkedQueue<SpanData> finishedSpans =
                new ConcurrentLinkedQueue<>();

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            finishedSpans.add(span.toSpanData());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        List<SpanData> finishedSpans() {
            return List.copyOf(finishedSpans);
        }

        void clear() {
            finishedSpans.clear();
        }
    }
}
