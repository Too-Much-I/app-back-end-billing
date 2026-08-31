package web.tosunsaeng.billing.domain.reservation.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

@Component
public class ReserveMetrics {

    private final MeterRegistry meterRegistry;

    public ReserveMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(Reservation.Kind kind, String outcome) {
        meterRegistry.counter(
                "billing.reservation.reserve",
                "kind", kind == null ? "UNKNOWN" : kind.name(),
                "outcome", outcome
        ).increment();
    }

    public void recordInvariantViolation() {
        meterRegistry.counter("billing.reservation.alias_invariant_violations").increment();
    }

    public void recordRetryExhausted() {
        meterRegistry.counter("billing.reservation.transaction_retry_exhausted").increment();
    }
}
