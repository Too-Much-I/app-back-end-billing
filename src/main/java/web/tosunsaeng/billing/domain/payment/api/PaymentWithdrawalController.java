package web.tosunsaeng.billing.domain.payment.api;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalService;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentWithdrawalException;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.response.InternalApiError;

@RestController
public class PaymentWithdrawalController {
    public static final String PATH = "/internal/v1/payments/accounts/withdrawal/events";
    public static final String SPAN_NAME = "payment_withdrawal_consume";
    private static final Logger log = LoggerFactory.getLogger(PaymentWithdrawalController.class);
    private final PaymentWithdrawalDecoder decoder;
    private final PaymentWithdrawalService service;
    private final PaymentProperties properties;
    private final Tracer tracer;
    private final MeterRegistry metrics;
    private final Clock clock;
    public PaymentWithdrawalController(PaymentWithdrawalDecoder decoder, PaymentWithdrawalService service,
            PaymentProperties properties, ObjectProvider<Tracer> tracer, MeterRegistry metrics, Clock clock) {
        this.decoder = decoder; this.service = service; this.properties = properties;
        this.tracer = tracer.getIfAvailable(); this.metrics = metrics; this.clock = clock;
    }
    @PostMapping(PATH)
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        if (!properties.isWithdrawalEnabled()) {
            throw new InternalApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_TEMPORARILY_UNAVAILABLE",
                    "Withdrawal consumer is disabled.", true, 5);
        }
        Span span = tracer == null ? null : tracer.spanBuilder().name(SPAN_NAME).start();
        long began = System.nanoTime();
        String outcome = "BILLING_TEMPORARILY_UNAVAILABLE";
        try (var scope = tracer == null ? null : tracer.withSpan(span)) {
            outcome = service.process(decoder.decode(read(request))).name();
            return ResponseEntity.noContent().build();
        } catch (InternalApiException error) {
            outcome = error.code();
            if (span != null && error.status().is5xxServerError()) span.error(new IllegalStateException(outcome));
            throw error;
        } catch (RuntimeException error) {
            if (span != null) span.error(new IllegalStateException(outcome));
            throw PaymentWithdrawalException.unavailable();
        } finally {
            long elapsed = System.nanoTime() - began;
            try {
                metrics.timer("billing.payment.withdrawal", "outcome", outcome).record(elapsed, TimeUnit.NANOSECONDS);
                log.info("timestamp={} service=billing environment={} operation={} outcome={} durationMs={} traceId={} spanId={}",
                        clock.instant(), properties.getEnvironment(), SPAN_NAME, outcome, TimeUnit.NANOSECONDS.toMillis(elapsed),
                        span == null ? "" : span.context().traceId(), span == null ? "" : span.context().spanId());
            } finally { if (span != null) span.end(); }
        }
    }
    private static byte[] read(HttpServletRequest request) {
        try {
            if (request.getQueryString() != null || request.getContentType() == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))
                    || request.getContentLengthLong() > PaymentWithdrawalDecoder.MAX_BYTES) throw InternalApiException.invalidRequest();
            byte[] payload = request.getInputStream().readNBytes(PaymentWithdrawalDecoder.MAX_BYTES + 1);
            if (payload.length == 0 || payload.length > PaymentWithdrawalDecoder.MAX_BYTES) throw InternalApiException.invalidRequest();
            return payload;
        } catch (IOException | IllegalArgumentException error) { throw InternalApiException.invalidRequest(); }
    }
    @ExceptionHandler(InternalApiException.class)
    public ResponseEntity<InternalApiError> error(InternalApiException error) {
        var headers = new HttpHeaders();
        if (error.retryAfterSeconds() != null) headers.set(HttpHeaders.RETRY_AFTER, error.retryAfterSeconds().toString());
        return new ResponseEntity<>(new InternalApiError(error.code(), error.getMessage(), error.retryable(),
                UUID.randomUUID().toString()), headers, error.status());
    }
}
