package web.tosunsaeng.billing.domain.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;

class AttemptGroupEventMetricsTest {

    @Test
    void usesOnlyApprovedLowCardinalityTagsAndHistogramValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AttemptGroupEventMetrics metrics = new AttemptGroupEventMetrics(registry);
        AttemptGroupStatusEvent event = event(Instant.parse("2026-08-31T12:00:01Z"));

        metrics.record(event, "APPLIED", 2_000_000, Instant.parse("2026-08-31T12:00:00Z"));

        assertThat(registry.get("billing.attempt_group.events")
                .tag("service", "billing")
                .tag("operation", "attempt_group_event_consume")
                .tag("outcome", "applied")
                .tag("targetStatus", "GRADING")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("billing.attempt_group.consume.duration")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("billing.attempt_group.event.age")
                .summary().totalAmount()).isZero();
        assertThat(registry.get("billing.attempt_group.clock_skew")
                .counter().count()).isEqualTo(1);

        assertThat(registry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .extracting(Tag::getKey)
                .doesNotContain(
                        "traceId", "eventId", "userId", "sessionId",
                        "attemptGroupId", "durationMs", "eventAgeMs"
                );
        assertThat(registry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .extracting(Tag::getValue)
                .doesNotContain(event.eventId(), event.userId(), event.sessionId());
    }

    private static AttemptGroupStatusEvent event(Instant occurredAt) {
        return new AttemptGroupStatusEvent(
                "8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442",
                "AttemptGroupStatusChanged",
                1,
                "learning-core",
                occurredAt,
                "e8b37a41-bae6-47f1-a770-052e6c5786d4",
                "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
                "ex_a1b2c3d4e5_0826_1530",
                AttemptGroupEventTarget.GRADING,
                null,
                null,
                "digest"
        );
    }
}
