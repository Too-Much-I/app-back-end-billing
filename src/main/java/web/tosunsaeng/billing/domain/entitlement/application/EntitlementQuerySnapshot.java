package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Instant;
import java.util.List;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.*;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

/** Transaction-local detached data; no repository or mutation capabilities. */
public record EntitlementQuerySnapshot(String userId, Instant asOf, BenefitDefinition definition,
        TrialEligibility eligibility, String candidateClaimId, List<TrialClaim> claims,
        List<BillingSubjectLink> links, List<EntitlementGrant> grants, List<AttemptGroup> groups,
        List<Reservation> reservations, List<AttemptSession> sessions, boolean processing) {
    public EntitlementQuerySnapshot {
        claims = List.copyOf(claims); links = List.copyOf(links); grants = List.copyOf(grants);
        groups = List.copyOf(groups); reservations = List.copyOf(reservations); sessions = List.copyOf(sessions);
    }
    @Override public String toString() { return "EntitlementQuerySnapshot[REDACTED]"; }
}
