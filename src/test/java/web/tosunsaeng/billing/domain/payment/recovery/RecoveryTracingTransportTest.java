package web.tosunsaeng.billing.domain.payment.recovery;

import io.micrometer.tracing.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryTracingTransportTest {
    @Test void clientSpanOnlyPropagatesItsOwnContextAndNeverSensitiveHeadersAsTags() {
        var tracer = mock(Tracer.class); var builder = mock(Span.Builder.class); var span = mock(Span.class);
        var context = mock(TraceContext.class);
        when(tracer.spanBuilder()).thenReturn(builder); when(builder.name(anyString())).thenReturn(builder);
        when(builder.kind(any())).thenReturn(builder); when(builder.start()).thenReturn(span); when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("1".repeat(32)); when(context.spanId()).thenReturn("2".repeat(16)); when(context.sampled()).thenReturn(true);
        AtomicReference<HttpIdentityRecoveryClient.Request> seen = new AtomicReference<>();
        var transport = new RecoveryTracingTransport((request, body) -> {
            seen.set(request); return new HttpIdentityRecoveryClient.Response(200, Map.of(), new byte[0]);
        }, tracer);
        byte[] body = new byte[]{1,2};
        var request = new HttpIdentityRecoveryClient.Request(URI.create("https://identity.example.invalid/internal/v1/billing/withdrawals/feed/page"),
                Map.of("Authorization", List.of("Bearer fake"), "baggage", List.of("private"),
                        "traceparent", List.of("untrusted"), "tracestate", List.of("private")));
        transport.exchange(request, body);
        assertThat(seen.get().firstMatchingHeader("traceparent")).contains("00-"+"1".repeat(32)+"-"+"2".repeat(16)+"-01");
        assertThat(seen.get().firstMatchingHeader("Authorization")).contains("Bearer fake");
        assertThat(seen.get().firstMatchingHeader("baggage")).isEmpty();
        assertThat(seen.get().firstMatchingHeader("tracestate")).isEmpty();
        verify(span).tag("operation", "feed_page"); verify(span).tag("outcome", "SUCCESS"); verify(span).end();
        verify(span, never()).error(any()); verify(builder).kind(Span.Kind.CLIENT);
        verify(span, atLeastOnce()).context();
        verifyNoMoreInteractions(span);
    }
    @Test void failureEndsSpanWithoutAttachingRawException() {
        var tracer = mock(Tracer.class); var builder = mock(Span.Builder.class); var span = mock(Span.class);
        when(tracer.spanBuilder()).thenReturn(builder); when(builder.name(anyString())).thenReturn(builder);
        when(builder.kind(any())).thenReturn(builder); when(builder.start()).thenReturn(span);
        when(span.context()).thenReturn(TraceContext.NOOP);
        var transport = new RecoveryTracingTransport((request, body) -> { throw new IllegalStateException("secret-provider-error"); }, tracer);
        assertThatThrownBy(() -> transport.exchange(new HttpIdentityRecoveryClient.Request(
                URI.create("https://identity.example.invalid/internal/v1/billing/withdrawals/snapshots"), Map.of()), new byte[0]))
                .isInstanceOf(IllegalStateException.class);
        verify(span).tag("outcome", "TRANSPORT_ERROR"); verify(span).end(); verify(span, never()).error(any());
    }
    @Test void cliRejectsUnknownAndDuplicateOptionsAndDoesNotDefaultToApply() {
        assertThat(RecoveryOperatorCli.parse(new String[0])).doesNotContainKey("apply");
        assertThatThrownBy(() -> RecoveryOperatorCli.parse(new String[]{"--action=erase"})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryOperatorCli.parse(new String[]{"--apply", "--apply"})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryOperatorCli.parse(new String[]{"--uri=secret"})).isInstanceOf(IllegalArgumentException.class);
    }
}
