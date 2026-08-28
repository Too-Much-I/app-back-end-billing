package web.tosunsaeng.billing.domain.eligibility.trial.domain.entity;

import java.time.Instant;
import java.util.List;

import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityEventType;

public record TrialEligibilityEvent(
        String eventId,
        TrialEligibilityEventType eventType,
        int schemaVersion,
        String producer,
        Instant occurredAt,
        String consumerScopeId,
        String userId,
        Instant verifiedAt,
        Instant revokedAt,
        long bindingRevision,
        List<TrialEligibilityCandidate> candidates,
        String payloadDigest
) {

    public TrialEligibilityEvent {
        candidates = List.copyOf(candidates);
    }

    @Override
    public String toString() {
        return "TrialEligibilityEvent[eventType=" + eventType
                + ", schemaVersion=" + schemaVersion
                + ", sensitiveFields=[REDACTED]]";
    }
}
