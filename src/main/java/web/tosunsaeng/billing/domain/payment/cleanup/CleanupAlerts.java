package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.*;
import org.bson.Document;
import io.micrometer.core.instrument.MeterRegistry;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** PR1 transport-neutral, privacy-safe summaries. No Slack client or per-user alert history. */
public final class CleanupAlerts {
    public enum Kind { DAILY_SUMMARY, INCIDENT, RECOVERY }
    public enum Cause { REVIEW, WORKER_FAILURE, ALERT_FAILURE }
    public record Summary(String environment, Kind kind, Cause reason, Instant asOf,
                          long count, long oldestAgeHours, long overdueCount, String runbookKey) {}
    @FunctionalInterface public interface Sink { boolean send(Summary summary); }
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final PaymentStore store;
    private final String environment;
    private final Clock clock;
    private final Sink sink;
    private final MeterRegistry metrics;
    private final boolean enabled;
    private LocalDate lastDaily;
    private Instant lastIncident;
    private Instant nextDailyAttempt = Instant.MIN;
    private Instant nextIncidentAttempt = Instant.MIN;
    private Instant nextRecoveryAttempt = Instant.MIN;
    public CleanupAlerts(PaymentStore store, String environment, Clock clock, Sink sink, MeterRegistry metrics) {
        this(store, environment, clock, sink, metrics, true);
    }
    public CleanupAlerts(PaymentStore store, String environment, Clock clock, Sink sink, MeterRegistry metrics, boolean enabled) {
        this.store = store; this.environment = environment; this.clock = clock; this.sink = sink; this.metrics = metrics;
        this.enabled = enabled;
    }
    public synchronized Summary snapshot() {
        var jobs = store.database().getCollection(CleanupSchema.COLLECTION);
        var key = new Document("environment", environment).append("state", "BLOCKED");
        long count = jobs.countDocuments(key);
        long overdue = jobs.countDocuments(new Document(key).append("cleanup.reviewDueAt", new Document("$lte", Date.from(clock.instant()))));
        var first = jobs.find(key).sort(new Document("cleanup.firstUnresolvedAt", 1)).limit(1).first();
        long age = first == null ? 0 : Math.max(0, Duration.between(first.get("cleanup", Document.class)
                .getDate("firstUnresolvedAt").toInstant(), clock.instant()).toHours());
        return new Summary(environment, Kind.DAILY_SUMMARY, Cause.REVIEW, clock.instant(), count, age, overdue, "TMI-209");
    }
    public synchronized void daily() {
        if (!enabled) return;
        var now = clock.instant().atZone(SEOUL);
        if (now.getHour() < 9 || now.toLocalDate().equals(lastDaily) || clock.instant().isBefore(nextDailyAttempt)) return;
        nextDailyAttempt = clock.instant().plusSeconds(3600);
        if (deliver(snapshot())) lastDaily = now.toLocalDate();
    }
    public synchronized void failure() {
        metrics.counter("billing_cleanup_failure").increment();
        if (!enabled) return;
        if (clock.instant().isBefore(nextIncidentAttempt)) return;
        if (lastIncident != null && clock.instant().isBefore(lastIncident.plusSeconds(3600))) return;
        nextIncidentAttempt = clock.instant().plusSeconds(3600);
        if (deliver(new Summary(environment, Kind.INCIDENT, Cause.WORKER_FAILURE, clock.instant(), 0, 0, 0, "TMI-209")))
            lastIncident = clock.instant();
    }
    public synchronized void recovered() {
        if (!enabled || lastIncident == null || clock.instant().isBefore(nextRecoveryAttempt)) return;
        nextRecoveryAttempt = clock.instant().plusSeconds(3600);
        if (lastIncident != null && deliver(new Summary(environment, Kind.RECOVERY, Cause.WORKER_FAILURE,
                clock.instant(), 0, 0, 0, "TMI-209"))) lastIncident = null;
    }
    private boolean deliver(Summary summary) {
        try {
            boolean sent = sink.send(summary);
            if (!sent) metrics.counter("billing_cleanup_alert_failure").increment();
            return sent;
        }
        catch (RuntimeException ignored) {
            metrics.counter("billing_cleanup_alert_failure").increment();
            return false; // Never log raw sink exceptions or make business state depend on delivery.
        }
    }
}
