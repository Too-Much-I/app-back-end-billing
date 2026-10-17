package web.tosunsaeng.billing.domain.payment.application;

import java.time.*;
import org.springframework.security.oauth2.jwt.Jwt;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;

/** Additional purchase-only bound after signature/issuer/audience/scope validation. */
public final class PurchaseTokenLifetime {
    private PurchaseTokenLifetime() {}
    public static Instant deadline(Jwt jwt, Duration skew) {
        if (skew == null || skew.isNegative() || skew.compareTo(Duration.ofSeconds(60)) > 0)
            throw PaymentException.unavailable();
        try {
            if (jwt == null || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null)
                throw new IllegalArgumentException();
            Duration lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
            if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(30)) > 0)
                throw new IllegalArgumentException();
            return jwt.getExpiresAt().plus(skew);
        } catch (RuntimeException invalid) { throw new PaymentException(401, "UNAUTHENTICATED"); }
    }
}
