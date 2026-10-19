package web.tosunsaeng.billing.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TraceCorrelationTest {

    @Test
    void classifiesW3cTraceparentWithoutExposingHeader() {
        TraceCorrelation correlation = new TraceCorrelation(provider(null));

        assertThat(correlation.classify(null))
                .isEqualTo(TraceCorrelation.TraceparentStatus.MISSING);
        assertThat(correlation.classify("not-a-traceparent"))
                .isEqualTo(TraceCorrelation.TraceparentStatus.INVALID);
        assertThat(correlation.classify(
                "00-00000000000000000000000000000000-0123456789abcdef-01"
        )).isEqualTo(TraceCorrelation.TraceparentStatus.INVALID);
        assertThat(correlation.classify(
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"
        )).isEqualTo(TraceCorrelation.TraceparentStatus.VALID);
    }

    @Test
    void usesCurrentTracingSpanAndHasSafeFallback() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("0123456789abcdef0123456789abcdef");

        assertThat(new TraceCorrelation(provider(tracer)).currentTraceId())
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(new TraceCorrelation(provider(null)).currentTraceId())
                .matches("[0-9a-f]{32}");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Tracer> provider(Tracer tracer) {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracer);
        return provider;
    }
}
