package web.tosunsaeng.billing.global.observability;

import java.util.UUID;
import java.util.regex.Pattern;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TraceCorrelation {

    private static final Pattern TRACEPARENT = Pattern.compile(
            "00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"
    );
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";

    private final Tracer tracer;

    public TraceCorrelation(ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    public String currentTraceId() {
        if (tracer != null) {
            Span span = tracer.currentSpan();
            if (span != null) {
                return span.context().traceId();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public TraceparentStatus classify(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return TraceparentStatus.MISSING;
        }
        if (!TRACEPARENT.matcher(traceparent).matches()) {
            return TraceparentStatus.INVALID;
        }
        String[] parts = traceparent.split("-");
        if (ZERO_TRACE_ID.equals(parts[1]) || ZERO_SPAN_ID.equals(parts[2])) {
            return TraceparentStatus.INVALID;
        }
        return TraceparentStatus.VALID;
    }

    public enum TraceparentStatus {
        VALID,
        MISSING,
        INVALID
    }
}
