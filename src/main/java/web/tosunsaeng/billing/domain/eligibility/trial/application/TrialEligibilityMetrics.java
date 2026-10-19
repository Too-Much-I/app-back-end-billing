package web.tosunsaeng.billing.domain.eligibility.trial.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;

@Component
public class TrialEligibilityMetrics {

    private static final String EVENT_METRIC = "billing.trial_eligibility.events";
    private final MeterRegistry meterRegistry;

    public TrialEligibilityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(TrialEligibilityEvent event, String outcome) {
        meterRegistry.counter(
                EVENT_METRIC,
                "eventType", event.eventType().name(),
                "schemaVersion", Integer.toString(event.schemaVersion()),
                "outcome", outcome
        ).increment();
    }

    public void recordRejected(String outcome) {
        meterRegistry.counter(
                EVENT_METRIC,
                "eventType", "UNKNOWN",
                "schemaVersion", "OTHER",
                "outcome", outcome
        ).increment();
    }

    public void recordRevisionGap() {
        meterRegistry.counter("billing.trial_eligibility.revision_gaps").increment();
    }

    public void recordTransactionRetryExhausted() {
        meterRegistry.counter("billing.trial_eligibility.transaction_retry_exhausted").increment();
    }
}
