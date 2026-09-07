package web.tosunsaeng.billing.global.config.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;

public class PublicIngressFilter extends OncePerRequestFilter {
    public static final String PATH = "/api/v1/entitlements";
    private final EntitlementQueryProperties properties;
    private final PublicApiWriter writer;
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer) {
        this.properties = properties; this.writer = writer;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Local port comes from the connector; forwarded/host/identity headers cannot select a trust boundary.
        boolean publicPort = request.getLocalPort() == properties.getPublicPort();
        String path = request.getRequestURI();
        if (publicPort || path.startsWith("/api/")) {
            writer.headers(response);
            if (!publicPort || !properties.isPublicConnectorEnabled() || !properties.isEnabled() || !PATH.equals(path)) {
                writer.error(response, 404, "NOT_FOUND"); return;
            }
            if (!"GET".equals(request.getMethod())) { writer.error(response, 405, "METHOD_NOT_ALLOWED"); return; }
        }
        chain.doFilter(request, response);
    }
}
