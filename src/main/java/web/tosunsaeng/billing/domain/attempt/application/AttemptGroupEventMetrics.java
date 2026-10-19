package web.tosunsaeng.billing.domain.attempt.application;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;

@Component
public class AttemptGroupEventMetrics {

    private static final String SERVICE = "billing";
    private static final String OPERATION = "attempt_group_event_consume";

    private final MeterRegistry meterRegistry;

    public AttemptGroupEventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(
            AttemptGroupStatusEvent event,
            String outcome,
            long durationNanos,
            Instant receivedAt
    ) {
        String normalizedOutcome = outcome.toLowerCase(java.util.Locale.ROOT);
        String target = event.targetStatus().name();
        Counter.builder("billing.attempt_group.events")
                .tags("service", SERVICE, "operation", OPERATION,
                        "outcome", normalizedOutcome, "targetStatus", target)
                .register(meterRegistry)
                .increment();
        Timer.builder("billing.attempt_group.consume.duration")
                .tags("service", SERVICE, "operation", OPERATION,
                        "outcome", normalizedOutcome, "targetStatus", target)
                .register(meterRegistry)
                .record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);

        long rawAge = Duration.between(event.occurredAt(), receivedAt).toMillis();
        if (rawAge < 0) {
            Counter.builder("billing.attempt_group.clock_skew")
                    .tags("service", SERVICE, "operation", OPERATION,
                            "outcome", "future_event")
                    .register(meterRegistry)
                    .increment();
        }
        DistributionSummary.builder("billing.attempt_group.event.age")
                .baseUnit("milliseconds")
                .tags("service", SERVICE, "operation", OPERATION,
                        "outcome", normalizedOutcome, "targetStatus", target)
                .register(meterRegistry)
                .record(Math.max(0, rawAge));
    }

    public void recordRejected(String outcome) {
        Counter.builder("billing.attempt_group.events")
                .tags("service", SERVICE, "operation", OPERATION,
                        "outcome", outcome.toLowerCase(java.util.Locale.ROOT),
                        "targetStatus", "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordTransactionRetryExhausted() {
        Counter.builder("billing.attempt_group.transaction.retry_exhausted")
                .tags("service", SERVICE, "operation", OPERATION)
                .register(meterRegistry)
                .increment();
    }

    public void recordTerminalConflict() {
        Counter.builder("billing.attempt_group.terminal_conflict")
                .tags("service", SERVICE, "operation", OPERATION)
                .register(meterRegistry)
                .increment();
    }

    public void recordTraceContext(String outcome) {
        Counter.builder("billing.attempt_group.trace_context_missing")
                .tags("service", SERVICE, "operation", OPERATION, "outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
