package web.tosunsaeng.billing.domain.payment;

import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.recovery.RecoveryRetryPolicy;
import static org.assertj.core.api.Assertions.*;

class RecoveryRetryPolicyTest {
    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");
    @Test void retryAfterPreservesPastAndOneDayBoundariesUsingOrdinaryClock() {
        var past = RecoveryRetryPolicy.retryAfter(List.of("Sun, 11 Oct 2026 23:59:59 GMT"), now);
        assertThat(past.invalid()).isFalse();
        assertThat(past.notBefore()).isEqualTo(now.minusSeconds(1));
        assertThat(past.overOneDay()).isFalse();
        assertThat(RecoveryRetryPolicy.retryAfter(List.of("0"), now).notBefore()).isEqualTo(now);
        assertThat(RecoveryRetryPolicy.retryAfter(List.of("86400"), now).overOneDay()).isFalse();
        assertThat(RecoveryRetryPolicy.retryAfter(List.of("86401"), now).overOneDay()).isTrue();
        assertThat(RecoveryRetryPolicy.retryAfter(List.of("Tue, 13 Oct 2026 00:00:01 GMT"), now).overOneDay()).isTrue();
        assertThat(RecoveryRetryPolicy.retryAfter(List.of("0", "0"), now).invalid()).isTrue();
    }
    @Test void backoffProbeAndPollAreBoundedWithoutOverflow() {
        var policy = new RecoveryRetryPolicy(() -> 0.5);
        assertThat(policy.failed(now, 1)).isEqualTo(now.plusSeconds(1));
        assertThat(policy.failed(now, 7)).isEqualTo(now.plusSeconds(30));
        assertThat(policy.failed(now, 20)).isEqualTo(now.plusSeconds(300));
        assertThat(policy.failed(now, Long.MAX_VALUE)).isEqualTo(now.plusSeconds(300));
        assertThat(policy.poll(now, 5)).isEqualTo(now.plusSeconds(5));
        assertThat(policy.poll(now, 30)).isEqualTo(now.plusSeconds(30));
    }
    @Test void retryAfterKeepsLongWaitAndRejectsAmbiguousOrOverflow() {
        var value = RecoveryRetryPolicy.retryAfter(List.of("172800"), now);
        assertThat(value.notBefore()).isEqualTo(now.plusSeconds(172800));
        assertThat(value.overOneDay()).isTrue();
        assertThat(value.invalid()).isFalse();
        assertThat(RecoveryRetryPolicy.retryAfter(List.of("Mon, 12 Oct 2026 01:00:00 GMT"), now).notBefore())
                .isEqualTo(now.plusSeconds(3600));
        for (var values : List.of(List.of("1", "2"), List.of("-1"), List.of("999999999999999999999999"), List.of("bad")))
            assertThat(RecoveryRetryPolicy.retryAfter(values, now).invalid()).isTrue();
        assertThat(RecoveryRetryPolicy.retryAfter(List.of(), now).invalid()).isFalse();
    }
}
