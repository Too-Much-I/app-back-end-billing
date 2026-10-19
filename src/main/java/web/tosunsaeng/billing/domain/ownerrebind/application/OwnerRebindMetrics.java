package web.tosunsaeng.billing.domain.ownerrebind.application;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;

@Component
public class OwnerRebindMetrics {

    private final MeterRegistry registry;

    public OwnerRebindMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(OwnerRebindEventKind kind, String outcome, long durationNanos) {
        String normalized = outcome.toLowerCase(Locale.ROOT);
        Counter.builder("billing.owner.rebind.events")
                .tags("service", "billing", "operation", "owner_rebind_consume",
                        "eventKind", kind.name(), "outcome", normalized)
                .register(registry)
                .increment();
        Timer.builder("billing.owner.rebind.duration")
                .tags("service", "billing", "operation", "owner_rebind_consume",
                        "eventKind", kind.name(), "outcome", normalized)
                .register(registry)
                .record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);
    }

    public void recordRejected(String outcome) {
        Counter.builder("billing.owner.rebind.events")
                .tags("service", "billing", "operation", "owner_rebind_consume",
                        "eventKind", "UNKNOWN", "outcome", outcome.toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
    }

    public void recordPending(String reason) {
        Counter.builder("billing.owner.rebind.pending")
                .tags("service", "billing", "operation", "owner_rebind_consume",
                        "reason", reason)
                .register(registry)
                .increment();
    }

    public void recordRetryExhausted() {
        Counter.builder("billing.owner.rebind.transaction.retry_exhausted")
                .tags("service", "billing", "operation", "owner_rebind_consume")
                .register(registry)
                .increment();
    }

    public void recordInvariantViolation(String reason) {
        Counter.builder("billing.owner.rebind.invariant_violation")
                .tags("service", "billing", "operation", "owner_rebind_consume",
                        "reason", reason)
                .register(registry)
                .increment();
    }

    public void recordTraceContext(String outcome) {
        Counter.builder("billing.owner.rebind.trace_context")
                .tags("service", "billing", "operation", "owner_rebind_consume",
                        "outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordCleanup(String outcome, int count) {
        Counter.builder("billing.owner.rebind.cleanup")
                .tags("service", "billing", "operation", "owner_rebind_cleanup",
                        "outcome", outcome.toLowerCase(Locale.ROOT))
                .register(registry)
                .increment(Math.max(0, count));
    }

    public void recordCleanupOverdue(int count) {
        if (count < 1) {
            return;
        }
        Counter.builder("billing.owner.rebind.cleanup.overdue")
                .tags("service", "billing", "operation", "owner_rebind_cleanup")
                .register(registry)
                .increment(count);
    }
}
