package web.tosunsaeng.billing.domain.payment.recovery;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.*;

/** Only fixed operation/outcome tags and locally generated W3C context; never raw errors or baggage. */
final class RecoveryTracingTransport implements HttpIdentityRecoveryClient.Transport, AutoCloseable {
    private final HttpIdentityRecoveryClient.Transport delegate;
    private final Tracer tracer;
    RecoveryTracingTransport(HttpIdentityRecoveryClient.Transport delegate, Tracer tracer) {
        this.delegate = delegate; this.tracer = tracer;
    }
    @Override public HttpIdentityRecoveryClient.Response exchange(HttpIdentityRecoveryClient.Request request, byte[] body) {
        String operation = switch (request.getUri().getPath()) {
            case "/internal/v1/billing/withdrawals/snapshots" -> "snapshot_create";
            case "/internal/v1/billing/withdrawals/snapshots/status" -> "snapshot_status";
            case "/internal/v1/billing/withdrawals/snapshots/page" -> "snapshot_page";
            case "/internal/v1/billing/withdrawals/feed/page" -> "feed_page";
            default -> throw RecoveryRemoteException.contract();
        };
        Span span = tracer.spanBuilder().name("billing_recovery_http").kind(Span.Kind.CLIENT).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            span.tag("operation", operation);
            var headers = new LinkedHashMap<String, List<String>>();
            request.headers().forEach((key, values) -> {
                if (!Set.of("baggage", "traceparent", "tracestate").contains(key.toLowerCase(Locale.ROOT))) headers.put(key, values);
            });
            String trace = span.context().traceId(), id = span.context().spanId();
            if (trace.matches("[0-9a-f]{32}") && id.matches("[0-9a-f]{16}")
                    && !trace.equals("0".repeat(32)) && !id.equals("0".repeat(16))) {
                headers.put("traceparent", List.of("00-" + trace + "-" + id + "-"
                        + (Boolean.TRUE.equals(span.context().sampled()) ? "01" : "00")));
            }
            var response = delegate.exchange(new HttpIdentityRecoveryClient.Request(request.getUri(), headers), body);
            span.tag("outcome", response.status() >= 200 && response.status() < 300 ? "SUCCESS" : "REMOTE_ERROR");
            return response;
        } catch (RuntimeException failure) {
            span.tag("outcome", "TRANSPORT_ERROR"); // Do not attach an exception message/stack with provider data.
            throw failure;
        } finally { span.end(); }
    }
    @Override public void close() throws Exception { if (delegate instanceof AutoCloseable c) c.close(); }
}
