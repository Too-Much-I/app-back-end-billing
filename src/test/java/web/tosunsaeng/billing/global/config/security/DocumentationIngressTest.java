package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.reward.*;
import static org.assertj.core.api.Assertions.*;

class DocumentationIngressTest {
    @Test void offProductionWrongPortAndWriteMethodsAreRejected() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            assertThat(call(path, false, "SANDBOX", 8083, "GET")).isEqualTo(404);
            assertThat(call(path, true, "PRODUCTION", 8083, "GET")).isEqualTo(404);
            assertThat(call(path, true, "SANDBOX", 8082, "GET")).isEqualTo(404);
            assertThat(call(path, true, "SANDBOX", 8443, "GET")).isEqualTo(404);
            assertThat(call(path, true, "SANDBOX", 8083, "POST")).isEqualTo(404);
            assertThat(call(path, true, "SANDBOX", 8083, "GET")).isEqualTo(204);
        }
        assertThat(call("/v3/api-docs/internal", true, "SANDBOX", 8083, "GET")).isEqualTo(404);
        assertThat(call("/actuator/env", true, "SANDBOX", 8083, "GET")).isEqualTo(404);
    }
    int call(String path, boolean enabled, String environment, int port, String method) throws Exception {
        var p = new EntitlementQueryProperties(); p.setPublicConnectorEnabled(true);
        var payments = new PaymentProperties(); payments.setEnvironment(environment);
        var filter = new PublicIngressFilter(p, new PublicApiWriter(new ObjectMapper(),
                org.mockito.Mockito.mock(web.tosunsaeng.billing.global.observability.TraceCorrelation.class)), payments,
                new InviteRewardProperties(), new ReferralRewardProperties(), enabled);
        var request = new MockHttpServletRequest(method, path); request.setLocalPort(port);
        request.addHeader("X-Forwarded-Port", "8083");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        return response.getStatus();
    }
}
