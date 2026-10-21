package web.tosunsaeng.billing.domain.reward;

import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.global.config.security.*;

@RestController
public class InviteRewardController {
    private final ObjectProvider<InviteRewardService> service;
    private final InviteRewardProperties properties;
    private final PaymentRateLimiter limiter;
    private final PublicApiWriter writer;
    private final EntitlementQueryProperties jwt;
    private final PaymentProperties payment;
    private final Clock clock;
    private final MeterRegistry metrics;
    public InviteRewardController(ObjectProvider<InviteRewardService> service, InviteRewardProperties properties,
            PaymentRateLimiter limiter, PublicApiWriter writer, EntitlementQueryProperties jwt,
            PaymentProperties payment, Clock clock, MeterRegistry metrics) {
        this.service = service; this.properties = properties; this.limiter = limiter; this.writer = writer;
        this.jwt = jwt; this.payment = payment; this.clock = clock; this.metrics = metrics;
    }
    @GetMapping(PublicIngressFilter.INVITE)
    public void get(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(false, principal, request, response);
    }
    @PostMapping(PublicIngressFilter.INVITE)
    public void post(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(true, principal, request, response);
    }
    private void handle(boolean award, Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String outcome = "SUCCESS";
        long began = System.nanoTime();
        try {
            if (!properties.isEnabled()) throw new PaymentException(404, "NOT_FOUND");
            if (principal == null) throw new PaymentException(401, "UNAUTHENTICATED");
            if (!"MEMBER".equals(principal.getClaims().get("account_type"))) throw new PaymentException(403, "FORBIDDEN");
            if (request.getQueryString() != null || request.getInputStream().read() != -1) throw new PaymentException(400, "INVALID_REQUEST");
            String operationId = null;
            if (award) {
                var values = Collections.list(request.getHeaders("Idempotency-Key"));
                if (values.size() != 1 || !values.getFirst().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
                    throw new PaymentException(400, "INVALID_REQUEST");
                operationId = values.getFirst();
            }
            long retry = limiter.acquire(award ? PaymentRateLimiter.Route.INVITE_AWARD : PaymentRateLimiter.Route.INVITE_QUERY, principal.getSubject());
            if (retry > 0) {
                outcome = "RATE_LIMITED"; writer.inviteError(response, 429, outcome, null, retry); return;
            }
            var implementation = service.getIfAvailable();
            if (implementation == null) throw PaymentException.unavailable();
            Object result = award ? implementation.award(VerifiedMemberEvidence.fromAuthenticated(principal, jwt.getIssuer(),
                    payment.getEnvironment(), jwt.getClockSkew(), clock.instant()), operationId) : implementation.read(principal.getSubject());
            writer.paymentSuccess(response, result);
        } catch (RuntimeException error) {
            int status = error instanceof PaymentException p ? p.status() : 503;
            outcome = error instanceof PaymentException p ? p.code() : "BILLING_TEMPORARILY_UNAVAILABLE";
            writer.inviteError(response, status, outcome, null, 5);
        } finally {
            metrics.timer("billing.invite.reward.request", "operation", award ? "award" : "query", "outcome", outcome)
                    .record(System.nanoTime() - began, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
