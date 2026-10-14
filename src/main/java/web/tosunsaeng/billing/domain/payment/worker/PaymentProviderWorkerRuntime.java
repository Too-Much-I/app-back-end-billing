package web.tosunsaeng.billing.domain.payment.worker;

import java.util.concurrent.*;
import io.micrometer.core.instrument.MeterRegistry;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncService;

/** Dedicated single-thread scheduler, started only after ApplicationReady. All identifiers stay out of metrics. */
public final class PaymentProviderWorkerRuntime implements AutoCloseable {
    private final Runnable sources, accounts, retention, shutdownTransport;
    private final MeterRegistry metrics;
    private final long pollMillis;
    private final PaymentSyncService sync;
    private ScheduledExecutorService executor;
    private boolean closed;
    public PaymentProviderWorkerRuntime(Runnable sources, Runnable accounts, Runnable shutdownTransport, MeterRegistry metrics, long pollMillis) {
        this(sources, accounts, () -> {}, shutdownTransport, metrics, pollMillis);
    }
    public PaymentProviderWorkerRuntime(Runnable sources, Runnable accounts, Runnable retention, Runnable shutdownTransport, MeterRegistry metrics, long pollMillis) {
        this(sources, accounts, retention, shutdownTransport, metrics, pollMillis, null);
    }
    public PaymentProviderWorkerRuntime(Runnable sources, Runnable accounts, Runnable retention, Runnable shutdownTransport,
                                        MeterRegistry metrics, long pollMillis, PaymentSyncService sync) {
        if (pollMillis < 1000 || pollMillis > 60000) throw new IllegalArgumentException("Invalid provider poll interval");
        this.sources = sources; this.accounts = accounts; this.retention = retention; this.shutdownTransport = shutdownTransport; this.metrics = metrics; this.pollMillis = pollMillis;
        this.sync = sync;
    }
    /** Expose the exact service used by the validated pipeline, never a separately configured API copy. */
    public PaymentSyncService syncService() {
        if (sync == null) throw new IllegalStateException("Provider runtime has no payment sync service");
        return sync;
    }
    public synchronized void start() {
        if (closed || executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "payment-provider"); t.setDaemon(true); return t; });
        executor.scheduleWithFixedDelay(this::tick, 0, pollMillis, TimeUnit.MILLISECONDS);
    }
    public void tick() {
        run("source", sources); run("account", accounts);
        run("retention", retention);
    }
    private void run(String phase, Runnable task) {
        metrics.counter("billing_payment_provider_worker_runs", "phase", phase).increment();
        try { task.run(); } catch (RuntimeException failure) {
            metrics.counter("billing_payment_provider_worker_failures", "phase", phase).increment();
        }
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true;
        if (executor != null) executor.shutdownNow();
        shutdownTransport.run();
    }
}
