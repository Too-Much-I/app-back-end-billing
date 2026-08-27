package web.tosunsaeng.billing.trialeligibility.domain;

import java.time.Instant;
import java.util.List;

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
