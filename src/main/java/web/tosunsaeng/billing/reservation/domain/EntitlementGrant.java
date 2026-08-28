package web.tosunsaeng.billing.reservation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "entitlement_grants")
public class EntitlementGrant {

    @Id
    private String id;
    private String grantId;
    private String grantType;
    private String sourceType;
    private String sourceId;
    private String subjectRefId;
    private int totalUnits;
    private int availableUnits;
    private int heldUnits;
    private int consumedUnits;
    private String state;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    protected EntitlementGrant() {
    }

    private EntitlementGrant(
            String grantId,
            String sourceId,
            String subjectRefId,
            Instant now
    ) {
        this.id = grantId;
        this.grantId = grantId;
        this.grantType = "FREE_EXAM_ONCE";
        this.sourceType = "TRIAL_CLAIM";
        this.sourceId = sourceId;
        this.subjectRefId = subjectRefId;
        this.totalUnits = 1;
        this.availableUnits = 1;
        this.heldUnits = 0;
        this.consumedUnits = 0;
        this.state = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
        this.version = 1;
    }

    public static EntitlementGrant freeExamOnce(
            String grantId,
            String trialClaimId,
            String subjectRefId,
            Instant now
    ) {
        return new EntitlementGrant(grantId, trialClaimId, subjectRefId, now);
    }

    public String getGrantId() {
        return grantId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getAvailableUnits() {
        return availableUnits;
    }

    public int getHeldUnits() {
        return heldUnits;
    }

    public int getConsumedUnits() {
        return consumedUnits;
    }

    public long getVersion() {
        return version;
    }

    public String getState() {
        return state;
    }

    @Override
    public String toString() {
        return "EntitlementGrant[state=" + state + ", units=[REDACTED]]";
    }
}
