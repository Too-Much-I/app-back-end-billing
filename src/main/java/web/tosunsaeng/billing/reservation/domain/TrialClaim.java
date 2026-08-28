package web.tosunsaeng.billing.reservation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "trial_claims")
public class TrialClaim {

    public enum State {
        ACTIVE,
        ANONYMIZED
    }

    @Id
    private String id;
    private String trialClaimId;
    private String benefitType;
    private String subjectRefId;
    private String sourceEventId;
    private Instant claimedAt;
    private Instant retentionExpiresAt;
    private State state;
    private Instant anonymizedAt;

    protected TrialClaim() {
    }

    private TrialClaim(
            String trialClaimId,
            String subjectRefId,
            String sourceEventId,
            Instant claimedAt,
            Instant retentionExpiresAt
    ) {
        this.id = trialClaimId;
        this.trialClaimId = trialClaimId;
        this.benefitType = "FREE_EXAM_ONCE";
        this.subjectRefId = subjectRefId;
        this.sourceEventId = sourceEventId;
        this.claimedAt = claimedAt;
        this.retentionExpiresAt = retentionExpiresAt;
        this.state = State.ACTIVE;
    }

    public static TrialClaim active(
            String trialClaimId,
            String subjectRefId,
            String sourceEventId,
            Instant claimedAt,
            Instant retentionExpiresAt
    ) {
        return new TrialClaim(
                trialClaimId, subjectRefId, sourceEventId, claimedAt, retentionExpiresAt
        );
    }

    public String getTrialClaimId() {
        return trialClaimId;
    }

    public String getBenefitType() {
        return benefitType;
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getRetentionExpiresAt() {
        return retentionExpiresAt;
    }

    public State getState() {
        return state;
    }

    @Override
    public String toString() {
        return "TrialClaim[state=" + state + ", sensitiveFields=[REDACTED]]";
    }
}
