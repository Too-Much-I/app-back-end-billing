package web.tosunsaeng.billing.domain.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroupEventInbox;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventDisposition;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupFailureCode;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupCompletionEvidence;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupEventInboxRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptSessionRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.SubjectOwnerRebind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@ExtendWith(MockitoExtension.class)
class AttemptGroupEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final String GROUP_ID = "be07ae1d-f877-4ae4-82df-c5f442e9bb8e";
    private static final String SESSION_ID = "ex_a1b2c3d4e5_0826_1530";
    private static final String SUBJECT_ID = "subject-ref-1";
    private static final String CLAIM_ID = "claim-1";
    private static final String USER_ID = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    private static final String TARGET_USER_ID = "00000000-0000-4000-8000-000000000099";

    @Mock
    private AttemptGroupEventInboxRepository inboxRepository;
    @Mock
    private AttemptGroupRepository groupRepository;
    @Mock
    private AttemptSessionRepository sessionRepository;
    @Mock
    private BillingSubjectLinkRepository subjectLinkRepository;
    @Mock
    private SubjectOwnerRebindRepository ownerRebindRepository;
    @Mock
    private MongoTransactionExecutor transactionExecutor;
    @Mock
    private AttemptGroupEventMetrics metrics;
    @Mock
    private TraceCorrelation traceCorrelation;

    private AttemptGroupEventService service;

    @BeforeEach
    void setUp() {
        when(transactionExecutor.execute(any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(0);
            return operation.get();
        });
        when(traceCorrelation.currentTraceId()).thenReturn("0123456789abcdef0123456789abcdef");
        service = new AttemptGroupEventService(
                inboxRepository, groupRepository, sessionRepository, subjectLinkRepository,
                ownerRebindRepository,
                transactionExecutor, metrics, traceCorrelation,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void appliesOpenToGradingWithoutClosingSession() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        arrangeProjection(group, session, true);
        when(groupRepository.markGrading(GROUP_ID, SESSION_ID, 1, NOW))
                .thenReturn(Optional.of(group));

        assertThat(service.process(event(AttemptGroupEventTarget.GRADING, 1)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);

        verify(groupRepository).markGrading(GROUP_ID, SESSION_ID, 1, NOW);
        verify(sessionRepository, never()).completeActive(any(), any(), any(), anyLong(), any());
        assertInboxDisposition(AttemptGroupEventDisposition.APPLIED);
    }

    @Test
    void completesActiveSessionWithRequiredEvidence() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        arrangeProjection(group, session, true);
        when(groupRepository.markCompleted(
                eq(GROUP_ID), eq(SESSION_ID), eq(1L), any(), eq(NOW)
        )).thenReturn(Optional.of(group));
        when(sessionRepository.completeActive(
                eq(SESSION_ID), eq(GROUP_ID), eq(SUBJECT_ID), eq(2L), any()
        )).thenReturn(Optional.of(session));

        assertThat(service.process(event(AttemptGroupEventTarget.COMPLETED, 2)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);

        verify(sessionRepository).completeActive(
                eq(SESSION_ID), eq(GROUP_ID), eq(SUBJECT_ID), eq(2L), any()
        );
    }

    @Test
    void allowsAnonymousCompletionAfterSubjectLinkPurge() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
        when(subjectLinkRepository.findBySubjectRefId(SUBJECT_ID)).thenReturn(Optional.empty());
        when(groupRepository.markCompleted(any(), any(), anyLong(), any(), any()))
                .thenReturn(Optional.of(group));
        when(sessionRepository.completeActive(any(), any(), any(), anyLong(), any()))
                .thenReturn(Optional.of(session));

        assertThat(service.process(event(AttemptGroupEventTarget.COMPLETED, 3)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);
    }

    @Test
    void missingSubjectLinkMakesGradingStale() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
        when(subjectLinkRepository.findBySubjectRefId(SUBJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.process(event(AttemptGroupEventTarget.GRADING, 4)))
                .isEqualTo(AttemptGroupEventOutcome.STALE);

        verify(groupRepository, never()).markGrading(any(), any(), anyLong(), any());
        assertInboxDisposition(AttemptGroupEventDisposition.STALE);
    }

    @Test
    void retakeClosesSessionWithoutRestoringEntitlement() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        arrangeProjection(group, session, true);
        when(groupRepository.markRetakeAvailable(
                GROUP_ID, SESSION_ID, 1, NOW
        )).thenReturn(Optional.of(group));
        when(sessionRepository.failActive(
                eq(SESSION_ID), eq(GROUP_ID), eq(SUBJECT_ID), eq(2L), any()
        )).thenReturn(Optional.of(session));

        assertThat(service.process(event(AttemptGroupEventTarget.RETAKE_AVAILABLE, 5)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);

        verify(sessionRepository).failActive(
                eq(SESSION_ID), eq(GROUP_ID), eq(SUBJECT_ID), eq(2L), any()
        );
    }

    @Test
    void terminalOrOldSessionEventIsStale() {
        AttemptGroup group = AttemptGroup.projection(
                GROUP_ID, SUBJECT_ID, CLAIM_ID, "ledger-1", "mock-exam-01",
                AttemptGroup.Status.COMPLETED, NOW.minusSeconds(60)
        );
        AttemptSession session = mock(AttemptSession.class);
        when(session.getAttemptGroupId()).thenReturn(GROUP_ID);
        when(session.getSubjectRefId()).thenReturn(SUBJECT_ID);
        when(session.getState()).thenReturn(AttemptSession.State.COMPLETED);
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

        assertThat(service.process(event(AttemptGroupEventTarget.RETAKE_AVAILABLE, 6)))
                .isEqualTo(AttemptGroupEventOutcome.STALE);
        assertInboxDisposition(AttemptGroupEventDisposition.STALE);
    }

    @Test
    void sameEventIsDuplicateAndDifferentPayloadIsConflict() {
        AttemptGroupStatusEvent event = event(AttemptGroupEventTarget.GRADING, 7);
        when(inboxRepository.findByEventId(event.eventId())).thenReturn(Optional.of(
                AttemptGroupEventInbox.from(
                        event, AttemptGroupEventDisposition.APPLIED, NOW.minusSeconds(1)
                )
        ));

        assertThat(service.process(event)).isEqualTo(AttemptGroupEventOutcome.DUPLICATE);

        AttemptGroupStatusEvent changed = new AttemptGroupStatusEvent(
                event.eventId(), event.eventType(), event.schemaVersion(), event.producer(),
                event.occurredAt(), event.userId(), event.attemptGroupId(), event.sessionId(),
                event.targetStatus(), event.evidence(), event.failureCode(), "different"
        );
        assertThatThrownBy(() -> service.process(changed))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_ID_CONFLICT");
    }

    @Test
    void sameEventIdAndDigestFromIdentityProducerIsConflict() {
        AttemptGroupStatusEvent event = event(AttemptGroupEventTarget.GRADING, 10);
        when(inboxRepository.findByEventId(event.eventId())).thenReturn(Optional.of(
                AttemptGroupEventInbox.restored(
                        event.eventId(), "identity", "PhoneEligibilityBindingVerified",
                        1, event.payloadDigest(), AttemptGroupEventDisposition.APPLIED,
                        NOW.minusSeconds(1), NOW.plusSeconds(3600)
                )
        ));

        assertThatThrownBy(() -> service.process(event))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_ID_CONFLICT");
    }

    @Test
    void missingProjectionIsRetryableForFiveSeconds() {
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(event(AttemptGroupEventTarget.GRADING, 8)))
                .isInstanceOf(InternalApiException.class)
                .satisfies(exception -> {
                    InternalApiException api = (InternalApiException) exception;
                    assertThat(api.code()).isEqualTo("ATTEMPT_PROJECTION_NOT_READY");
                    assertThat(api.retryAfterSeconds()).isEqualTo(5);
                });
        verify(inboxRepository, never()).insert(any());
    }

    @Test
    void activeRelationshipMismatchIsNonRetryableConflict() {
        AttemptGroup group = openGroup();
        AttemptSession session = mock(AttemptSession.class);
        when(session.getAttemptGroupId()).thenReturn("other-group");
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.process(event(AttemptGroupEventTarget.GRADING, 9)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_TARGET_CONFLICT");
    }

    @Test
    void exactPreRebindSessionAllowsLateLegacySourceEvent() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        when(session.getProposedAt()).thenReturn(NOW.minusSeconds(60));
        arrangeProjectionWithCurrentOwner(group, session, TARGET_USER_ID);
        SubjectOwnerRebind fence = SubjectOwnerRebind.waitingTerminal(
                "00000000-0000-4000-8000-000000000090",
                OwnerRebindEventKind.USER_MERGED,
                SUBJECT_ID, CLAIM_ID, USER_ID, GROUP_ID, SESSION_ID,
                1, NOW.minusSeconds(30), NOW.plusSeconds(60)
        );
        when(ownerRebindRepository.findActiveFence(
                SUBJECT_ID, USER_ID, GROUP_ID, SESSION_ID, NOW
        )).thenReturn(Optional.of(fence));
        when(groupRepository.markGrading(GROUP_ID, SESSION_ID, 1, NOW))
                .thenReturn(Optional.of(group));

        assertThat(service.process(event(AttemptGroupEventTarget.GRADING, 14)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);

        verify(groupRepository).markGrading(GROUP_ID, SESSION_ID, 1, NOW);
        verify(ownerRebindRepository, never()).markTerminalDue(any(), any(), any(), any());
    }

    @Test
    void missingOrExpiredLegacyFenceRejectsOldOwnerEvent() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        arrangeProjectionWithCurrentOwner(group, session, TARGET_USER_ID);
        when(ownerRebindRepository.findActiveFence(
                SUBJECT_ID, USER_ID, GROUP_ID, SESSION_ID, NOW
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(event(AttemptGroupEventTarget.GRADING, 15)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_TARGET_CONFLICT");

        verify(groupRepository, never()).markGrading(any(), any(), anyLong(), any());
    }

    @Test
    void terminalLegacySourceEventMakesFenceImmediatelyDue() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        when(session.getProposedAt()).thenReturn(NOW.minusSeconds(60));
        arrangeProjectionWithCurrentOwner(group, session, TARGET_USER_ID);
        SubjectOwnerRebind fence = SubjectOwnerRebind.waitingTerminal(
                "00000000-0000-4000-8000-000000000091",
                OwnerRebindEventKind.USER_MERGED,
                SUBJECT_ID, CLAIM_ID, USER_ID, GROUP_ID, SESSION_ID,
                1, NOW.minusSeconds(30), NOW.plusSeconds(60)
        );
        when(ownerRebindRepository.findActiveFence(
                SUBJECT_ID, USER_ID, GROUP_ID, SESSION_ID, NOW
        )).thenReturn(Optional.of(fence));
        when(groupRepository.markCompleted(
                eq(GROUP_ID), eq(SESSION_ID), eq(1L), any(), eq(NOW)
        )).thenReturn(Optional.of(group));
        when(sessionRepository.completeActive(
                eq(SESSION_ID), eq(GROUP_ID), eq(SUBJECT_ID), eq(2L), any()
        )).thenReturn(Optional.of(session));

        AttemptGroupStatusEvent completed = event(AttemptGroupEventTarget.COMPLETED, 16);
        assertThat(service.process(completed)).isEqualTo(AttemptGroupEventOutcome.APPLIED);

        verify(ownerRebindRepository).markTerminalDue(
                SUBJECT_ID, GROUP_ID, SESSION_ID, completed.occurredAt()
        );
    }

    @Test
    void unknownCommitRechecksInboxAndConvergesToDuplicate() {
        AttemptGroupStatusEvent event = event(AttemptGroupEventTarget.GRADING, 11);
        MongoException unknownCommit = new MongoException(251, "unknown commit");
        unknownCommit.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        reset(transactionExecutor);
        doThrow(unknownCommit)
                .doAnswer(invocation -> {
                    Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                })
                .when(transactionExecutor).execute(any());
        when(inboxRepository.findByEventId(event.eventId())).thenReturn(Optional.of(
                AttemptGroupEventInbox.from(
                        event, AttemptGroupEventDisposition.APPLIED, NOW.minusSeconds(1)
                )
        ));

        assertThat(service.process(event)).isEqualTo(AttemptGroupEventOutcome.DUPLICATE);
    }

    @Test
    void transientTransactionFailureRetriesSameEvent() {
        AttemptGroup group = openGroup();
        AttemptSession session = activeSession();
        arrangeProjection(group, session, true);
        when(groupRepository.markGrading(GROUP_ID, SESSION_ID, 1, NOW))
                .thenReturn(Optional.of(group));
        MongoException transientFailure = new MongoException(251, "transient");
        transientFailure.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        reset(transactionExecutor);
        doThrow(transientFailure)
                .doAnswer(invocation -> {
                    Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                })
                .doAnswer(invocation -> {
                    Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                })
                .when(transactionExecutor).execute(any());

        assertThat(service.process(event(AttemptGroupEventTarget.GRADING, 12)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);
    }

    @Test
    void structuredLogContainsOperationalFieldsWithoutSensitiveIdentifiers() {
        AttemptGroupStatusEvent event = event(AttemptGroupEventTarget.GRADING, 13);
        when(inboxRepository.findByEventId(event.eventId())).thenReturn(Optional.of(
                AttemptGroupEventInbox.from(
                        event, AttemptGroupEventDisposition.APPLIED, NOW.minusSeconds(1)
                )
        ));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(AttemptGroupEventService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.process(event);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).hasSize(1);
        String message = appender.list.getFirst().getFormattedMessage();
        assertThat(message)
                .contains(
                        "service=billing",
                        "operation=attempt_group_event_consume",
                        "outcome=duplicate",
                        "traceId=0123456789abcdef0123456789abcdef",
                        "eventId=" + event.eventId(),
                        "durationMs=",
                        "eventAgeMs="
                )
                .doesNotContain(
                        event.userId(), event.attemptGroupId(), event.sessionId(),
                        event.payloadDigest()
                );
    }

    private void arrangeProjection(
            AttemptGroup group,
            AttemptSession session,
            boolean activeLink
    ) {
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
        if (activeLink) {
            when(subjectLinkRepository.findBySubjectRefId(SUBJECT_ID)).thenReturn(Optional.of(
                    BillingSubjectLink.active(
                            SUBJECT_ID, CLAIM_ID, "scope", USER_ID,
                            NOW.minusSeconds(60), NOW.plusSeconds(3600)
                    )
            ));
        }
    }

    private void arrangeProjectionWithCurrentOwner(
            AttemptGroup group,
            AttemptSession session,
            String currentOwner
    ) {
        when(inboxRepository.findByEventId(any())).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
        when(subjectLinkRepository.findBySubjectRefId(SUBJECT_ID)).thenReturn(Optional.of(
                BillingSubjectLink.active(
                        SUBJECT_ID, CLAIM_ID, "scope", currentOwner,
                        NOW.minusSeconds(60), NOW.plusSeconds(3600)
                )
        ));
    }

    private void assertInboxDisposition(AttemptGroupEventDisposition disposition) {
        ArgumentCaptor<AttemptGroupEventInbox> captor = ArgumentCaptor.forClass(
                AttemptGroupEventInbox.class
        );
        verify(inboxRepository).insert(captor.capture());
        assertThat(captor.getValue().getDisposition()).isEqualTo(disposition);
    }

    private static AttemptGroup openGroup() {
        return AttemptGroup.open(
                GROUP_ID, SUBJECT_ID, CLAIM_ID, "ledger-1", "mock-exam-01",
                SESSION_ID, NOW.minusSeconds(60)
        );
    }

    private static AttemptSession activeSession() {
        AttemptSession session = mock(AttemptSession.class);
        lenient().when(session.getSessionId()).thenReturn(SESSION_ID);
        when(session.getAttemptGroupId()).thenReturn(GROUP_ID);
        when(session.getSubjectRefId()).thenReturn(SUBJECT_ID);
        when(session.getState()).thenReturn(AttemptSession.State.ACTIVE);
        lenient().when(session.getVersion()).thenReturn(2L);
        return session;
    }

    private static AttemptGroupStatusEvent event(AttemptGroupEventTarget target, int suffix) {
        AttemptGroupCompletionEvidence evidence = target == AttemptGroupEventTarget.COMPLETED
                ? new AttemptGroupCompletionEvidence(true, true, true, 1)
                : null;
        AttemptGroupFailureCode failureCode = target == AttemptGroupEventTarget.RETAKE_AVAILABLE
                ? AttemptGroupFailureCode.SUMMARY_UNAVAILABLE
                : null;
        return new AttemptGroupStatusEvent(
                "00000000-0000-4000-8000-%012d".formatted(suffix),
                "AttemptGroupStatusChanged",
                1,
                "learning-core",
                NOW.minusSeconds(10),
                USER_ID,
                GROUP_ID,
                SESSION_ID,
                target,
                evidence,
                failureCode,
                "digest-" + suffix
        );
    }
}
