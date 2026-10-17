package web.tosunsaeng.billing.domain.payment;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import web.tosunsaeng.billing.domain.payment.application.PurchaseTokenLifetime;
import static org.assertj.core.api.Assertions.*;

class PurchaseTokenLifetimeTest {
    final Instant now = Instant.parse("2026-10-17T00:00:00Z");
    Jwt token(Instant expiry) {
        return Jwt.withTokenValue("fake").header("alg", "RS256").issuedAt(now).expiresAt(expiry).build();
    }
    @Test void thirtyMinutesInclusiveAndSixtySecondSkew() {
        assertThat(PurchaseTokenLifetime.deadline(token(now.plusSeconds(1800)), Duration.ofSeconds(60)))
                .isEqualTo(now.plusSeconds(1860));
    }
    @Test void overLimitIsRejectedRatherThanClamped() {
        assertThatThrownBy(() -> PurchaseTokenLifetime.deadline(token(now.plusSeconds(1800).plusNanos(1)), Duration.ZERO))
                .hasMessage("UNAUTHENTICATED");
        assertThatThrownBy(() -> PurchaseTokenLifetime.deadline(token(now.plus(Duration.ofDays(30))), Duration.ZERO))
                .hasMessage("UNAUTHENTICATED");
    }
    @Test void missingIssueTimeAndInvalidConfigurationFailClosed() {
        var missing = Jwt.withTokenValue("fake").header("alg", "RS256").expiresAt(now.plusSeconds(60)).build();
        assertThatThrownBy(() -> PurchaseTokenLifetime.deadline(missing, Duration.ZERO)).hasMessage("UNAUTHENTICATED");
        assertThatThrownBy(() -> PurchaseTokenLifetime.deadline(token(now.plusSeconds(60)), Duration.ofSeconds(61)))
                .hasMessage("BILLING_TEMPORARILY_UNAVAILABLE");
    }
}
