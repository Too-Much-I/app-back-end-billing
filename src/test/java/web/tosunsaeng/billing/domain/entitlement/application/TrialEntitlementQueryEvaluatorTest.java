package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.*;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityState;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.*;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrialEntitlementQueryEvaluatorTest {
    static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    final TrialEntitlementQueryEvaluator evaluator = new TrialEntitlementQueryEvaluator();
    final BenefitDefinition definition = BenefitDefinition.freeExamOnce(NOW);
    final TrialClaim claim = TrialClaim.active("claim", "FREE_EXAM_ONCE", "subject", "event", NOW, NOW.plusSeconds(10000));
    final BillingSubjectLink link = BillingSubjectLink.active("subject", "claim", "scope", "actor", NOW, NOW.plusSeconds(10000));
    final EntitlementGrant grant = EntitlementGrant.unitGrant("grant", "FREE_EXAM_ONCE", "TRIAL_CLAIM", "claim", "subject", 1, NOW);
    TrialEligibility eligibility = verified();
    List<AttemptGroup> groups = List.of();
    List<Reservation> reservations = List.of();
    List<AttemptSession> sessions = List.of();

    @Test void noGrantStillMeansOneWithoutMutation() {
        var snapshot = new EntitlementQuerySnapshot("actor", NOW, definition, eligibility, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false);
        var result = evaluator.evaluate(snapshot);
        assertThat(result.availableQuantity()).isOne();
        assertThat(result.newAttempt()).isEqualTo(Action.ALLOWED);
        assertThat(result.retake()).isEqualTo(Action.BLOCKED);
        assertThat(evaluator.evaluate(snapshot)).isEqualTo(result);
    }
    @Test void missingProjectionIsUnknownNotZero() {
        eligibility = null;
        var result = evaluator.evaluate(snapshot());
        assertThat(result.availableQuantity()).isNull();
        assertThat(result.eligibility()).isEqualTo(Eligibility.UNKNOWN);
        assertThat(result.pending()).isTrue();
    }
    @Test void revokedCannotUseStoredAvailableGrant() {
        eligibility = mock(TrialEligibility.class);
        when(eligibility.getState()).thenReturn(TrialEligibilityState.REVOKED);
        var result = evaluator.evaluate(snapshot());
        assertThat(result.availableQuantity()).isZero();
        assertThat(result.newAttempt()).isEqualTo(Action.BLOCKED);
        assertThat(grant.getAvailableUnits()).isOne();
    }
    @Test void heldAndExpiredReservationNeverReleasedByReader() {
        set(grant, "availableUnits", 0); set(grant, "heldUnits", 1);
        var r = reservation().withSessionOwnerEpoch(1);
        reservations = List.of(r);
        assertThat(evaluator.evaluate(snapshot()).retake()).isEqualTo(Action.PENDING);
        set(r, "expiresAt", NOW);
        assertThat(evaluator.evaluate(snapshot()).reasonCodes()).contains(Reason.RESERVATION_EXPIRY_PENDING);
        assertThat(grant.getHeldUnits()).isOne();
        assertThat(r.getStatus()).isEqualTo(Reservation.Status.RESERVED);
    }
    @ParameterizedTest @EnumSource(AttemptGroup.Status.class)
    void groupStatesDoNotEquateConsumptionWithCompletion(AttemptGroup.Status status) {
        used(status, 1);
        var result = evaluator.evaluate(snapshot());
        assertThat(result.availableQuantity()).isZero();
        assertThat(result.hasInProgress()).isEqualTo(status == AttemptGroup.Status.OPEN || status == AttemptGroup.Status.GRADING);
        assertThat(result.retake()).isEqualTo(status == AttemptGroup.Status.OPEN || status == AttemptGroup.Status.RETAKE_AVAILABLE
                ? Action.ALLOWED : Action.BLOCKED);
        assertThat(result.usageState()).isEqualTo(status == AttemptGroup.Status.COMPLETED ? Usage.COMPLETED : Usage.INCOMPLETE);
        assertThat(result.pending()).isFalse();
    }
    @Test void unresolvedOwnerDoesNotLeakSourceGroup() {
        used(AttemptGroup.Status.OPEN, 1); set(link, "userId", "old-actor");
        var result = evaluator.evaluate(snapshot());
        assertThat(result.availableQuantity()).isNull();
        assertThat(result.attemptGroups()).isEmpty();
        assertThat(result.hasInProgress()).isNull();
        assertThat(result.reasonCodes()).containsExactly(Reason.OWNER_LINK_UNRESOLVED);
    }
    @Test void foreignCompletedIsOnlyPolicySummary() {
        used(AttemptGroup.Status.COMPLETED, 1); set(link, "userId", "old-actor");
        var result = evaluator.evaluate(snapshot());
        assertThat(result.availableQuantity()).isZero();
        assertThat(result.usageState()).isEqualTo(Usage.COMPLETED);
        assertThat(result.attemptGroups()).isEmpty();
        assertThat(result.pending()).isFalse();
    }
    @Test void repeatedRejoinLeavesEarlierSessionOutsideCurrentProgress() {
        used(AttemptGroup.Status.OPEN, 2);
        set(link, "sessionOwnerEpoch", 3L); set(link, "ownerTransitionReason", "PHONE_REJOIN");
        set(link, "ownerTransitionId", "transition");
        var result = evaluator.evaluate(snapshot());
        assertThat(result.hasInProgress()).isFalse();
        assertThat(result.retake()).isEqualTo(Action.ALLOWED);
        assertThat(result.reasonCodes()).contains(Reason.REJOIN_RESTART_AVAILABLE);
        set(sessions.getFirst(), "sessionOwnerEpoch", 3L);
        set(reservations.getFirst(), "sessionOwnerEpoch", 3L);
        assertThat(evaluator.evaluate(snapshot()).hasInProgress()).isTrue();
        // Snapshot has no command ownership data: its deletion cannot alter this answer.
    }
    @Test void legacyMissingEvidenceFailsRatherThanStaysPending() {
        used(AttemptGroup.Status.OPEN, 1); set(sessions.getFirst(), "sessionOwnerEpoch", null);
        assertThatThrownBy(() -> evaluator.evaluate(snapshot())).isInstanceOf(EntitlementQueryException.class)
                .hasMessage("LEGACY_SESSION_ATTRIBUTION_MISSING");
    }
    @Test void futureEpochAndMismatchedReservationFailClosed() {
        used(AttemptGroup.Status.OPEN, 2);
        assertThatThrownBy(() -> evaluator.evaluate(snapshot())).isInstanceOf(EntitlementQueryException.class);
        set(link, "sessionOwnerEpoch", 2L); set(reservations.getFirst(), "operationId", "other");
        assertThatThrownBy(() -> evaluator.evaluate(snapshot())).isInstanceOf(EntitlementQueryException.class);
    }
    @Test void missingGrantAndNegativeUnitsFailClosed() {
        set(grant, "heldUnits", -1);
        assertThatThrownBy(() -> evaluator.evaluate(snapshot())).isInstanceOf(EntitlementQueryException.class);
        var s = snapshot();
        assertThatThrownBy(() -> evaluator.evaluate(new EntitlementQuerySnapshot(s.userId(), s.asOf(), s.definition(), s.eligibility(),
                s.candidateClaimId(), s.claims(), s.links(), List.of(), s.groups(), s.reservations(), s.sessions(), false)))
                .isInstanceOf(EntitlementQueryException.class);
    }
    @Test void taskLocalRateLimitIsBoundedAndRefills() {
        Clock clock = mock(Clock.class); when(clock.millis()).thenReturn(0L);
        var limiter = new EntitlementQueryRateLimiter(clock);
        for (int i = 0; i < 60; i++) { assertThat(limiter.allow("actor")).isTrue(); }
        assertThat(limiter.allow("actor")).isFalse();
        when(clock.millis()).thenReturn(1000L);
        assertThat(limiter.allow("actor")).isTrue();
        assertThat(limiter.allow("other")).isTrue();
    }

    @Test void independentOwnedGroupsAreNotCollapsedAndAmbiguousPhoneContextsArePending() {
        used(AttemptGroup.Status.OPEN, 1);
        var secondClaim = TrialClaim.active("claim-2", "FREE_EXAM_ONCE", "subject-2", "event-2", NOW, NOW.plusSeconds(10000));
        var secondLink = BillingSubjectLink.active("subject-2", "claim-2", "scope", "actor", NOW, NOW.plusSeconds(10000));
        var secondGrant = EntitlementGrant.unitGrant("grant-2", "FREE_EXAM_ONCE", "TRIAL_CLAIM", "claim-2", "subject-2", 1, NOW);
        set(secondGrant, "availableUnits", 0); set(secondGrant, "consumedUnits", 1);
        var secondGroup = AttemptGroup.open("group-2", "subject-2", "claim-2", "ledger-2", "mock-2", "session-2", NOW);
        var secondSession = AttemptSession.proposed("session-2", "group-2", "subject-2", "operation-2", NOW).withSessionOwnerEpoch(1);
        set(secondSession, "state", AttemptSession.State.ACTIVE);
        var secondReservation = Reservation.reserved("reservation-2", "subject-2", "operation-2", "hash-2", Reservation.Kind.INITIAL,
                "group-2", "session-2", "mock-2", NOW, NOW.plusSeconds(300)).withSessionOwnerEpoch(1);
        set(secondReservation, "status", Reservation.Status.CONFIRMED);
        var combined = new EntitlementQuerySnapshot("actor", NOW, definition, eligibility, "claim", List.of(claim, secondClaim),
                List.of(link, secondLink), List.of(grant, secondGrant), List.of(groups.getFirst(), secondGroup),
                List.of(reservations.getFirst(), secondReservation), List.of(sessions.getFirst(), secondSession), false);
        var result = evaluator.evaluate(combined);
        assertThat(result.attemptGroups()).hasSize(2);
        assertThat(result.attemptGroups().getFirst().retake()).isEqualTo(Action.ALLOWED);
        assertThat(result.attemptGroups().get(1).retake()).isEqualTo(Action.BLOCKED);
        set(link, "ownerTransitionReason", "PHONE_REJOIN"); set(secondLink, "ownerTransitionReason", "PHONE_REJOIN");
        assertThat(evaluator.evaluate(combined).retake()).isEqualTo(Action.PENDING);
        assertThat(evaluator.evaluate(combined).reasonCodes()).contains(Reason.AMBIGUOUS_ATTEMPT_CONTEXT);
    }

    @Test void benefitRegistryKeepsDifferentUnitsAndQuantitiesSeparate() {
        var executor = mock(web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor.class);
        when(executor.execute(any())).thenAnswer(i -> ((java.util.function.Supplier<?>) i.getArgument(0)).get());
        FreeBenefitQueryReader first = (actor, now) -> evaluator.evaluate(new EntitlementQuerySnapshot(actor, now, definition, eligibility,
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false));
        FreeBenefitQueryReader futureFixture = (actor, now) -> new Benefit("TEST_ONLY", "test", "OTHER_UNIT", 3,
                Eligibility.VERIFIED, Usage.NOT_STARTED, Action.ALLOWED, false, Action.BLOCKED, List.of(), List.of());
        var service = new EntitlementQueryService(List.of(futureFixture, first), executor, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.query("actor");
        assertThat(result.benefits()).extracting(Benefit::availableQuantity).containsExactly(1, 3);
        assertThat(result.benefits()).extracting(Benefit::unit).containsExactly("EXAM_ATTEMPT", "OTHER_UNIT");
    }
    EntitlementQuerySnapshot snapshot() {
        return new EntitlementQuerySnapshot("actor", NOW, definition, eligibility, "claim", List.of(claim), List.of(link),
                List.of(grant), groups, reservations, sessions, false);
    }
    void used(AttemptGroup.Status state, long epoch) {
        set(grant, "availableUnits", 0); set(grant, "consumedUnits", 1);
        var group = AttemptGroup.open("group", "subject", "claim", "ledger", "mock", "session", NOW);
        set(group, "status", state); groups = List.of(group);
        var session = AttemptSession.proposed("session", "group", "subject", "operation", NOW).withSessionOwnerEpoch(epoch);
        set(session, "state", AttemptSession.State.ACTIVE); sessions = List.of(session);
        var reservation = reservation().withSessionOwnerEpoch(epoch);
        set(reservation, "status", Reservation.Status.CONFIRMED); reservations = List.of(reservation);
    }
    Reservation reservation() { return Reservation.reserved("reservation", "subject", "operation", "hash", Reservation.Kind.INITIAL,
            "group", "session", "mock", NOW, NOW.plusSeconds(300)); }
    static void set(Object value, String field, Object content) { ReflectionTestUtils.setField(value, field, content); }
    static TrialEligibility verified() {
        var value = mock(TrialEligibility.class);
        when(value.getState()).thenReturn(TrialEligibilityState.VERIFIED);
        when(value.getCandidates()).thenReturn(List.of(new TrialEligibilityCandidate("v1", "candidate")));
        return value;
    }
}
