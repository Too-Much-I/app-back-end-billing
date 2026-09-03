package web.tosunsaeng.billing.domain.ownerrebind.api;

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

import io.micrometer.tracing.Tracer;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindOutcome;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindService;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindTracing;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.ownerrebind.exception.OwnerRebindException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(OwnerRebindTracePropagationIntegrationTest.SpanCaptureConfiguration.class)
@TestPropertySource(properties = {
        "management.tracing.enabled=true",
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1",
        "billing.owner-rebind.enabled=true"
})
class OwnerRebindTracePropagationIntegrationTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String TRACEPARENT = "00-" + TRACE_ID
            + "-0123456789abcdef-01";

    @Autowired private Tracer tracer;
    @Autowired private CapturingSpanProcessor spanProcessor;
    @LocalServerPort private int port;

    @MockitoBean private UserMergedEventDecoder mergeDecoder;
    @MockitoBean private PhoneOwnerRebindEventDecoder phoneDecoder;
    @MockitoBean private OwnerRebindService service;

    @BeforeEach
    void clearSpans() {
        spanProcessor.clear();
    }

    @Test
    void productionControllerContinuesTraceAndDoesNotPropagateBaggage() throws Exception {
        OwnerRebindCommand command = command();
        AtomicReference<String> decoderSpanId = new AtomicReference<>();
        AtomicReference<String> serviceSpanId = new AtomicReference<>();
        AtomicReference<Map<String, String>> baggage = new AtomicReference<>();
        when(mergeDecoder.decode(any())).thenAnswer(invocation -> {
            decoderSpanId.set(tracer.currentSpan().context().spanId());
            return command;
        });
        when(service.process(command)).thenAnswer(invocation -> {
            serviceSpanId.set(tracer.currentSpan().context().spanId());
            baggage.set(tracer.getAllBaggage());
            return OwnerRebindOutcome.APPLIED;
        });

        HttpResponse<Void> response = send(true);

        assertThat(response.statusCode()).isEqualTo(204);
        SpanData consume = consumeSpan();
        SpanData server = spanProcessor.finishedSpans().stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .filter(span -> span.getTraceId().equals(consume.getTraceId()))
                .findFirst()
                .orElseThrow();
        assertThat(consume.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(consume.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(consume.hasEnded()).isTrue();
        assertThat(server.getSpanId()).isNotEqualTo(consume.getSpanId());
        assertThat(decoderSpanId).hasValue(consume.getSpanId());
        assertThat(serviceSpanId).hasValue(consume.getSpanId());
        assertThat(baggage.get()).isEmpty();
        assertNoSensitiveAttributes(consume);
    }

    @Test
    void failedRequestEndsInnerSpanWithoutSensitiveAttributes() throws Exception {
        OwnerRebindCommand command = command();
        when(mergeDecoder.decode(any())).thenReturn(command);
        when(service.process(command)).thenThrow(OwnerRebindException.ownerConflict());

        HttpResponse<Void> response = send(false);

        assertThat(response.statusCode()).isEqualTo(409);
        SpanData consume = consumeSpan();
        assertThat(consume.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(consume.hasEnded()).isTrue();
        assertNoSensitiveAttributes(consume);
    }

    private HttpResponse<Void> send(boolean includeBaggage) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port
                        + OwnerRebindEventController.MERGE_PATH))
                .header("Content-Type", "application/json")
                .header("traceparent", TRACEPARENT)
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (includeBaggage) {
            request.header("baggage", "private-key=must-not-propagate");
        }
        return HttpClient.newHttpClient().send(
                request.build(), HttpResponse.BodyHandlers.discarding()
        );
    }

    private SpanData consumeSpan() {
        return spanProcessor.finishedSpans().stream()
                .filter(span -> OwnerRebindTracing.SPAN_NAME.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertNoSensitiveAttributes(SpanData span) {
        Set<String> names = span.getAttributes().asMap().keySet().stream()
                .map(key -> key.getKey())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(names).doesNotContain(
                "eventId", "userId", "sourceUserId", "targetUserId", "subjectRefId",
                "trialClaimId", "attemptGroupId", "sessionId", "payload", "digest",
                "Authorization", "traceparent", "SigV4", "credential"
        );
    }

    private static OwnerRebindCommand command() {
        return new OwnerRebindCommand(
                "00000000-0000-4000-8000-000000000001",
                OwnerRebindEventKind.USER_MERGED,
                1,
                Instant.parse("2026-09-02T04:59:00Z"),
                "00000000-0000-4000-8000-000000000002",
                "00000000-0000-4000-8000-000000000003",
                null, null, null, "digest"
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
        SecurityFilterChain ownerRebindTraceEndpointSecurity(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher(
                            OwnerRebindEventController.PHONE_PATH,
                            OwnerRebindEventController.MERGE_PATH
                    )
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
