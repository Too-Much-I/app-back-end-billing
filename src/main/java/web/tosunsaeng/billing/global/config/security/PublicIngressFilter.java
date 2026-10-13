package web.tosunsaeng.billing.global.config.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;

public class PublicIngressFilter extends OncePerRequestFilter {
    public static final String PATH = "/api/v1/entitlements";
    public static final String PRODUCTS = "/api/v1/payments/products";
    public static final String ACCOUNT = "/api/v1/payments/purchase-account";
    public static final String SYNC = "/api/v1/payments/sync";
    public static boolean payment(String path) { return PRODUCTS.equals(path) || ACCOUNT.equals(path) || SYNC.equals(path); }
    public static String method(String path) { return ACCOUNT.equals(path) || SYNC.equals(path) ? "POST" : "GET"; }
    public static String scope(String path) { return ACCOUNT.equals(path) || SYNC.equals(path) ? "billing:purchase" : "billing:read"; }
    private final EntitlementQueryProperties properties;
    private final PaymentProperties payments;
    private final PublicApiWriter writer;
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer) {
        this(properties, writer, new PaymentProperties());
    }
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer, PaymentProperties payments) {
        this.properties = properties; this.writer = writer; this.payments = payments;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Local port comes from the connector; forwarded/host/identity headers cannot select a trust boundary.
        boolean publicPort = request.getLocalPort() == properties.getPublicPort();
        String path = request.getRequestURI();
        if (publicPort || path.startsWith("/api/")) {
            writer.headers(response);
            boolean enabled = PATH.equals(path) ? properties.isEnabled()
                    : PRODUCTS.equals(path) ? payments.isCatalogEnabled() : SYNC.equals(path) ? payments.isSyncEnabled()
                    : ACCOUNT.equals(path) && payments.isPurchaseAccountEnabled();
            if (!publicPort || !properties.isPublicConnectorEnabled() || !enabled) {
                writer.error(response, 404, "NOT_FOUND"); return;
            }
            if (!method(path).equals(request.getMethod())) { writer.routeError(request, response, 405, "METHOD_NOT_ALLOWED"); return; }
        }
        chain.doFilter(request, response);
    }
}
