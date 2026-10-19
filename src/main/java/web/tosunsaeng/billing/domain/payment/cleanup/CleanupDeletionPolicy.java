package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.*;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;

/** Approved observed-absence policy; not a guarantee against delayed Store delivery. */
final class CleanupDeletionPolicy implements CoveragePolicy, CleanupPurger.FinalPolicy {
    static final String VERSION = "observed-absence-r1";
    private final String environment;
    CleanupDeletionPolicy(String environment) { this.environment = environment; }
    @Override public Coverage confirm(Subject subject, Provider observation, Instant now) {
        if (observation == null || observation.observation() != Observation.ABSENT_OBSERVED
                || !observation.complete() || !eligible(subject, now)) return null;
        // Keep the installed r2 storage shape, but manage one code-owned policy revision.
        // Old three-version proofs are NOT translated to this revision.
        return new Coverage(VERSION, VERSION, VERSION, now.plusSeconds(3600));
    }
    @Override public boolean valid(Subject subject, Coverage recorded, Instant checkedAt, Instant now) {
        return eligible(subject, checkedAt) && eligible(subject, now) && !checkedAt.isAfter(now)
                && VERSION.equals(recorded.adapterVersion()) && VERSION.equals(recorded.coverageVersion())
                && VERSION.equals(recorded.drainPolicyVersion()) && now.isBefore(recorded.validUntil())
                && !recorded.validUntil().isAfter(checkedAt.plusSeconds(3600));
    }
    private boolean eligible(Subject s, Instant now) {
        if (!environment.equals(s.environment()) || s.pendingEvidence() != 0 || now.isBefore(s.eligibleAt())) return false;
        if (s.accountId() == null) return s.accountVersion() == null && s.references().isEmpty() && s.cursorActivityAt() == null;
        return s.accountVersion() != null && s.accountVersion() > 0 && !s.references().isEmpty()
                && new HashSet<>(s.references()).size() == s.references().size() && s.cursorActivityAt() != null
                // 30 minute token + both 60 second clock boundaries; DB work also checks its deadline.
                && !now.isBefore(s.cursorActivityAt().plusSeconds(1920));
    }
}
