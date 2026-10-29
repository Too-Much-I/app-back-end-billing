package web.tosunsaeng.billing.domain.reward;

import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import com.fasterxml.jackson.core.*;
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
public class ReferralRewardController {
    private final ObjectProvider<ReferralRewardService> service;
    private final ReferralRewardProperties properties;
    private final PaymentRateLimiter limiter;
    private final PublicApiWriter writer;
    private final EntitlementQueryProperties jwt;
    private final PaymentProperties payment;
    private final Clock clock;
    private final MeterRegistry metrics;
    public ReferralRewardController(ObjectProvider<ReferralRewardService> service, ReferralRewardProperties properties,
            PaymentRateLimiter limiter, PublicApiWriter writer, EntitlementQueryProperties jwt,
            PaymentProperties payment, Clock clock, MeterRegistry metrics) {
        this.service = service; this.properties = properties; this.limiter = limiter; this.writer = writer;
        this.jwt = jwt; this.payment = payment; this.clock = clock; this.metrics = metrics;
    }
    @PostMapping(PublicIngressFilter.REFERRAL_CODE)
    public void issue(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(false, principal, request, response);
    }
    @PostMapping(PublicIngressFilter.REFERRALS)
    public void apply(@AuthenticationPrincipal Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(true, principal, request, response);
    }
    private void handle(boolean apply, Jwt principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String outcome = "SUCCESS";
        long began = System.nanoTime();
        try {
            if (principal == null) throw new PaymentException(401, "UNAUTHENTICATED");
            if (!"MEMBER".equals(principal.getClaims().get("account_type"))) throw new PaymentException(403, "FORBIDDEN");
            if (!properties.isEnabled()) throw new PaymentException(503, "REFERRAL_TEMPORARILY_UNAVAILABLE");
            if (request.getQueryString() != null) throw new PaymentException(400, "INVALID_REQUEST");
            String operationId = null, code = null;
            if (apply) {
                var values = Collections.list(request.getHeaders("Idempotency-Key"));
                if (values.size() != 1 || !values.getFirst().matches(ReferralRewardService.UUID_V4))
                    throw new PaymentException(400, "INVALID_REQUEST");
                operationId = values.getFirst();
                code = decode(request);
            } else if (request.getInputStream().read() != -1) throw new PaymentException(400, "INVALID_REQUEST");
            long retry = limiter.acquire(apply ? PaymentRateLimiter.Route.REFERRAL_APPLY : PaymentRateLimiter.Route.REFERRAL_CODE, principal.getSubject());
            if (retry > 0) { outcome = "RATE_LIMITED"; writer.referralError(response, 429, outcome, null, retry); return; }
            var implementation = service.getIfAvailable();
            if (implementation == null) throw PaymentException.unavailable();
            var evidence = VerifiedMemberEvidence.fromAuthenticated(principal, jwt.getIssuer(), payment.getEnvironment(), jwt.getClockSkew(), clock.instant());
            writer.paymentSuccess(response, apply ? implementation.apply(evidence, operationId, code) : implementation.issue(evidence));
        } catch (RuntimeException error) {
            int status = error instanceof PaymentException p ? p.status() : 503;
            outcome = error instanceof PaymentException p ? p.code() : "BILLING_TEMPORARILY_UNAVAILABLE";
            writer.referralError(response, status, outcome, null, 5);
        } finally {
            metrics.timer("billing.referral.reward.request", "operation", apply ? "apply" : "code", "outcome", outcome)
                    .record(System.nanoTime() - began, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
    private String decode(HttpServletRequest request) throws IOException {
        try {
            var media = org.springframework.http.MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType());
            if (!"application".equalsIgnoreCase(media.getType()) || !"json".equalsIgnoreCase(media.getSubtype())
                    || media.getCharset() != null && !java.nio.charset.StandardCharsets.UTF_8.equals(media.getCharset()))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new PaymentException(415, "UNSUPPORTED_MEDIA_TYPE"); }
        byte[] bytes = request.getInputStream().readNBytes(1025);
        if (bytes.length > 1024) throw new PaymentException(413, "PAYLOAD_TOO_LARGE");
        String text;
        try { text = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
        catch (java.nio.charset.CharacterCodingException malformed) { throw new PaymentException(400, "INVALID_REQUEST"); }
        try (var parser = new JsonFactory().createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT || parser.nextToken() != JsonToken.FIELD_NAME
                    || !"code".equals(parser.currentName()) || parser.nextToken() != JsonToken.VALUE_STRING)
                throw new PaymentException(400, "INVALID_REQUEST");
            String code = ReferralRewardService.normalize(parser.getText());
            if (parser.nextToken() != JsonToken.END_OBJECT || parser.nextToken() != null) throw new PaymentException(400, "INVALID_REQUEST");
            return code;
        } catch (JsonProcessingException malformed) { throw new PaymentException(400, "INVALID_REQUEST"); }
    }
}
