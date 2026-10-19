package web.tosunsaeng.billing.domain.entitlement.application;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.domain.entitlement.trial.application.TrialEntitlementPolicy;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.*;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityState;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;
import static web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Action.*;

@Component
public class TrialEntitlementQueryEvaluator {
    public Benefit evaluate(EntitlementQuerySnapshot s) {
        require(s.definition() != null && s.definition().hasApprovedFreeExamOncePolicy());
        var claims = unique(s.claims(), TrialClaim::getTrialClaimId);
        var links = unique(s.links(), BillingSubjectLink::getTrialClaimId);
        var grants = unique(s.grants(), EntitlementGrant::getSourceId);
        var sessions = unique(s.sessions(), AttemptSession::getSessionId);
        var reservations = unique(s.reservations(), Reservation::getProposedSessionId);
        for (var claim : claims.values()) {
            var link = links.get(claim.getTrialClaimId());
            var grant = grants.get(claim.getTrialClaimId());
            require(claim.getState() == TrialClaim.State.ACTIVE && TrialEntitlementPolicy.retained(claim.getRetentionExpiresAt(), s.asOf()));
            require(link != null && link.isActive() && TrialEntitlementPolicy.retained(link.getRetentionExpiresAt(), s.asOf())
                    && claim.getSubjectRefId().equals(link.getSubjectRefId()));
            require(grant != null && TrialEntitlementPolicy.validUnits(grant) && "ACTIVE".equals(grant.getState())
                    && "TRIAL_CLAIM".equals(grant.getSourceType()) && claim.getSubjectRefId().equals(grant.getSubjectRefId())
                    && TrialEntitlementPolicy.matchesDefinition(s.definition(), grant));
            var groups = s.groups().stream().filter(g -> claim.getTrialClaimId().equals(g.getTrialClaimId())).toList();
            require(groups.size() <= 1); // One consumption per free Claim; not one group per user/benefit.
            require(groups.stream().allMatch(g -> claim.getSubjectRefId().equals(g.getSubjectRefId())));
            require((grant.getConsumedUnits() == 1) == !groups.isEmpty());
            var held = s.reservations().stream().filter(r -> claim.getSubjectRefId().equals(r.getSubjectRefId())
                    && r.getStatus() == Reservation.Status.RESERVED).toList();
            require(held.size() <= 1);
            require((grant.getHeldUnits() == 1) == held.stream().anyMatch(r -> r.getReservationKind() == Reservation.Kind.INITIAL));
        }
        require(s.groups().stream().allMatch(g -> claims.containsKey(g.getTrialClaimId())));
        Eligibility eligibility = TrialEntitlementPolicy.verified(s.eligibility()) ? Eligibility.VERIFIED
                : s.eligibility() != null && s.eligibility().getState() == TrialEligibilityState.REVOKED
                ? Eligibility.NOT_ELIGIBLE : Eligibility.UNKNOWN;
        Set<Reason> reasons = new TreeSet<>(Comparator.comparing(Enum::name));
        String selected = s.candidateClaimId();
        var selectedLink = selected == null ? null : links.get(selected);
        require(selected == null || selectedLink != null);
        boolean unresolved = selectedLink != null && !s.userId().equals(selectedLink.getUserId());
        boolean foreignCompleted = unresolved && s.groups().stream().anyMatch(g -> selected.equals(g.getTrialClaimId())
                && g.getStatus() == AttemptGroup.Status.COMPLETED);
        Integer quantity;
        if (eligibility == Eligibility.NOT_ELIGIBLE || foreignCompleted) { quantity = 0; }
        else if (eligibility == Eligibility.UNKNOWN || unresolved) { quantity = null; }
        else { quantity = selected == null ? s.definition().getDefaultGrantUnits() : grants.get(selected).getAvailableUnits(); }
        Action start = quantity == null ? PENDING : quantity > 0 ? ALLOWED : BLOCKED;
        List<Group> views = new ArrayList<>();
        boolean incomplete = false, completed = foreignCompleted;
        long phoneContexts = s.groups().stream().filter(g -> g.getStatus() != AttemptGroup.Status.COMPLETED)
                .filter(g -> { var l = links.get(g.getTrialClaimId()); return s.userId().equals(l.getUserId())
                        && "PHONE_REJOIN".equals(l.getOwnerTransitionReason()); }).count();
        boolean phoneGrading = s.groups().stream().anyMatch(g -> g.getStatus() == AttemptGroup.Status.GRADING
                && s.userId().equals(links.get(g.getTrialClaimId()).getUserId())
                && "PHONE_REJOIN".equals(links.get(g.getTrialClaimId()).getOwnerTransitionReason()));
        for (var group : s.groups()) {
            var link = links.get(group.getTrialClaimId());
            if (!s.userId().equals(link.getUserId())) { continue; }
            boolean terminal = group.getStatus() == AttemptGroup.Status.COMPLETED;
            completed |= terminal; incomplete |= !terminal;
            List<Reason> groupReasons = new ArrayList<>();
            boolean currentCandidate = group.getTrialClaimId().equals(selected);
            boolean progress = false;
            boolean oldSession = false;
            var session = sessions.get(group.getActiveSessionId());
            if (!terminal) {
                if (session == null) {
                    throw new EntitlementQueryException(EntitlementQueryException.Failure.INVARIANT);
                }
                var reservation = reservations.get(session.getSessionId());
                validateAttribution(group, link, session, reservation);
                oldSession = session.getSessionOwnerEpoch() < link.getSessionOwnerEpoch();
                progress = !oldSession && session.getState() == AttemptSession.State.ACTIVE
                        && (group.getStatus() == AttemptGroup.Status.OPEN || group.getStatus() == AttemptGroup.Status.GRADING);
                if (group.getStatus() == AttemptGroup.Status.GRADING) {
                    require(session.getState() == AttemptSession.State.ACTIVE);
                }
            }
            Action retake = TrialEntitlementPolicy.replaceable(group.getStatus())
                    && currentCandidate && eligibility == Eligibility.VERIFIED ? ALLOWED : BLOCKED;
            if (!terminal && eligibility == Eligibility.UNKNOWN) { retake = PENDING; }
            if (oldSession && retake == ALLOWED && (!"PHONE_REJOIN".equals(link.getOwnerTransitionReason())
                    || link.getOwnerTransitionId() == null)) {
                retake = PENDING; groupReasons.add(Reason.RECONCILIATION_PENDING);
            }
            if (terminal) { groupReasons.add(Reason.BENEFIT_COMPLETED); }
            else if (group.getStatus() == AttemptGroup.Status.GRADING) { groupReasons.add(Reason.GRADING_IN_PROGRESS); }
            else if (oldSession && retake == ALLOWED) { groupReasons.add(Reason.REJOIN_RESTART_AVAILABLE); }
            else if (progress) { groupReasons.add(Reason.ATTEMPT_IN_PROGRESS); }
            else { groupReasons.add(Reason.RETAKE_AVAILABLE); }
            var active = s.reservations().stream().filter(r -> r.getSubjectRefId().equals(link.getSubjectRefId())
                    && r.getStatus() == Reservation.Status.RESERVED).findFirst();
            if (active.isPresent() && eligibility != Eligibility.NOT_ELIGIBLE) {
                retake = PENDING;
                groupReasons.add(active.get().getExpiresAt().isAfter(s.asOf()) ? Reason.RESERVATION_PENDING : Reason.RESERVATION_EXPIRY_PENDING);
            }
            if (s.processing() && eligibility != Eligibility.NOT_ELIGIBLE) { retake = PENDING; groupReasons.add(Reason.COMMAND_PENDING); }
            if ((phoneContexts > 1 || phoneGrading) && retake == ALLOWED) {
                retake = PENDING; groupReasons.add(Reason.AMBIGUOUS_ATTEMPT_CONTEXT);
            }
            views.add(new Group(group.getAttemptGroupId(), group.getStatus(), progress, retake, sorted(groupReasons)));
        }
        var ownedReservations = s.reservations().stream().filter(r -> r.getStatus() == Reservation.Status.RESERVED)
                .filter(r -> s.links().stream().anyMatch(l -> l.getSubjectRefId().equals(r.getSubjectRefId()) && s.userId().equals(l.getUserId()))).toList();
        boolean reserved = !ownedReservations.isEmpty();
        boolean expiryDue = ownedReservations.stream().anyMatch(r -> !r.getExpiresAt().isAfter(s.asOf()));
        incomplete |= reserved;
        Action retake = views.stream().anyMatch(g -> g.retake() == ALLOWED) ? ALLOWED
                : views.stream().anyMatch(g -> g.retake() == PENDING) ? PENDING : BLOCKED;
        Boolean progress = views.stream().anyMatch(g -> Boolean.TRUE.equals(g.hasInProgress()));
        if (eligibility == Eligibility.UNKNOWN || unresolved && !foreignCompleted) {
            reasons.add(eligibility == Eligibility.UNKNOWN ? Reason.ELIGIBILITY_UNKNOWN : Reason.OWNER_LINK_UNRESOLVED);
            start = PENDING; retake = PENDING;
            if (views.isEmpty()) { progress = null; }
        } else if (eligibility == Eligibility.NOT_ELIGIBLE) {
            reasons.add(Reason.PHONE_NOT_ELIGIBLE); start = BLOCKED; retake = BLOCKED;
        } else {
            if (reserved) {
                reasons.add(expiryDue ? Reason.RESERVATION_EXPIRY_PENDING : Reason.RESERVATION_PENDING);
                start = expiryDue ? PENDING : BLOCKED; retake = PENDING;
            }
            if (s.processing()) { start = PENDING; retake = PENDING; reasons.add(Reason.COMMAND_PENDING); }
            if (phoneContexts > 1 || phoneGrading && start == ALLOWED) {
                start = PENDING; retake = PENDING; reasons.add(Reason.AMBIGUOUS_ATTEMPT_CONTEXT);
            }
        }
        if (quantity != null && quantity == 0) { reasons.add(Reason.NO_NEW_UNITS); }
        views.forEach(g -> reasons.addAll(g.reasonCodes()));
        if (foreignCompleted) { reasons.add(Reason.BENEFIT_COMPLETED); }
        boolean unused = quantity != null && quantity > 0;
        int states = (unused ? 1 : 0) + (incomplete ? 1 : 0) + (completed ? 1 : 0);
        Usage usage = states > 1 ? Usage.MIXED : incomplete ? Usage.INCOMPLETE : completed ? Usage.COMPLETED
                : unused ? Usage.NOT_STARTED : Usage.UNKNOWN;
        return new Benefit(s.definition().getBenefitCode(), s.definition().getDisplayName(), s.definition().getUnitType().name(),
                quantity, eligibility, usage, start, progress, retake, List.copyOf(reasons),
                views.stream().sorted(Comparator.comparing(Group::attemptGroupId)).toList());
    }
    private static void validateAttribution(AttemptGroup group, BillingSubjectLink link, AttemptSession session, Reservation reservation) {
        if (link.getSessionOwnerEpoch() == null || session.getSessionOwnerEpoch() == null
                || reservation != null && reservation.getSessionOwnerEpoch() == null) {
            throw new EntitlementQueryException(EntitlementQueryException.Failure.LEGACY_SESSION_ATTRIBUTION_MISSING);
        }
        require(reservation != null && reservation.getStatus() == Reservation.Status.CONFIRMED
                && group.getAttemptGroupId().equals(session.getAttemptGroupId())
                && group.getSubjectRefId().equals(session.getSubjectRefId())
                && group.getAttemptGroupId().equals(reservation.getAttemptGroupId())
                && group.getSubjectRefId().equals(reservation.getSubjectRefId())
                && session.getOperationId().equals(reservation.getOperationId())
                && session.getSessionOwnerEpoch().equals(reservation.getSessionOwnerEpoch())
                && session.getSessionOwnerEpoch() > 0 && session.getSessionOwnerEpoch() <= link.getSessionOwnerEpoch());
        require(session.getState() != AttemptSession.State.PROPOSED);
    }
    private static List<Reason> sorted(Collection<Reason> reasons) {
        return reasons.stream().distinct().sorted(Comparator.comparing(Enum::name)).toList();
    }
    private static <T> Map<String, T> unique(List<T> values, Function<T, String> id) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) { require(result.put(id.apply(value), value) == null); }
        return result;
    }
}
