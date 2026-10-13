package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.Instant;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;

/** Pure classification, not an authorization to delete. */
public final class CleanupEligibilityEvaluator {
    public Decision evaluate(Subject subject, Local local, Provider provider, Coverage coverage, Instant now) {
        if (local == Local.FOUND || provider != null && provider.observation() == Observation.FOUND)
            return new Decision(State.FINANCIAL_RETAINED, Reason.PURCHASE_FOUND, null);
        if (subject.pendingEvidence() > 0 || local == Local.PENDING) return Decision.blocked(Reason.PENDING_EVIDENCE);
        if (local != Local.NONE) return Decision.blocked(Reason.LOCAL_EVIDENCE_UNAVAILABLE);
        if (provider == null || provider.observation() == Observation.UNKNOWN)
            return Decision.blocked(provider == null ? Reason.PROVIDER_UNAVAILABLE : provider.failure());
        if (!provider.complete()) return Decision.blocked(Reason.PROVIDER_INCOMPLETE);
        if (coverage == null) return Decision.blocked(Reason.COVERAGE_UNPROVEN);
        if (now.isBefore(subject.eligibleAt()) || !now.isBefore(coverage.validUntil()))
            return Decision.blocked(Reason.PROOF_EXPIRED);
        return new Decision(State.READY, null, coverage);
    }
}
