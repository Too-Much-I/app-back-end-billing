package web.tosunsaeng.billing.domain.payment;

import java.time.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import web.tosunsaeng.billing.domain.payment.api.*;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalService;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PaymentWithdrawalObservabilityTest {
    @Test @SuppressWarnings("unchecked") void spanEndsForDecodeBusinessSuccessAndFailureWithoutIdentifiers() {
        var tracer = mock(Tracer.class);
        var provider = (ObjectProvider<Tracer>) mock(ObjectProvider.class);
        var builder = mock(Span.Builder.class);
        var span = mock(Span.class);
        when(provider.getIfAvailable()).thenReturn(tracer);
        when(tracer.spanBuilder()).thenReturn(builder);
        when(builder.name(PaymentWithdrawalController.SPAN_NAME)).thenReturn(builder);
        when(builder.start()).thenReturn(span);
        when(span.context()).thenReturn(mock(TraceContext.class));
        var service = mock(PaymentWithdrawalService.class);
        var props = new PaymentProperties(); props.setWithdrawalEnabled(true);
        Clock clock = Clock.fixed(PaymentWithdrawalDecoderTest.NOW, ZoneOffset.UTC);
        var controller = new PaymentWithdrawalController(new PaymentWithdrawalDecoder(clock), service, props, provider, new SimpleMeterRegistry(), clock);
        when(service.process(any())).thenReturn(PaymentWithdrawalService.Outcome.APPLIED);
        assertThat(controller.receive(request(PaymentWithdrawalDecoderTest.payload(clock.instant().toString()))).getStatusCode().value()).isEqualTo(204);
        when(service.process(any())).thenThrow(new IllegalStateException("secret-payload"));
        assertThatThrownBy(() -> controller.receive(request(PaymentWithdrawalDecoderTest.payload(clock.instant().toString()))))
                .hasMessage("Billing is temporarily unavailable.");
        assertThatThrownBy(() -> controller.receive(request("{}"))).hasMessage("The request is invalid.");
        verify(builder, times(3)).name(PaymentWithdrawalController.SPAN_NAME);
        verify(span, times(3)).end();
        verify(span, never()).tag(anyString(), anyString());
        var error = org.mockito.ArgumentCaptor.forClass(Throwable.class);
        verify(span).error(error.capture());
        assertThat(error.getValue().getMessage()).isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(new PaymentWithdrawalDecoder(clock).decode(PaymentWithdrawalDecoderTest.payload(clock.instant().toString()).getBytes()).toString())
                .doesNotContain(PaymentWithdrawalDecoderTest.USER, PaymentWithdrawalDecoderTest.EVENT);
    }
    private static MockHttpServletRequest request(String body) {
        var request = new MockHttpServletRequest("POST", PaymentWithdrawalController.PATH);
        request.setContentType("application/json"); request.setContent(body.getBytes()); return request;
    }
}
