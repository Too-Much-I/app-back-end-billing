package web.tosunsaeng.billing.domain.payment.api;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.*;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.application.PaymentRateLimiter;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.sync.*;
import web.tosunsaeng.billing.global.config.security.*;

@RestController
public class PaymentSyncController {
    private final ObjectProvider<PaymentSyncService> service;
    private final PaymentSyncDecoder decoder;
    private final PaymentRateLimiter limiter;
    private final PublicApiWriter writer;
    private final EntitlementQueryProperties jwt;
    private final MeterRegistry metrics;
    private final Tracer tracer;
    public PaymentSyncController(ObjectProvider<PaymentSyncService> service, PaymentSyncDecoder decoder, PaymentRateLimiter limiter,
            PublicApiWriter writer, EntitlementQueryProperties jwt, MeterRegistry metrics, ObjectProvider<Tracer> tracer) {
        this.service = service; this.decoder = decoder; this.limiter = limiter; this.writer = writer;
        this.jwt = jwt; this.metrics = metrics; this.tracer = tracer.getIfAvailable();
    }
    @PostMapping(PublicIngressFilter.SYNC)
    public void sync(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Span span = tracer == null ? null : tracer.nextSpan().name("payment_sync").start();
        long start = System.nanoTime(); String outcome = "SUCCESS";
        try (Tracer.SpanInScope scope = tracer == null ? null : tracer.withSpan(span)) {
            if (!"MEMBER".equals(principal.getClaimAsString("account_type"))) throw new PaymentException(403, "FORBIDDEN");
            if (request.getQueryString() != null) throw new PaymentException(400, "INVALID_REQUEST");
            try {
                var types = Collections.list(request.getHeaders("Content-Type"));
                if (types.size() != 1) throw new IllegalArgumentException();
                MediaType type = MediaType.parseMediaType(types.getFirst());
                if (!"application".equals(type.getType()) || !"json".equals(type.getSubtype())
                        || type.getCharset() != null && !java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset())) throw new IllegalArgumentException();
            } catch (RuntimeException invalid) { throw new PaymentException(415, "UNSUPPORTED_MEDIA_TYPE"); }
            var keys = Collections.list(request.getHeaders("Idempotency-Key"));
            if (keys.size() != 1) throw new PaymentException(400, "INVALID_REQUEST");
            if (request.getContentLengthLong() > PaymentSyncDecoder.MAX_BYTES) throw new PaymentException(413, "PAYLOAD_TOO_LARGE");
            var input = decoder.decode(keys.getFirst(), request.getInputStream().readNBytes(PaymentSyncDecoder.MAX_BYTES + 1));
            long retry = limiter.acquire(PaymentRateLimiter.Route.SYNC, principal.getSubject());
            if (retry > 0) { outcome = "RATE_LIMITED"; writer.paymentError(response, 429, outcome, null, null, retry); return; }
            PaymentSyncService app = service.getIfAvailable();
            if (app == null) throw PaymentException.unavailable();
            var result = app.sync(principal.getSubject(), principal.getExpiresAt().plus(jwt.getClockSkew()), input);
            outcome = result.status().name();
            writer.paymentSuccess(response, result, result.status() == PaymentSyncResult.Status.PENDING);
        } catch (RuntimeException error) {
            int status = error instanceof PaymentException p ? p.status() : 503;
            outcome = error instanceof PaymentException p ? p.code() : "BILLING_TEMPORARILY_UNAVAILABLE";
            if (span != null && status >= 500) span.error(new IllegalStateException(outcome));
            writer.paymentError(response, status, outcome, null, null, 5);
        } finally {
            metrics.timer("billing.payment.request", "operation", "payment_sync", "outcome", outcome).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            if (span != null) span.end();
        }
    }
}
