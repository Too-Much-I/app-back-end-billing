package web.tosunsaeng.billing.domain.benefit.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "benefit_definitions")
public class BenefitDefinition {

    public static final String FREE_EXAM_ONCE = "FREE_EXAM_ONCE";
    public static final String FREE_EXAM_ONCE_DISPLAY_NAME = "무료 모의고사 응시권";
    public static final int FREE_EXAM_ONCE_POLICY_VERSION = 1;
    public static final int FREE_EXAM_ONCE_GRANT_UNITS = 1;

    private static final Pattern BENEFIT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public enum EntitlementModel {
        UNIT
    }

    public enum UnitType {
        EXAM_ATTEMPT
    }

    @Id
    private String benefitCode;
    private String displayName;
    private EntitlementModel entitlementModel;
    private UnitType unitType;
    private int defaultGrantUnits;
    private int policyVersion;
    private boolean active;
    private Instant createdAt;

    protected BenefitDefinition() {
    }

    private BenefitDefinition(
            String benefitCode,
            String displayName,
            EntitlementModel entitlementModel,
            UnitType unitType,
            int defaultGrantUnits,
            int policyVersion,
            boolean active,
            Instant createdAt
    ) {
        if (!isValidBenefitCode(benefitCode)) {
            throw new IllegalArgumentException("The benefit code is invalid.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("The benefit display name is required.");
        }
        if (entitlementModel == null || unitType == null) {
            throw new IllegalArgumentException("The benefit unit policy is required.");
        }
        if (defaultGrantUnits <= 0 || policyVersion <= 0 || createdAt == null) {
            throw new IllegalArgumentException("The benefit policy values are invalid.");
        }
        this.benefitCode = benefitCode;
        this.displayName = displayName;
        this.entitlementModel = entitlementModel;
        this.unitType = unitType;
        this.defaultGrantUnits = defaultGrantUnits;
        this.policyVersion = policyVersion;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static BenefitDefinition freeExamOnce(Instant createdAt) {
        return new BenefitDefinition(
                FREE_EXAM_ONCE,
                FREE_EXAM_ONCE_DISPLAY_NAME,
                EntitlementModel.UNIT,
                UnitType.EXAM_ATTEMPT,
                FREE_EXAM_ONCE_GRANT_UNITS,
                FREE_EXAM_ONCE_POLICY_VERSION,
                true,
                createdAt
        );
    }

    public static boolean isValidBenefitCode(String value) {
        return value != null && BENEFIT_CODE.matcher(value).matches();
    }

    public boolean hasApprovedFreeExamOncePolicy() {
        return FREE_EXAM_ONCE.equals(benefitCode)
                && FREE_EXAM_ONCE_DISPLAY_NAME.equals(displayName)
                && entitlementModel == EntitlementModel.UNIT
                && unitType == UnitType.EXAM_ATTEMPT
                && defaultGrantUnits == FREE_EXAM_ONCE_GRANT_UNITS
                && policyVersion == FREE_EXAM_ONCE_POLICY_VERSION
                && active
                && createdAt != null;
    }

    public String getBenefitCode() {
        return benefitCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntitlementModel getEntitlementModel() {
        return entitlementModel;
    }

    public UnitType getUnitType() {
        return unitType;
    }

    public int getDefaultGrantUnits() {
        return defaultGrantUnits;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BenefitDefinition that)) {
            return false;
        }
        return defaultGrantUnits == that.defaultGrantUnits
                && policyVersion == that.policyVersion
                && active == that.active
                && Objects.equals(benefitCode, that.benefitCode)
                && Objects.equals(displayName, that.displayName)
                && entitlementModel == that.entitlementModel
                && unitType == that.unitType
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                benefitCode, displayName, entitlementModel, unitType,
                defaultGrantUnits, policyVersion, active, createdAt
        );
    }

    @Override
    public String toString() {
        return "BenefitDefinition[benefitCode=" + benefitCode
                + ", policyVersion=" + policyVersion + ", active=" + active + "]";
    }
}
