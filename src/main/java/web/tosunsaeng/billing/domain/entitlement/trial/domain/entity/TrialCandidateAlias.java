package web.tosunsaeng.billing.domain.entitlement.trial.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "trial_candidate_aliases")
public class TrialCandidateAlias {

    @Id
    private String id;
    private String aliasId;
    private String benefitCode;
    private String keyVersion;
    private String candidate;
    private String trialClaimId;
    private boolean active;
    private Instant createdAt;
    private Instant retentionExpiresAt;

    protected TrialCandidateAlias() {
    }

    private TrialCandidateAlias(
            String aliasId,
            String benefitCode,
            String keyVersion,
            String candidate,
            String trialClaimId,
            Instant createdAt,
            Instant retentionExpiresAt
    ) {
        this.id = aliasId;
        this.aliasId = aliasId;
        this.benefitCode = benefitCode;
        this.keyVersion = keyVersion;
        this.candidate = candidate;
        this.trialClaimId = trialClaimId;
        this.active = true;
        this.createdAt = createdAt;
        this.retentionExpiresAt = retentionExpiresAt;
    }

    public static TrialCandidateAlias active(
            String aliasId,
            String benefitCode,
            String keyVersion,
            String candidate,
            String trialClaimId,
            Instant createdAt,
            Instant retentionExpiresAt
    ) {
        return new TrialCandidateAlias(
                aliasId, benefitCode, keyVersion, candidate, trialClaimId,
                createdAt, retentionExpiresAt
        );
    }

    public String getAliasId() {
        return aliasId;
    }

    public String getBenefitCode() {
        return benefitCode;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public String getCandidate() {
        return candidate;
    }

    public String getTrialClaimId() {
        return trialClaimId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getRetentionExpiresAt() {
        return retentionExpiresAt;
    }

    @Override
    public String toString() {
        return "TrialCandidateAlias[active=" + active + ", sensitiveFields=[REDACTED]]";
    }
}
