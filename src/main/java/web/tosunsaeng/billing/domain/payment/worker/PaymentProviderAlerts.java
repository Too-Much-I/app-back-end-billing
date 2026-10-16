package web.tosunsaeng.billing.domain.payment.worker;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;

/** Privacy-safe operational summaries. A channel-specific transport must be explicitly supplied to enable delivery. */
public final class PaymentProviderAlerts {
    public enum Kind { DAILY_REVIEW, AUTH_INCIDENT, AUTH_RECOVERY, REFUND_DAILY, REFUND_AUTH, REFUND_DELAY, REFUND_RECOVERY }
    public record Summary(String environment, Kind kind, Instant asOf, long pending, long review, long cases,
                          long oldestPendingHours, long overdueReview, String runbookKey) {}
    @FunctionalInterface public interface Sink { boolean send(Summary summary); }
    private final PaymentStore store;
    private final String environment;
    private final Clock clock;
    private final BooleanSupplier authBlocked;
    private final Sink sink;
    private final boolean enabled;
    private final MeterRegistry metrics;
    private final Map<String, AtomicLong> values = new LinkedHashMap<>();
    private LocalDate lastDaily;
    private Instant nextAuth = Instant.MIN, nextDaily = Instant.MIN;
    private boolean authIncidentSent;
    public PaymentProviderAlerts(PaymentStore store, String environment, Clock clock, BooleanSupplier authBlocked,
                                 MeterRegistry metrics, boolean enabled, Sink sink) {
        if (enabled && sink == null) throw new IllegalStateException("Provider alerts require an explicit authorized delivery sink");
        this.store = store; this.environment = environment; this.clock = clock; this.authBlocked = authBlocked;
        this.metrics = metrics; this.enabled = enabled; this.sink = sink;
        for (String key : List.of("pending", "review", "cases", "oldest_pending_hours", "overdue_review", "auth_blocked", "sample_epoch_seconds")) {
            var value = new AtomicLong(); values.put(key, value); metrics.gauge("billing_payment_provider_" + key, value);
        }
    }
    public synchronized void poll() {
        Instant now = clock.instant();
        var sync = store.database().getCollection(PaymentSyncSchema.COLLECTION);
        var cases = store.database().getCollection("payment_purchase_cases");
        Document base = new Document("environment", environment);
        Document pending = new Document(base).append("state", "PENDING");
        Document review = new Document(base).append("state", new Document("$in", List.of("REVIEW_REQUIRED", "BLOCKED_AUTH")));
        Document first = sync.find(pending).sort(new Document("createdAt", 1)).limit(1).first();
        long pendingCount = sync.countDocuments(pending), reviewCount = sync.countDocuments(review), caseCount = cases.countDocuments(base);
        long overdue = sync.countDocuments(new Document(review).append("updatedAt", new Document("$lte", Date.from(now.minus(Duration.ofDays(7))))));
        long age = first == null ? 0 : Math.max(0, Duration.between(first.getDate("createdAt").toInstant(), now).toHours());
        boolean blocked = authBlocked.getAsBoolean();
        values.get("pending").set(pendingCount); values.get("review").set(reviewCount); values.get("cases").set(caseCount);
        values.get("oldest_pending_hours").set(age); values.get("overdue_review").set(overdue); values.get("auth_blocked").set(blocked ? 1 : 0);
        values.get("sample_epoch_seconds").set(now.getEpochSecond());
        if (!enabled) return;
        if (blocked && !now.isBefore(nextAuth)) {
            nextAuth = now.plusSeconds(3600);
            if (send(new Summary(environment, Kind.AUTH_INCIDENT, now, pendingCount, reviewCount, caseCount, age, overdue, "TMI-212"))) authIncidentSent = true;
        } else if (!blocked && authIncidentSent && !now.isBefore(nextAuth)) {
            nextAuth = now.plusSeconds(3600);
            if (send(new Summary(environment, Kind.AUTH_RECOVERY, now, pendingCount, reviewCount, caseCount, age, overdue, "TMI-212"))) authIncidentSent = false;
        }
        var local = now.atZone(ZoneId.of("Asia/Seoul"));
        if (local.getHour() >= 9 && !local.toLocalDate().equals(lastDaily) && !now.isBefore(nextDaily)) {
            nextDaily = now.plusSeconds(3600);
            if (send(new Summary(environment, Kind.DAILY_REVIEW, now, pendingCount, reviewCount, caseCount, age, overdue, "TMI-212"))) lastDaily = local.toLocalDate();
        }
    }
    private boolean send(Summary summary) {
        try {
            if (sink.send(summary)) return true;
        } catch (RuntimeException ignored) { /* Do not log transport exceptions or change financial state. */ }
        metrics.counter("billing_payment_provider_alert_delivery_failed").increment(); return false;
    }
}
