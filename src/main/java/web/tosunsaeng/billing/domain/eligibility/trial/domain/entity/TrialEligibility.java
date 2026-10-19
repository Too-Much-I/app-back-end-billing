package web.tosunsaeng.billing.domain.eligibility.trial.domain.entity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityEventType;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityState;

@Document(collection = "trial_eligibility")
public class TrialEligibility {

    @Id
    private String id;
    private String consumerScopeId;
    private String userId;
    private long bindingRevision;
    private TrialEligibilityState state;
    private List<TrialEligibilityCandidate> candidates;
    private Instant verifiedAt;
    private Instant revokedAt;
    private String lastEventId;
    private String lastPayloadDigest;
    private Instant updatedAt;
    private long version;

    protected TrialEligibility() {
    }

    private TrialEligibility(
            String id,
            String consumerScopeId,
            String userId,
            long bindingRevision,
            TrialEligibilityState state,
            List<TrialEligibilityCandidate> candidates,
            Instant verifiedAt,
            Instant revokedAt,
            String lastEventId,
            String lastPayloadDigest,
            Instant updatedAt,
            long version
    ) {
        this.id = id;
        this.consumerScopeId = consumerScopeId;
        this.userId = userId;
        this.bindingRevision = bindingRevision;
        this.state = state;
        this.candidates = candidates == null ? null : List.copyOf(candidates);
        this.verifiedAt = verifiedAt;
        this.revokedAt = revokedAt;
        this.lastEventId = lastEventId;
        this.lastPayloadDigest = lastPayloadDigest;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static TrialEligibility applied(
            TrialEligibility current,
            TrialEligibilityEvent event,
            Instant updatedAt
    ) {
        boolean verified = event.eventType() == TrialEligibilityEventType.VERIFIED;
        return new TrialEligibility(
                current == null ? null : current.id,
                event.consumerScopeId(),
                event.userId(),
                event.bindingRevision(),
                verified ? TrialEligibilityState.VERIFIED : TrialEligibilityState.REVOKED,
                verified ? event.candidates() : null,
                verified ? event.verifiedAt() : null,
                verified ? null : event.revokedAt(),
                event.eventId(),
                event.payloadDigest(),
                updatedAt,
                current == null ? 1 : current.version + 1
        );
    }

    public String getId() {
        return id;
    }

    public String getConsumerScopeId() {
        return consumerScopeId;
    }

    public String getUserId() {
        return userId;
    }

    public long getBindingRevision() {
        return bindingRevision;
    }

    public TrialEligibilityState getState() {
        return state;
    }

    public List<TrialEligibilityCandidate> getCandidates() {
        return candidates == null ? List.of() : List.copyOf(candidates);
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public String getLastPayloadDigest() {
        return lastPayloadDigest;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "TrialEligibility[state=" + state
                + ", sensitiveFields=[REDACTED]]";
    }
}
