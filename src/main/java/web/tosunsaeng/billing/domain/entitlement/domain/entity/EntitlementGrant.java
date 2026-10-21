package web.tosunsaeng.billing.domain.entitlement.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "entitlement_grants")
public class EntitlementGrant {

    @Id
    private String id;
    private String grantId;
    private String benefitCode;
    private String sourceType;
    private String sourceId;
    private String subjectRefId;
    private String environment;
    private String ownerUserId;
    private Long grantOrder;
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
            String benefitCode,
            String sourceType,
            String sourceId,
            String subjectRefId,
            int totalUnits,
            Instant now
    ) {
        this.id = grantId;
        this.grantId = grantId;
        this.benefitCode = benefitCode;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.subjectRefId = subjectRefId;
        this.totalUnits = totalUnits;
        this.availableUnits = totalUnits;
        this.heldUnits = 0;
        this.consumedUnits = 0;
        this.state = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
        this.version = 1;
    }

    public static EntitlementGrant unitGrant(
            String grantId,
            String benefitCode,
            String sourceType,
            String sourceId,
            String subjectRefId,
            int totalUnits,
            Instant now
    ) {
        if (benefitCode == null || benefitCode.isBlank()
                || sourceType == null || sourceType.isBlank()
                || sourceId == null || sourceId.isBlank()
                || totalUnits <= 0) {
            throw new IllegalArgumentException("The entitlement grant policy is invalid.");
        }
        return new EntitlementGrant(
                grantId, benefitCode, sourceType, sourceId, subjectRefId, totalUnits, now
        );
    }

    /** Persistence foundation only: caller proves MEMBER, eligibility and original consumption in its transaction. */
    public static EntitlementGrant restoredTrial(String grantId, String environment, String ownerUserId,
            String originalConsumptionId, long grantOrder, Instant now) {
        return memberGrant(grantId, environment, ownerUserId, "TRIAL_PAID_RESTORATION", originalConsumptionId, grantOrder, now);
    }
    /** Caller proves MEMBER and lifetime quota in the same transaction. */
    public static EntitlementGrant invite(String grantId, String environment, String ownerUserId,
            String awardId, long grantOrder, Instant now) {
        return memberGrant(grantId, environment, ownerUserId, "INVITE", awardId, grantOrder, now);
    }
    private static EntitlementGrant memberGrant(String grantId, String environment, String ownerUserId,
            String sourceType, String sourceId, long grantOrder, Instant now) {
        if (!java.util.Set.of("test", "staging", "production").contains(environment)
                || ownerUserId == null || !ownerUserId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || grantOrder < 1 || grantId == null || grantId.isBlank())
            throw new IllegalArgumentException("Invalid MEMBER grant owner/order");
        EntitlementGrant grant = unitGrant(grantId, "FREE_EXAM_ONCE", sourceType, sourceId, null, 1, java.util.Objects.requireNonNull(now));
        grant.environment = environment; grant.ownerUserId = ownerUserId; grant.grantOrder = grantOrder;
        return grant;
    }
    public Instant getCreatedAt() { return createdAt; }

    public String getEnvironment() { return environment; }
    public String getOwnerUserId() { return ownerUserId; }
    public Long getGrantOrder() { return grantOrder; }

    public String getGrantId() {
        return grantId;
    }

    public String getBenefitCode() {
        return benefitCode;
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
