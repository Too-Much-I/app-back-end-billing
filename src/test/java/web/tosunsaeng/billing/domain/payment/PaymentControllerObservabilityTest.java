package web.tosunsaeng.billing.domain.payment;

import java.time.Clock;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import web.tosunsaeng.billing.domain.payment.api.PaymentController;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.dto.response.PaymentProductsResponse;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.global.config.security.PublicApiWriter;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PaymentControllerObservabilityTest {
    @Test @SuppressWarnings("unchecked")
    void actualControllerClosesItsInternalSpanOnSuccessAndFailureWithoutSensitiveAttributes() throws Exception {
        var catalog = mock(PaymentCatalogService.class);
        var tracer = mock(Tracer.class);
        var provider = (ObjectProvider<Tracer>) mock(ObjectProvider.class);
        var span = mock(Span.class);
        var context = mock(TraceContext.class);
        when(provider.getIfAvailable()).thenReturn(tracer);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("payment_products")).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.spanId()).thenReturn("fake-span");
        var writer = new PublicApiWriter(new ObjectMapper(), mock(TraceCorrelation.class));
        var controller = new PaymentController(catalog, mock(PaymentAccountService.class), mock(PaymentRateLimiter.class),
                writer, new EntitlementQueryProperties(), new PaymentProperties(), Clock.systemUTC(), provider, new SimpleMeterRegistry());
        var principal = PaymentControllerTest.token("GUEST", "billing:read");
        when(catalog.read()).thenReturn(new PaymentProductsResponse(1, List.of()));
        controller.products(principal, new MockHttpServletRequest(), new MockHttpServletResponse());
        when(catalog.read()).thenThrow(new IllegalStateException("sensitive-provider-payload"));
        var response = new MockHttpServletResponse();
        controller.products(principal, new MockHttpServletRequest(), response);
        verify(span, times(2)).name("payment_products");
        verify(span, times(2)).end();
        verify(span, never()).tag(anyString(), anyString());
        var error = org.mockito.ArgumentCaptor.forClass(Throwable.class);
        verify(span).error(error.capture());
        assertThat(error.getValue().getMessage()).isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(response.getContentAsString()).doesNotContain("sensitive-provider-payload", principal.getSubject());
    }
}
