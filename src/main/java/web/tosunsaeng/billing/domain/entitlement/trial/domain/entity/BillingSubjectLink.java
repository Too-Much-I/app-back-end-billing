package web.tosunsaeng.billing.domain.entitlement.trial.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "billing_subject_links")
public class BillingSubjectLink {

    @Id
    private String id;
    private String subjectRefId;
    private String trialClaimId;
    private String consumerScopeId;
    private String userId;
    private boolean active;
    private Instant createdAt;
    private Instant retentionExpiresAt;
    private Long ownerVersion;
    private Instant ownerUpdatedAt;
    private String ownerTransitionReason;
    private String ownerTransitionId;

    protected BillingSubjectLink() {
    }

    private BillingSubjectLink(
            String subjectRefId,
            String trialClaimId,
            String consumerScopeId,
            String userId,
            Instant createdAt,
            Instant retentionExpiresAt
    ) {
        this.id = subjectRefId;
        this.subjectRefId = subjectRefId;
        this.trialClaimId = trialClaimId;
        this.consumerScopeId = consumerScopeId;
        this.userId = userId;
        this.active = true;
        this.createdAt = createdAt;
        this.retentionExpiresAt = retentionExpiresAt;
        this.ownerVersion = 1L;
        this.ownerUpdatedAt = createdAt;
    }

    public static BillingSubjectLink active(
            String subjectRefId,
            String trialClaimId,
            String consumerScopeId,
            String userId,
            Instant createdAt,
            Instant retentionExpiresAt
    ) {
        return new BillingSubjectLink(
                subjectRefId, trialClaimId, consumerScopeId, userId,
                createdAt, retentionExpiresAt
        );
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public String getTrialClaimId() {
        return trialClaimId;
    }

    public String getConsumerScopeId() {
        return consumerScopeId;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getRetentionExpiresAt() {
        return retentionExpiresAt;
    }

    public long getOwnerVersion() {
        return ownerVersion == null ? 1L : ownerVersion;
    }

    public boolean hasExplicitOwnerVersion() {
        return ownerVersion != null;
    }

    public Instant getOwnerUpdatedAt() {
        return ownerUpdatedAt == null ? createdAt : ownerUpdatedAt;
    }

    public String getOwnerTransitionReason() {
        return ownerTransitionReason;
    }

    public String getOwnerTransitionId() {
        return ownerTransitionId;
    }

    @Override
    public String toString() {
        return "BillingSubjectLink[active=" + active + ", sensitiveFields=[REDACTED]]";
    }
}
