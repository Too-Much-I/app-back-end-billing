package web.tosunsaeng.billing.global.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs before public routing; forwarded headers never select a connector. */
public final class WorkloadIngressFilter extends OncePerRequestFilter {
    public static final Map<String, String> ROUTES = Map.of(
            "/internal/v1/payments/accounts/withdrawal/events", "withdrawal-events",
            "/internal/v1/eligibility/trial/events", "trial-eligibility-events",
            "/internal/v1/eligibility/trial/owner/events", "trial-owner-events",
            "/internal/v1/owners/merge/events", "user-merge-events");
    private static final Map<String, String> LC_ROUTES = Map.of(
            "/internal/v1/exam-deletions/events", "exam-deletion-events",
            "/internal/v1/reservations", "reservations",
            "/internal/v1/reservations/status", "reservations",
            "/internal/v1/reservations/continuations/phone", "reservations",
            "/internal/v1/attempt-group-events", "attempt-group-events");
    private static final Pattern LC_LIFECYCLE = Pattern.compile("/internal/v1/reservations/[^/\\\\;%]+/(confirm|cancel)");
    public static String learningCorePurpose(String path) {
        String purpose = LC_ROUTES.get(path);
        return purpose != null ? purpose : LC_LIFECYCLE.matcher(path).matches() ? "reservations" : null;
    }
    private final WorkloadJwtProperties config;
    private final PaymentProperties payments;
    private final OwnerRebindProperties owners;
    private final AttemptGroupEventProperties attempts;
    private final web.tosunsaeng.billing.domain.examdeletion.DeletionProperties deletions;
    public WorkloadIngressFilter(WorkloadJwtProperties config, PaymentProperties payments, OwnerRebindProperties owners,
                                 AttemptGroupEventProperties attempts) {
        this(config, payments, owners, attempts, new web.tosunsaeng.billing.domain.examdeletion.DeletionProperties());
    }
    public WorkloadIngressFilter(WorkloadJwtProperties config, PaymentProperties payments, OwnerRebindProperties owners,
                                 AttemptGroupEventProperties attempts, web.tosunsaeng.billing.domain.examdeletion.DeletionProperties deletions) {
        this.config = config; this.payments = payments; this.owners = owners; this.attempts = attempts; this.deletions = deletions;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean port = config.getPort() > 0 && request.getLocalPort() == config.getPort();
        String identity = ROUTES.get(request.getRequestURI());
        String lc = learningCorePurpose(request.getRequestURI());
        if (identity != null) {
            boolean business = switch (identity) {
                case "withdrawal-events" -> payments.isWithdrawalEnabled();
                case "trial-owner-events", "user-merge-events" -> owners.isEnabled();
                default -> true;
            };
            if (!config.isReceiverEnabled() || !port || !business) { reject(response, 404); return; }
        } else if (lc != null) {
            boolean business = "exam-deletion-events".equals(lc) ? deletions.isEnabled() : "attempt-group-events".equals(lc) ? attempts.isEnabled()
                    : !request.getRequestURI().endsWith("/continuations/phone") || owners.isEnabled();
            if (!config.isLearningCoreReceiverEnabled() || !port || !business) { reject(response, 404); return; }
        }
        if (port && !config.anyReceiverEnabled()) { reject(response, 404); return; }
        chain.doFilter(request, response);
    }
    static void reject(HttpServletResponse response, int status) { response.setHeader("Cache-Control", "no-store"); response.setStatus(status); }
}
