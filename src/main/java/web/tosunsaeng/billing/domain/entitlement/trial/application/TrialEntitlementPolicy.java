package web.tosunsaeng.billing.domain.entitlement.trial.application;

import java.time.Instant;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityState;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;

/** Shared pure predicates. Never issues benefits or modifies retained aliases. */
public final class TrialEntitlementPolicy {
    private TrialEntitlementPolicy() { }
    public static boolean verified(TrialEligibility value) {
        return value != null && value.getState() == TrialEligibilityState.VERIFIED
                && !value.getCandidates().isEmpty();
    }
    public static boolean retained(Instant expiresAt, Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }
    public static boolean validUnits(EntitlementGrant grant) {
        return grant.getAvailableUnits() >= 0 && grant.getHeldUnits() >= 0 && grant.getConsumedUnits() >= 0
                && (long) grant.getAvailableUnits() + grant.getHeldUnits() + grant.getConsumedUnits()
                == grant.getTotalUnits();
    }
    public static boolean matchesDefinition(BenefitDefinition definition, EntitlementGrant grant) {
        return definition.getBenefitCode().equals(grant.getBenefitCode())
                && definition.getDefaultGrantUnits() == grant.getTotalUnits();
    }
    public static boolean replaceable(AttemptGroup.Status state) {
        return state == AttemptGroup.Status.OPEN || state == AttemptGroup.Status.RETAKE_AVAILABLE;
    }
}
