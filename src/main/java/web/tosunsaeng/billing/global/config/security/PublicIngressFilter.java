package web.tosunsaeng.billing.global.config.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;

public class PublicIngressFilter extends OncePerRequestFilter {
    public static final String INVITE = "/api/v1/rewards/invite-shares";
    public static final String REFERRAL_CODE = "/api/v1/rewards/referral-code";
    public static final String REFERRALS = "/api/v1/rewards/referrals";
    public static boolean referral(String path) { return REFERRAL_CODE.equals(path) || REFERRALS.equals(path); }
    public static final String PATH = "/api/v1/entitlements";
    public static final String PRODUCTS = "/api/v1/payments/products";
    public static final String ACCOUNT = "/api/v1/payments/purchase-account";
    public static final String SYNC = "/api/v1/payments/sync";
    public static boolean payment(String path) { return PRODUCTS.equals(path) || ACCOUNT.equals(path) || SYNC.equals(path); }
    public static String method(String path) { return referral(path) || ACCOUNT.equals(path) || SYNC.equals(path) ? "POST" : "GET"; }
    public static String scope(String path) { return referral(path) || ACCOUNT.equals(path) || SYNC.equals(path) ? "billing:purchase" : "billing:read"; }
    public static String scope(String path, String method) {
        return INVITE.equals(path) && "POST".equals(method) ? "billing:purchase" : scope(path);
    }
    private final web.tosunsaeng.billing.domain.reward.InviteRewardProperties rewards;
    private final web.tosunsaeng.billing.domain.reward.ReferralRewardProperties referrals;
    private final EntitlementQueryProperties properties;
    private final PaymentProperties payments;
    private final PublicApiWriter writer;
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer) {
        this(properties, writer, new PaymentProperties());
    }
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer, PaymentProperties payments) {
        this(properties, writer, payments, new web.tosunsaeng.billing.domain.reward.InviteRewardProperties());
    }
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer, PaymentProperties payments,
            web.tosunsaeng.billing.domain.reward.InviteRewardProperties rewards) {
        this(properties, writer, payments, rewards, new web.tosunsaeng.billing.domain.reward.ReferralRewardProperties());
    }
    public PublicIngressFilter(EntitlementQueryProperties properties, PublicApiWriter writer, PaymentProperties payments,
            web.tosunsaeng.billing.domain.reward.InviteRewardProperties rewards,
            web.tosunsaeng.billing.domain.reward.ReferralRewardProperties referrals) {
        this.properties = properties; this.writer = writer; this.payments = payments; this.rewards = rewards;
        this.referrals = referrals;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Local port comes from the connector; forwarded/host/identity headers cannot select a trust boundary.
        boolean publicPort = request.getLocalPort() == properties.getPublicPort();
        String path = request.getRequestURI();
        // The earlier provider guard and its exclusive security chain own this exact route, including OFF rejection.
        if (web.tosunsaeng.billing.domain.payment.provider.RevenueCatWebhookFilter.PATH.equals(path)) { chain.doFilter(request, response); return; }
        if (publicPort || path.startsWith("/api/")) {
            writer.headers(response);
            boolean enabled = referral(path) ? referrals.isEnabled() : INVITE.equals(path) ? rewards.isEnabled() : PATH.equals(path) ? properties.isEnabled()
                    : PRODUCTS.equals(path) ? payments.isCatalogEnabled() : SYNC.equals(path) ? payments.isSyncEnabled()
                    : ACCOUNT.equals(path) && payments.isPurchaseAccountEnabled();
            if (!publicPort || !properties.isPublicConnectorEnabled() || !enabled) {
                writer.error(response, 404, "NOT_FOUND"); return;
            }
            if (!(INVITE.equals(path) ? java.util.Set.of("GET", "POST").contains(request.getMethod()) : method(path).equals(request.getMethod()))) { writer.routeError(request, response, 405, "METHOD_NOT_ALLOWED"); return; }
        }
        chain.doFilter(request, response);
    }
}
