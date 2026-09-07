package web.tosunsaeng.billing.domain.entitlement.api;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.billing.domain.entitlement.application.*;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.global.config.security.*;

@RestController
public class EntitlementQueryController {
    private static final Logger log = LoggerFactory.getLogger(EntitlementQueryController.class);
    private final EntitlementQueryService service;
    private final EntitlementQueryRateLimiter limiter;
    private final PublicApiWriter writer;
    private final Tracer tracer;
    private final MeterRegistry metrics;
    private final InternalIngressProperties ingress;
    private final Clock clock;
    public EntitlementQueryController(EntitlementQueryService service, EntitlementQueryRateLimiter limiter,
            PublicApiWriter writer, ObjectProvider<Tracer> tracer, MeterRegistry metrics,
            InternalIngressProperties ingress, Clock clock) {
        this.service = service; this.limiter = limiter; this.writer = writer;
        this.tracer = tracer.getIfAvailable(); this.metrics = metrics; this.ingress = ingress; this.clock = clock;
    }
    @GetMapping(PublicIngressFilter.PATH)
    public void query(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Micrometer's unspecified kind maps to OpenTelemetry INTERNAL.
        Span span = tracer == null ? null : tracer.nextSpan().name("entitlement_query").start();
        long began = System.nanoTime();
        String outcome = "FAILURE";
        String failure = "NONE";
        try (Tracer.SpanInScope scope = tracer == null ? null : tracer.withSpan(span)) {
            if (request.getQueryString() != null || request.getInputStream().read() != -1) {
                outcome = "INVALID_REQUEST"; writer.error(response, 400, outcome); return;
            }
            if (!limiter.allow(principal.getSubject())) {
                outcome = "RATE_LIMITED"; writer.error(response, 429, outcome); return;
            }
            var result = service.query(principal.getSubject());
            outcome = result.status().name();
            writer.success(response, result, result.status() == web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Status.PENDING);
        } catch (RuntimeException e) {
            outcome = "FAILURE";
            failure = e instanceof EntitlementQueryException queryError ? queryError.failure().name() : "STORAGE";
            if (span != null) { span.error(new IllegalStateException(failure)); }
            writer.error(response, 503, "ENTITLEMENT_QUERY_UNAVAILABLE");
        } finally {
            long duration = System.nanoTime() - began;
            metrics.timer("billing.entitlement.query", "outcome", outcome, "reason", failure).record(duration, TimeUnit.NANOSECONDS);
            log.info("timestamp={} service=billing environment={} operation=entitlement_query outcome={} reason={} durationMs={} traceId={} spanId={}",
                    clock.instant(), ingress.getEnvironment(), outcome, failure, TimeUnit.NANOSECONDS.toMillis(duration),
                    response.getHeader("X-Trace-Id"), span == null ? "" : span.context().spanId());
            if (span != null) { span.end(); }
        }
    }
}
