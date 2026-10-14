package web.tosunsaeng.billing.domain.payment.api;

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
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.global.config.security.*;

@RestController
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentCatalogService catalog;
    private final PaymentAccountService accounts;
    private final PaymentRateLimiter limiter;
    private final PublicApiWriter writer;
    private final EntitlementQueryProperties jwt;
    private final PaymentProperties properties;
    private final Clock clock;
    private final Tracer tracer;
    private final MeterRegistry metrics;
    public PaymentController(PaymentCatalogService catalog, PaymentAccountService accounts, PaymentRateLimiter limiter,
            PublicApiWriter writer, EntitlementQueryProperties jwt, PaymentProperties properties, Clock clock,
            ObjectProvider<Tracer> tracer, MeterRegistry metrics) {
        this.catalog = catalog; this.accounts = accounts; this.limiter = limiter; this.writer = writer;
        this.jwt = jwt; this.properties = properties; this.clock = clock; this.tracer = tracer.getIfAvailable(); this.metrics = metrics;
    }
    @GetMapping(PublicIngressFilter.PRODUCTS)
    public void products(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(false, principal, request, response);
    }
    @PostMapping(PublicIngressFilter.ACCOUNT)
    public void account(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(true, principal, request, response);
    }
    private void handle(boolean purchase, Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String operation = purchase ? "payment_purchase_account" : "payment_products";
        Span span = tracer == null ? null : tracer.nextSpan().name(operation).start();
        long began = System.nanoTime();
        String outcome = "SUCCESS";
        try (Tracer.SpanInScope scope = tracer == null ? null : tracer.withSpan(span)) {
            if (purchase && !"MEMBER".equals(principal.getClaims().get("account_type"))) throw new PaymentException(403, "FORBIDDEN");
            if (request.getQueryString() != null || request.getInputStream().read() != -1) throw new PaymentException(400, "INVALID_REQUEST");
            long retry = limiter.acquire(purchase, principal.getSubject());
            if (retry > 0) {
                outcome = "RATE_LIMITED"; writer.paymentError(response, 429, outcome, null, null, retry); return;
            }
            Object result = !purchase ? catalog.read() : properties.isMemberEvidenceEnabled()
                    ? accounts.getOrCreate(VerifiedMemberEvidence.fromAuthenticated(principal, jwt.getIssuer(),
                            properties.getEnvironment(), jwt.getClockSkew(), clock.instant()))
                    : accounts.getOrCreate(principal.getSubject(), principal.getExpiresAt().plus(jwt.getClockSkew()));
            writer.paymentSuccess(response, result);
        } catch (RuntimeException error) {
            int status = error instanceof PaymentException p ? p.status() : 503;
            outcome = error instanceof PaymentException p ? p.code() : "BILLING_TEMPORARILY_UNAVAILABLE";
            if (span != null && status >= 500) span.error(new IllegalStateException(outcome));
            writer.paymentError(response, status, outcome, null, null, 5);
        } finally {
            long duration = System.nanoTime() - began;
            metrics.timer("billing.payment.request", "operation", operation, "outcome", outcome).record(duration, TimeUnit.NANOSECONDS);
            log.info("timestamp={} service=billing environment={} operation={} outcome={} durationMs={} traceId={} spanId={}",
                    clock.instant(), properties.getEnvironment(), operation, outcome, TimeUnit.NANOSECONDS.toMillis(duration),
                    response.getHeader("X-Trace-Id"), span == null ? "" : span.context().spanId());
            if (span != null) span.end();
        }
    }
}
