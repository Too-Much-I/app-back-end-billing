package web.tosunsaeng.billing.domain.ownerrebind.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptSessionRepository;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityCandidate;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityState;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialCandidateAlias;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialCandidateAliasRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.OwnerRebindInbox;
import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.SubjectOwnerRebind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindDisposition;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.ownerrebind.repository.OwnerRebindInboxRepository;
import web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.repository.IdempotencyCommandRepository;
import web.tosunsaeng.billing.domain.reservation.repository.ReservationRepository;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@ExtendWith(MockitoExtension.class)
class OwnerRebindServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T05:00:00Z");
    private static final String SOURCE = "00000000-0000-4000-8000-000000000002";
    private static final String TARGET = "00000000-0000-4000-8000-000000000003";
    private static final String SUBJECT = "subject-ref-1";
    private static final String CLAIM = "claim-1";
    private static final String SCOPE = "opaque-scope-v1";

    @Mock private OwnerRebindInboxRepository inboxRepository;
    @Mock private SubjectOwnerRebindRepository rebindRepository;
    @Mock private BillingSubjectLinkRepository subjectLinkRepository;
    @Mock private TrialEligibilityRepository eligibilityRepository;
    @Mock private TrialCandidateAliasRepository aliasRepository;
    @Mock private TrialClaimRepository claimRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private IdempotencyCommandRepository commandRepository;
    @Mock private AttemptGroupRepository groupRepository;
    @Mock private AttemptSessionRepository sessionRepository;
    @Mock private MongoTransactionExecutor transactionExecutor;
    @Mock private OwnerRebindMetrics metrics;
    @Mock private TraceCorrelation traceCorrelation;

    private OwnerRebindService service;
    private BillingSubjectLink link;

    @BeforeEach
    void setUp() {
        when(transactionExecutor.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(traceCorrelation.currentTraceId()).thenReturn("0123456789abcdef0123456789abcdef");
        OwnerRebindProperties properties = new OwnerRebindProperties();
        service = new OwnerRebindService(
                inboxRepository, rebindRepository, subjectLinkRepository,
                eligibilityRepository, aliasRepository, claimRepository,
                reservationRepository, commandRepository, groupRepository,
                sessionRepository, transactionExecutor, properties, metrics,
                traceCorrelation, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        link = BillingSubjectLink.active(
                SUBJECT, CLAIM, SCOPE, SOURCE, NOW.minusSeconds(60), NOW.plusSeconds(3600)
        );
    }

    @Test
    void guestMergeMovesOnlyCurrentOwnerMapping() {
        arrangeOneActiveLink();
        when(subjectLinkRepository.rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("USER_MERGED"), any()
        )).thenReturn(Optional.of(link));

        assertThat(service.process(guestCommand(1))).isEqualTo(OwnerRebindOutcome.APPLIED);

        verify(subjectLinkRepository).rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("USER_MERGED"), any()
        );
        ArgumentCaptor<OwnerRebindInbox> inbox = ArgumentCaptor.forClass(OwnerRebindInbox.class);
        verify(inboxRepository).insert(inbox.capture());
        assertThat(inbox.getValue().getDisposition()).isEqualTo(OwnerRebindDisposition.APPLIED);
        assertThat(inbox.getValue().getAffectedSubjectCount()).isEqualTo(1);
    }

    @Test
    void noRetainedSubjectIsNoopAndCreatesNoRight() {
        when(subjectLinkRepository.findActiveByOwner(SOURCE, NOW, 101)).thenReturn(List.of());

        assertThat(service.process(guestCommand(2))).isEqualTo(OwnerRebindOutcome.NOOP);

        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
        ArgumentCaptor<OwnerRebindInbox> inbox = ArgumentCaptor.forClass(OwnerRebindInbox.class);
        verify(inboxRepository).insert(inbox.capture());
        assertThat(inbox.getValue().getDisposition()).isEqualTo(OwnerRebindDisposition.NOOP);
    }

    @Test
    void moreThanOneHundredSubjectsFailsWithoutPartialMove() {
        when(subjectLinkRepository.findActiveByOwner(SOURCE, NOW, 101))
                .thenReturn(java.util.Collections.nCopies(101, link));

        assertThatThrownBy(() -> service.process(guestCommand(3)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("OWNER_REBIND_CONFLICT");

        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
        verify(metrics).recordInvariantViolation("subject_limit");
    }

    @Test
    void activeReservationReturnsBoundedPendingWithoutOwnerWrite() {
        arrangeOneActiveLink();
        Reservation reservation = Reservation.reserved(
                "reservation-1", SUBJECT, "operation-1", "hash",
                Reservation.Kind.INITIAL, "group-1", "session-1", "mock-1",
                NOW.minusSeconds(10), NOW.plusSeconds(900)
        );
        when(reservationRepository.findActiveBySubjects(List.of(SUBJECT)))
                .thenReturn(List.of(reservation));

        assertThatThrownBy(() -> service.process(guestCommand(4)))
                .isInstanceOf(InternalApiException.class)
                .satisfies(exception -> {
                    InternalApiException api = (InternalApiException) exception;
                    assertThat(api.code()).isEqualTo("OWNER_REBIND_PENDING");
                    assertThat(api.retryAfterSeconds()).isEqualTo(300);
                });
        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
    }

    @Test
    void processingReserveCommandKeepsWholeTransferPending() {
        arrangeOneActiveLink();
        when(commandRepository.existsProcessingReserve(SOURCE)).thenReturn(true);

        assertThatThrownBy(() -> service.process(guestCommand(8)))
                .isInstanceOf(InternalApiException.class)
                .satisfies(exception -> {
                    InternalApiException api = (InternalApiException) exception;
                    assertThat(api.code()).isEqualTo("OWNER_REBIND_PENDING");
                    assertThat(api.retryAfterSeconds()).isEqualTo(5);
                });

        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
        verify(inboxRepository, never()).insert(any());
    }

    @Test
    void activePreRebindSessionCreatesBoundedLegacyFence() {
        arrangeOneActiveLink();
        AttemptGroup group = AttemptGroup.open(
                "group-1", SUBJECT, CLAIM, "ledger-1", "mock-1",
                "session-1", NOW.minusSeconds(60)
        );
        AttemptSession session = org.mockito.Mockito.mock(AttemptSession.class);
        when(groupRepository.findNonTerminalBySubject(SUBJECT)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(session.getState()).thenReturn(AttemptSession.State.ACTIVE);
        when(session.getProposedAt()).thenReturn(NOW.minusSeconds(60));
        when(session.getSessionId()).thenReturn("session-1");
        when(subjectLinkRepository.rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("USER_MERGED"), any()
        )).thenReturn(Optional.of(link));

        assertThat(service.process(guestCommand(9))).isEqualTo(OwnerRebindOutcome.APPLIED);

        ArgumentCaptor<SubjectOwnerRebind> fence = ArgumentCaptor.forClass(
                SubjectOwnerRebind.class
        );
        verify(rebindRepository).insert(fence.capture());
        assertThat(fence.getValue().getSubjectRefId()).isEqualTo(SUBJECT);
        assertThat(fence.getValue().getAttemptGroupId()).isEqualTo("group-1");
        assertThat(fence.getValue().getSessionId()).isEqualTo("session-1");
        assertThat(fence.getValue().getAppliedAt()).isEqualTo(NOW);
        assertThat(fence.getValue().getLegacyFenceExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void phoneRejoinWaitsForRequiredProjectionRevision() {
        when(subjectLinkRepository.findActiveByOwnerAndScope(SOURCE, SCOPE, NOW, 101))
                .thenReturn(List.of(link));
        arrangeClaim();
        TrialEligibility source = org.mockito.Mockito.mock(TrialEligibility.class);
        TrialEligibility target = org.mockito.Mockito.mock(TrialEligibility.class);
        when(source.getBindingRevision()).thenReturn(1L);
        when(eligibilityRepository.findByScopeAndUser(SCOPE, SOURCE))
                .thenReturn(Optional.of(source));
        when(eligibilityRepository.findByScopeAndUser(SCOPE, TARGET))
                .thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.process(phoneCommand(5, 2, 1)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("OWNER_REBIND_PENDING");
    }

    @Test
    void phoneRejoinRequiresVerifiedCandidateToMatchRetainedClaim() {
        arrangePhoneReady();
        when(subjectLinkRepository.rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("PHONE_REJOIN"), any()
        )).thenReturn(Optional.of(link));

        assertThat(service.process(phoneCommand(6, 2, 1)))
                .isEqualTo(OwnerRebindOutcome.APPLIED);
    }

    @Test
    void phoneRejoinWithOpenAttemptMovesOwnerForSameGroupReplacement() {
        arrangePhoneReady();
        AttemptGroup group = AttemptGroup.open(
                "group-open", SUBJECT, CLAIM, "ledger-consumed", "mock-1",
                "session-old", NOW.minusSeconds(120)
        );
        when(groupRepository.findByClaimIds(List.of(CLAIM))).thenReturn(List.of(group));
        when(groupRepository.findNonTerminalBySubject(SUBJECT)).thenReturn(Optional.of(group));
        when(subjectLinkRepository.rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("PHONE_REJOIN"), any()
        )).thenReturn(Optional.of(link));

        assertThat(service.process(phoneCommand(10, 2, 1)))
                .isEqualTo(OwnerRebindOutcome.APPLIED);

        verify(subjectLinkRepository).rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("PHONE_REJOIN"), any()
        );
    }

    @Test
    void phoneRejoinWithRetakeAvailableMovesOwnerWithoutNewConsumption() {
        arrangePhoneReady();
        AttemptGroup group = AttemptGroup.projection(
                "group-retake", SUBJECT, CLAIM, "ledger-consumed", "mock-1",
                AttemptGroup.Status.RETAKE_AVAILABLE, NOW.minusSeconds(120)
        );
        when(groupRepository.findByClaimIds(List.of(CLAIM))).thenReturn(List.of(group));
        when(subjectLinkRepository.rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("PHONE_REJOIN"), any()
        )).thenReturn(Optional.of(link));

        assertThat(service.process(phoneCommand(11, 2, 1)))
                .isEqualTo(OwnerRebindOutcome.APPLIED);

        verify(subjectLinkRepository).rebindOwner(
                eq(link), eq(TARGET), eq(NOW), eq("PHONE_REJOIN"), any()
        );
    }

    @Test
    void phoneRejoinWaitsWhileAttemptIsGrading() {
        arrangePhoneReady();
        AttemptGroup group = AttemptGroup.projection(
                "group-grading", SUBJECT, CLAIM, "ledger-consumed", "mock-1",
                AttemptGroup.Status.GRADING, NOW.minusSeconds(120)
        );
        when(groupRepository.findByClaimIds(List.of(CLAIM))).thenReturn(List.of(group));

        assertThatThrownBy(() -> service.process(phoneCommand(12, 2, 1)))
                .isInstanceOf(InternalApiException.class)
                .satisfies(exception -> {
                    InternalApiException api = (InternalApiException) exception;
                    assertThat(api.code()).isEqualTo("OWNER_REBIND_PENDING");
                    assertThat(api.retryAfterSeconds()).isEqualTo(5);
                });

        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
        verify(rebindRepository, never()).insert(any());
        verify(inboxRepository, never()).insert(any());
    }

    @Test
    void phoneRejoinWithCompletedAttemptIsSuccessfulNoop() {
        arrangePhoneReady();
        AttemptGroup group = AttemptGroup.projection(
                "group-completed", SUBJECT, CLAIM, "ledger-consumed", "mock-1",
                AttemptGroup.Status.COMPLETED, NOW.minusSeconds(120)
        );
        when(groupRepository.findByClaimIds(List.of(CLAIM))).thenReturn(List.of(group));

        assertThat(service.process(phoneCommand(13, 2, 1)))
                .isEqualTo(OwnerRebindOutcome.NOOP);

        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
        verify(rebindRepository, never()).insert(any());
        ArgumentCaptor<OwnerRebindInbox> inbox = ArgumentCaptor.forClass(OwnerRebindInbox.class);
        verify(inboxRepository).insert(inbox.capture());
        assertThat(inbox.getValue().getDisposition()).isEqualTo(OwnerRebindDisposition.NOOP);
        assertThat(inbox.getValue().getAffectedSubjectCount()).isZero();
    }

    @Test
    void completedAttemptNoopReplayIsDuplicate() {
        OwnerRebindCommand command = phoneCommand(14, 2, 1);
        OwnerRebindInbox existing = OwnerRebindInbox.processed(
                command, OwnerRebindDisposition.NOOP, 0,
                NOW.minusSeconds(1), NOW.plusSeconds(3600)
        );
        when(inboxRepository.findByEventId(command.eventId())).thenReturn(Optional.of(existing));

        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.DUPLICATE);

        verify(subjectLinkRepository, never()).rebindOwner(any(), any(), any(), any(), any());
    }

    @Test
    void exactReplayUsesExistingCommitAndDifferentDigestConflicts() {
        OwnerRebindCommand command = guestCommand(7);
        OwnerRebindInbox existing = OwnerRebindInbox.processed(
                command, OwnerRebindDisposition.APPLIED, 1,
                NOW.minusSeconds(1), NOW.plusSeconds(3600)
        );
        when(inboxRepository.findByEventId(command.eventId())).thenReturn(Optional.of(existing));

        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.DUPLICATE);

        OwnerRebindCommand changed = new OwnerRebindCommand(
                command.eventId(), command.eventKind(), command.schemaVersion(),
                command.occurredAt(), command.sourceUserId(), command.targetUserId(),
                null, null, null, "different"
        );
        assertThatThrownBy(() -> service.process(changed))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_ID_CONFLICT");
    }

    private void arrangeOneActiveLink() {
        when(subjectLinkRepository.findActiveByOwner(SOURCE, NOW, 101))
                .thenReturn(List.of(link));
        arrangeClaim();
    }

    private void arrangeClaim() {
        when(claimRepository.findById(CLAIM)).thenReturn(Optional.of(TrialClaim.active(
                CLAIM, BenefitDefinition.FREE_EXAM_ONCE, SUBJECT, "event-source",
                NOW.minusSeconds(60), NOW.plusSeconds(3600)
        )));
    }

    private void arrangePhoneReady() {
        when(subjectLinkRepository.findActiveByOwnerAndScope(SOURCE, SCOPE, NOW, 101))
                .thenReturn(List.of(link));
        arrangeClaim();
        TrialEligibility source = eligibility(2, TrialEligibilityState.REVOKED, null);
        TrialEligibility target = eligibility(1, TrialEligibilityState.VERIFIED, candidates());
        when(eligibilityRepository.findByScopeAndUser(SCOPE, SOURCE))
                .thenReturn(Optional.of(source));
        when(eligibilityRepository.findByScopeAndUser(SCOPE, TARGET))
                .thenReturn(Optional.of(target));
        when(aliasRepository.findActiveByClaim(
                CLAIM, BenefitDefinition.FREE_EXAM_ONCE, NOW
        )).thenReturn(List.of(TrialCandidateAlias.active(
                "alias-1", BenefitDefinition.FREE_EXAM_ONCE, "v1",
                "candidate-value", CLAIM, NOW.minusSeconds(60), NOW.plusSeconds(3600)
        )));
    }

    private static TrialEligibility eligibility(
            long revision,
            TrialEligibilityState state,
            List<TrialEligibilityCandidate> candidates
    ) {
        TrialEligibility eligibility = org.mockito.Mockito.mock(TrialEligibility.class);
        when(eligibility.getBindingRevision()).thenReturn(revision);
        when(eligibility.getState()).thenReturn(state);
        if (candidates != null) {
            when(eligibility.getCandidates()).thenReturn(candidates);
        }
        return eligibility;
    }

    private static List<TrialEligibilityCandidate> candidates() {
        return List.of(new TrialEligibilityCandidate("v1", "candidate-value"));
    }

    private static OwnerRebindCommand guestCommand(int suffix) {
        return new OwnerRebindCommand(
                eventId(suffix), OwnerRebindEventKind.USER_MERGED, 1,
                NOW.minusSeconds(10), SOURCE, TARGET,
                null, null, null, "digest-" + suffix
        );
    }

    private static OwnerRebindCommand phoneCommand(
            int suffix,
            long sourceRevision,
            long targetRevision
    ) {
        return new OwnerRebindCommand(
                eventId(suffix), OwnerRebindEventKind.PHONE_REJOIN, 1,
                NOW.minusSeconds(10), SOURCE, TARGET, SCOPE,
                sourceRevision, targetRevision, "digest-" + suffix
        );
    }

    private static String eventId(int suffix) {
        return "00000000-0000-4000-8000-%012d".formatted(suffix);
    }
}
