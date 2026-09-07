package web.tosunsaeng.billing.global.config.security;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;
import web.tosunsaeng.billing.global.response.PublicResponse;

@Component
public class PublicApiWriter {
    private final ObjectMapper json;
    private final TraceCorrelation trace;
    public PublicApiWriter(ObjectMapper json, TraceCorrelation trace) { this.json = json; this.trace = trace; }
    public void headers(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Trace-Id", trace.currentTraceId());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
    }
    public void error(HttpServletResponse response, int status, String code) throws IOException {
        headers(response); response.setStatus(status);
        if (status == 401) { response.setHeader("WWW-Authenticate", "Bearer"); }
        if (status == 403) { response.setHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"billing:read\""); }
        if (status == 405) { response.setHeader("Allow", "GET"); }
        if (status == 429 || status == 503) { response.setHeader("Retry-After", "5"); }
        json.writeValue(response.getOutputStream(), PublicResponse.failure(code));
    }
    public void success(HttpServletResponse response, Object value, boolean pending) throws IOException {
        byte[] bytes = json.writeValueAsBytes(PublicResponse.success(value));
        if (bytes.length > 65536) {
            throw new web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException(
                    web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.Failure.LIMIT);
        }
        headers(response); response.setStatus(200);
        if (pending) { response.setHeader("Retry-After", "5"); }
        response.getOutputStream().write(bytes);
    }
}
