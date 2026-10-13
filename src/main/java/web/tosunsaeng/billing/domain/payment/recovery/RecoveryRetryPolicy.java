package web.tosunsaeng.billing.domain.payment.recovery;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Pure policy; the worker persists its result before dispatch. */
public final class RecoveryRetryPolicy {
    private final DoubleSupplier random;
    public RecoveryRetryPolicy(DoubleSupplier random) { this.random = random; }
    public Instant failed(Instant now, long failures) {
        if (failures < 1) throw new IllegalArgumentException("Invalid retry count");
        if (failures >= 20) return poll(now, 300);
        long cap = 1L << Math.min(6, failures - 1);
        return now.plusMillis(Math.max(1000, (long) (unit() * Math.min(60, cap) * 1000)));
    }
    public Instant poll(Instant now, long seconds) {
        return now.plusMillis((long) (seconds * 1000 * (0.8 + 0.4 * unit())));
    }
    private double unit() {
        double value = random.getAsDouble();
        if (!Double.isFinite(value) || value < 0 || value >= 1) throw new IllegalArgumentException("Invalid jitter");
        return value;
    }
    public record RetryAfter(Instant notBefore, boolean invalid, boolean overOneDay) {}
    /** Invalid/ambiguous values fall back to policy; a valid long wait is never clamped earlier. */
    public static RetryAfter retryAfter(List<String> values, Instant now) {
        if (values == null || values.isEmpty()) return new RetryAfter(null, false, false);
        if (values.size() != 1) return new RetryAfter(null, true, false);
        try {
            String value = values.getFirst();
            Instant until;
            if (value.matches("[0-9]+")) until = now.plusSeconds(Long.parseLong(value));
            else until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            java.util.Date.from(until); // Persistable, otherwise invalid/overflow.
            return new RetryAfter(until, false, until.isAfter(now.plus(Duration.ofDays(1))));
        } catch (RuntimeException invalid) { return new RetryAfter(null, true, false); }
    }
}
