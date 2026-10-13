package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.concurrent.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;

/** Own executor avoids suppressing Boot's shared scheduler or blocking recovery scheduling. */
public final class CleanupWorker {
    private final CleanupProcessor processor;
    private final CleanupAlerts alerts;
    private final Clock clock;
    private final MeterRegistry metrics;
    private ScheduledExecutorService executor;
    private Instant nextDiscovery = Instant.MIN;
    private CleanupProcessor.Due cursor;
    private CleanupProcessor.Due recoveryCursor;
    private int processed;
    private boolean round;
    public CleanupWorker(CleanupProcessor processor, CleanupAlerts alerts, Clock clock, MeterRegistry metrics) {
        this.processor = processor; this.alerts = alerts; this.clock = clock; this.metrics = metrics;
    }
    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "payment-cleanup"); thread.setDaemon(true); return thread;
        });
        executor.scheduleWithFixedDelay(this::safeTick, 0, 10, TimeUnit.SECONDS);
    }
    public void safeTick() {
        try { tick(); }
        catch (RuntimeException ignored) { alerts.failure(); }
    }
    public synchronized void tick() {
        metrics.counter("billing_cleanup_heartbeat").increment();
        if (!round && !clock.instant().isBefore(nextDiscovery)) {
            round = true; processed = 0;
            nextDiscovery = clock.instant().plusSeconds(3600);
        }
        long start = System.nanoTime();
        int tickProcessed = 0;
        var visited = new java.util.HashSet<String>();
        // Expired dispatches are recoverable on each tick, not only the next hourly discovery.
        var recoverable = processor.recoverable(recoveryCursor, processor.canPurge() ? 100 : 10);
        if (recoverable.isEmpty()) recoveryCursor = null;
        for (var row : recoverable) {
            if (System.nanoTime() - start >= 30_000_000_000L) break;
            recoveryCursor = row;
            tickProcessed++;
            if (visited.add(row.id())) process(row);
        }
        while (round && processed < 100 && tickProcessed < 100 && System.nanoTime() - start < 30_000_000_000L) {
            var due = processor.due(cursor, 1);
            if (due.isEmpty()) { round = false; cursor = null; break; }
            var row = due.getFirst();
            // Advance the bounded scan even if one malformed candidate cannot be claimed.
            cursor = row; processed++; tickProcessed++;
            if (visited.add(row.id())) process(row);
        }
        if (processed >= 100) round = false;
        alerts.daily();
    }
    private void process(CleanupProcessor.Due row) {
        try {
            var result = processor.processResult(row.id());
            var state = result.state();
            if (state != null) metrics.counter("billing_cleanup_result", "state", state.name()).increment();
            if (result.purge() != null) metrics.counter("billing_cleanup_purge_result", "result", result.purge().name()).increment();
        } catch (RuntimeException ignored) { alerts.failure(); }
    }
    @PreDestroy public synchronized void stop() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }
}
