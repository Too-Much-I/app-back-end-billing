package web.tosunsaeng.billing.domain.reservation.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

@Component
public class ReservationLifecycleMetrics {

    private final MeterRegistry meterRegistry;

    public ReservationLifecycleMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String command, Reservation.Kind kind, String outcome) {
        meterRegistry.counter(
                "billing.reservation.lifecycle",
                "action", command,
                "kind", kind == null ? "UNKNOWN" : kind.name(),
                "outcome", outcome
        ).increment();
    }

    public void recordExpiryBatch(int dueCount) {
        meterRegistry.summary("billing.reservation.expiry.batch_size").record(dueCount);
    }

    public void recordOldestDueLag(long lagMillis) {
        meterRegistry.summary("billing.reservation.expiry.oldest_due_lag_millis")
                .record(Math.max(lagMillis, 0));
    }

    public void recordRetryExhausted(String command) {
        meterRegistry.counter(
                "billing.reservation.lifecycle.retry_exhausted", "command", command
        ).increment();
    }
}
