package web.tosunsaeng.billing.domain.entitlement.api;

import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import web.tosunsaeng.billing.domain.entitlement.application.EntitlementQueryService;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.BillingMongoIndexInitializer;
import web.tosunsaeng.billing.domain.benefit.config.BenefitCatalogInitializer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "billing.entitlement-query.enabled=true", "billing.entitlement-query.public-connector-enabled=true",
        "billing.entitlement-query.legacy-attribution-verified=true", "billing.entitlement-query.issuer=https://identity.test",
        "billing.entitlement-query.jwks-uri=https://identity.test/jwks", "billing.mongodb.require-transactions=true",
        "billing.mongodb.initialize-indexes=true", "billing.trial-eligibility.expected-consumer-scope-id=scope",
        "management.tracing.enabled=true", "management.tracing.sampling.probability=1.0"
})
@ActiveProfiles("test")
@Import(EntitlementQueryTraceIntegrationTest.CaptureConfig.class)
class EntitlementQueryTraceIntegrationTest {
    static final int PUBLIC_PORT = freePort();
    static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    @LocalServerPort int internalPort;
    @MockitoBean EntitlementQueryService service;
    @MockitoBean JwtDecoder identityUserJwtDecoder;
    @MockitoBean BillingMongoIndexInitializer indexInitializer;
    @MockitoBean BenefitCatalogInitializer catalogInitializer;
    @MockitoBean web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionCapabilityVerifier capabilityVerifier;
    @Autowired Tracer tracer;
    @Autowired Capture capture;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("billing.entitlement-query.public-port", () -> PUBLIC_PORT);
    }
    @TestConfiguration static class CaptureConfig {
        @Bean Capture capture() { return new Capture(); }
    }
    static class Capture implements SpanProcessor {
        final Queue<SpanData> ended = new ConcurrentLinkedQueue<>();
        public void onStart(Context parent, ReadWriteSpan span) { }
        public boolean isStartRequired() { return false; }
        public void onEnd(ReadableSpan span) { ended.add(span.toSpanData()); }
        public boolean isEndRequired() { return true; }
    }
    @BeforeEach void setup() {
        capture.ended.clear();
        when(identityUserJwtDecoder.decode("test")).thenReturn(EntitlementQueryControllerTest.token("billing:read"));
    }
    @Test void productionControllerStartsInternalSpanBelowHttpAndDropsBaggage() throws Exception {
        when(service.query(anyString())).thenAnswer(invocation -> {
            assertThat(tracer.currentSpan()).isNotNull();
            assertThat(tracer.getAllBaggage()).isEmpty();
            return new EntitlementQueryResponse(Instant.now(), Consistency.LOCAL_PROJECTION, Status.PENDING, List.of());
        });
        var response = send(PUBLIC_PORT, "/api/v1/entitlements");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Trace-Id")).contains(TRACE_ID);
        assertThat(response.headers().firstValue("Retry-After")).contains("5");
        var inner = inner();
        assertThat(inner.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(inner.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(inner.hasEnded()).isTrue();
        var server = capture.ended.stream().filter(s -> s.getKind() == SpanKind.SERVER && s.getTraceId().equals(TRACE_ID)).findFirst().orElseThrow();
        assertThat(inner.getSpanId()).isNotEqualTo(server.getSpanId());
        assertThat(inner.getAttributes().isEmpty()).isTrue();
    }
    @Test void failedInnerSpanEndsWithoutSensitiveExceptionAndActualPortsAreIsolated() throws Exception {
        when(service.query(anyString())).thenThrow(new IllegalStateException("PRIVATE_SOURCE_PAYLOAD"));
        assertThat(send(PUBLIC_PORT, "/api/v1/entitlements").statusCode()).isEqualTo(503);
        assertThat(inner().hasEnded()).isTrue();
        assertThat(inner().getEvents().toString()).doesNotContain("PRIVATE_SOURCE_PAYLOAD");
        assertThat(send(PUBLIC_PORT, "/internal/v1/reservations").statusCode()).isEqualTo(404);
        assertThat(send(PUBLIC_PORT, "/actuator/health").statusCode()).isEqualTo(404);
        assertThat(send(internalPort, "/api/v1/entitlements").statusCode()).isEqualTo(404);
    }
    SpanData inner() { return capture.ended.stream().filter(s -> "entitlement_query".equals(s.getName())).findFirst().orElseThrow(); }
    HttpResponse<String> send(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer test").header("traceparent", "00-" + TRACE_ID + "-0123456789abcdef-01")
                .header("baggage", "private-source=DO_NOT_PROPAGATE").GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    static int freePort() {
        try (var socket = new java.net.ServerSocket(0)) { return socket.getLocalPort(); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
