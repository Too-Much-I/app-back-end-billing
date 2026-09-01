package web.tosunsaeng.billing.domain.attempt.application;

import java.util.function.Supplier;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
public class AttemptGroupEventTracing {

    public static final String SPAN_NAME = "attempt_group_event_consume";

    private final Tracer tracer;

    public AttemptGroupEventTracing(Tracer tracer) {
        this.tracer = tracer;
    }

    public <T> T inConsumeSpan(Supplier<T> action) {
        Span span = tracer.spanBuilder()
                .name(SPAN_NAME)
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } catch (RuntimeException | Error exception) {
            span.error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }
}
