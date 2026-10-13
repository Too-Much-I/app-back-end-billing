package web.tosunsaeng.billing.domain.payment.recovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Low-cardinality alert signals; external routing/watchdog deployment remains an operations gate. */
@Component
public final class PaymentRecoveryMonitor {
    private record Alarm(int severity, Instant emittedAt) {}
    private final Map<String, Alarm> alarms = new HashMap<>();
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final AtomicLong heartbeat = new AtomicLong();
    private volatile boolean ready;
    public PaymentRecoveryMonitor(PaymentStore store, PaymentProperties properties, Clock clock, MeterRegistry metrics) {
        this.store = store; this.properties = properties; this.clock = clock; this.metrics = metrics;
        metrics.gauge("billing.recovery.monitor.heartbeat.seconds", heartbeat);
    }
    @EventListener(ApplicationReadyEvent.class) public void ready() { ready = true; }
    @Scheduled(fixedDelay = 60000) public void tick() {
        if (!ready) return;
        sample();
    }
    public synchronized void sample() {
        var flags = properties.getRecovery();
        if (!flags.isFeedEnabled() && !flags.isSnapshotWorkerEnabled() && !flags.isRetentionEnabled()) return;
        try {
            Document scope = new Document("environment", properties.getEnvironment()).append("consumer", "billing");
            Document c = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(scope).first();
            if (c == null) { signal("CONTROL_MISSING", 2); return; }
            signal("CONTROL_MISSING", 0);
            Document feed = c.get("feedJob", Document.class), snapshot = c.get("snapshotJob", Document.class);
            String fs = feed == null ? "" : feed.getString("state");
            signal("AUTH", "BLOCKED_AUTH".equals(fs) || code(snapshot, "AUTH") ? 2 : 0);
            signal("CONTRACT", "BLOCKED_CONTRACT".equals(fs) || code(snapshot, "CONTRACT") ? 2 : 0);
            signal("SOURCE_LOSS", Set.of("SNAPSHOT_REQUIRED", "STREAM_REPLACED").contains(fs) ? 2 : 0);
            signal("RUN_BLOCKED", snapshot != null && snapshot.getBoolean("blocked", false) ? 2 : 0);
            signal("SNAPSHOT_FAILED", snapshot != null
                    && Set.of("FAILED", "EXPIRED").contains(snapshot.getString("state"))
                    && !Set.of("SNAPSHOT_BUILD_TIMEOUT", "SOURCE_EXPIRED").contains(Objects.toString(snapshot.get("failureCode"), "")) ? 2 : 0);
            signal("BUILD_OVERDUE", snapshot != null && "POLLING".equals(snapshot.getString("state"))
                    && snapshot.getBoolean("buildOverdue", false) ? 1 : 0);
            signal("CIRCUIT", "CIRCUIT".equals(fs) || (snapshot != null && snapshot.get("failures") instanceof Long
                    && snapshot.getLong("failures") >= 20 && !"COMPLETE".equals(snapshot.getString("state"))) ? 1 : 0);
            long lag = 0;
            if (feed != null && flags.isFeedEnabled()) {
                Date last = feed.getDate("lastSuccessAt");
                if (last == null) last = feed.getDate("scanStartedAt");
                lag = Math.max(0, Duration.between(last.toInstant(), clock.instant()).toSeconds());
            }
            signal("FEED_NO_SUCCESS", lag >= 1800 ? 2 : lag >= 300 ? 1 : 0);
            boolean longWait = longWait(feed) || longWait(snapshot);
            signal("LONG_RETRY_AFTER", longWait ? 1 : 0);
            boolean overdue = false;
            for (String collection : List.of(WithdrawalSnapshotProcessor.RUNS, WithdrawalSnapshotProcessor.ROWS,
                    WithdrawalSnapshotProcessor.PAGES, WithdrawalSnapshotProcessor.OPERATIONS)) {
                String expiry = collection.equals(WithdrawalSnapshotProcessor.OPERATIONS) ? "retentionExpiresAt" : "expiresAt";
                overdue |= store.database().getCollection(collection).find(new Document(scope)
                                .append(expiry, new Document("$lte", Date.from(clock.instant()))))
                        .sort(new Document(expiry, 1).append("_id", 1)).first() != null;
            }
            signal("TEMPORARY_DATA_OVERDUE", overdue ? 2 : 0);
            signal("MONITOR_FAILURE", 0); heartbeat.set(clock.instant().getEpochSecond());
        } catch (RuntimeException failure) { signal("MONITOR_FAILURE", 2); }
    }
    private boolean longWait(Document job) {
        return job != null && job.get("nextAttemptAt") instanceof Date
                && job.getDate("nextAttemptAt").toInstant().isAfter(clock.instant().plus(Duration.ofDays(1)));
    }
    private static boolean code(Document job, String code) { return job != null && code.equals(job.getString("failureCode")); }
    private void signal(String reason, int severity) {
        Alarm previous = alarms.get(reason);
        if (severity == 0) {
            if (previous != null) { metrics.counter("billing.recovery.alert", "reason", reason, "severity", "RESOLVED").increment(); alarms.remove(reason); }
        } else if (previous == null || severity > previous.severity()
                || !clock.instant().isBefore(previous.emittedAt().plusSeconds(3600))) {
            metrics.counter("billing.recovery.alert", "reason", reason, "severity", severity == 2 ? "CRITICAL" : "WARNING").increment();
            alarms.put(reason, new Alarm(severity, clock.instant()));
        }
    }
}
