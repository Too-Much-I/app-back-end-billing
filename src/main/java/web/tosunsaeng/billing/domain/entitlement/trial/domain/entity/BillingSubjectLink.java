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

    @Override
    public String toString() {
        return "BillingSubjectLink[active=" + active + ", sensitiveFields=[REDACTED]]";
    }
}
