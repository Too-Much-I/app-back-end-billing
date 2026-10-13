package web.tosunsaeng.billing.global.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;

/** Runs before public routing; forwarded headers never select a connector. */
public final class WorkloadIngressFilter extends OncePerRequestFilter {
    public static final Map<String, String> ROUTES = Map.of(
            "/internal/v1/payments/accounts/withdrawal/events", "withdrawal-events",
            "/internal/v1/eligibility/trial/events", "trial-eligibility-events",
            "/internal/v1/eligibility/trial/owner/events", "trial-owner-events",
            "/internal/v1/owners/merge/events", "user-merge-events");
    private final WorkloadJwtProperties config;
    private final PaymentProperties payments;
    private final OwnerRebindProperties owners;
    public WorkloadIngressFilter(WorkloadJwtProperties config, PaymentProperties payments, OwnerRebindProperties owners) {
        this.config = config; this.payments = payments; this.owners = owners;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean port = config.getPort() > 0 && request.getLocalPort() == config.getPort();
        String purpose = ROUTES.get(request.getRequestURI());
        if (purpose != null && (!config.isReceiverEnabled() || !port)) { reject(response, 404); return; }
        if (port) {
            boolean enabled = purpose != null && switch (purpose) {
                case "withdrawal-events" -> payments.isWithdrawalEnabled();
                case "trial-owner-events", "user-merge-events" -> owners.isEnabled();
                default -> true;
            };
            if (!config.isReceiverEnabled() || (purpose != null && !enabled)) { reject(response, 404); return; }
        }
        chain.doFilter(request, response);
    }
    static void reject(HttpServletResponse response, int status) { response.setHeader("Cache-Control", "no-store"); response.setStatus(status); }
}
