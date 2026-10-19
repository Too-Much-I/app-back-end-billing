package web.tosunsaeng.billing.global.config.security;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
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
        error(response, status, code, 5);
    }
    public void error(HttpServletResponse response, int status, String code, long retryAfter) throws IOException {
        headers(response); response.setStatus(status);
        if (status == 401) { response.setHeader("WWW-Authenticate", "Bearer"); }
        if (status == 403) { response.setHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"billing:read\""); }
        if (status == 405) { response.setHeader("Allow", "GET"); }
        if (status == 429 || status == 503) { response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfter))); }
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
    public void routeError(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        if (!PublicIngressFilter.payment(request.getRequestURI())) { error(response, status, code); return; }
        paymentError(response, status, code, status == 403 ? PublicIngressFilter.scope(request.getRequestURI()) : null,
                PublicIngressFilter.method(request.getRequestURI()), 5);
    }
    public void paymentError(HttpServletResponse response, int status, String code, String scope, String method, long retryAfter) throws IOException {
        headers(response); response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        if (status == 403 && scope != null) response.setHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\"");
        if (status == 405) response.setHeader("Allow", method);
        if (status == 429 || status == 503) response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfter)));
        Object body = status == 503 ? new PublicResponse<>(false, code, "결제 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.", null)
                : "PAYMENT_ACCOUNT_WITHDRAWN".equals(code) ? new PublicResponse<>(false, code, "탈퇴한 계정은 사용할 수 없습니다.", null)
                : PublicResponse.failure(code);
        json.writeValue(response.getOutputStream(), body);
    }
    public void paymentSuccess(HttpServletResponse response, Object value) throws IOException {
        paymentSuccess(response, value, false);
    }
    public void paymentSuccess(HttpServletResponse response, Object value, boolean pending) throws IOException {
        byte[] bytes = json.writeValueAsBytes(PublicResponse.success(value));
        if (bytes.length > 16384) throw web.tosunsaeng.billing.domain.payment.exception.PaymentException.unavailable();
        headers(response); response.setStatus(pending ? 202 : 200);
        if (pending) response.setHeader("Retry-After", "5");
        response.getOutputStream().write(bytes);
    }
}
