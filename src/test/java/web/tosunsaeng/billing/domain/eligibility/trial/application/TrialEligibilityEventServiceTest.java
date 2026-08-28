package web.tosunsaeng.billing.domain.eligibility.trial.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mongodb.MongoException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.InboundEventDisposition;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.InboundEventInbox;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityCandidate;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityEventType;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.InboundEventInboxRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository;

class TrialEligibilityEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private InboundEventInboxRepository inboxRepository;
    private TrialEligibilityRepository eligibilityRepository;
    private MongoTransactionExecutor transactionExecutor;
    private TrialEligibilityEventService service;

    @BeforeEach
    void setUp() {
        inboxRepository = mock(InboundEventInboxRepository.class);
        eligibilityRepository = mock(TrialEligibilityRepository.class);
        transactionExecutor = mock(MongoTransactionExecutor.class);
        when(transactionExecutor.execute(any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(0);
            return operation.get();
        });
        service = new TrialEligibilityEventService(
                inboxRepository,
                eligibilityRepository,
                transactionExecutor,
                new TrialEligibilityMetrics(new SimpleMeterRegistry()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void appliesNewEventToInboxAndProjection() {
        TrialEligibilityEvent event = event(1, "00000000-0000-4000-8000-000000000001", "digest-1");

        assertThat(service.process(event)).isEqualTo(TrialEligibilityEventOutcome.APPLIED);

        verify(inboxRepository).insert(any(InboundEventInbox.class));
        verify(eligibilityRepository).replace(any(TrialEligibility.class));
    }

    @Test
    void storesStaleInboxWithoutChangingProjection() {
        TrialEligibilityEvent currentEvent = event(2, "00000000-0000-4000-8000-000000000002", "digest-2");
        TrialEligibility current = TrialEligibility.applied(null, currentEvent, NOW);
        when(eligibilityRepository.findByScopeAndUser(any(), any())).thenReturn(Optional.of(current));

        assertThat(service.process(event(1, "00000000-0000-4000-8000-000000000001", "digest-1")))
                .isEqualTo(TrialEligibilityEventOutcome.STALE);

        verify(inboxRepository).insert(any(InboundEventInbox.class));
        verify(eligibilityRepository, never()).replace(any());
    }

    @Test
    void sameEventAndDigestIsDuplicateNoOp() {
        TrialEligibilityEvent event = event(1, "00000000-0000-4000-8000-000000000001", "digest-1");
        when(inboxRepository.findByEventId(event.eventId())).thenReturn(Optional.of(
                InboundEventInbox.from(event, InboundEventDisposition.APPLIED, NOW)
        ));

        assertThat(service.process(event)).isEqualTo(TrialEligibilityEventOutcome.DUPLICATE);
        verify(inboxRepository, never()).insert(any());
        verify(eligibilityRepository, never()).replace(any());
    }

    @Test
    void sameEventWithDifferentDigestIsConflict() {
        TrialEligibilityEvent original = event(
                1, "00000000-0000-4000-8000-000000000001", "digest-1"
        );
        when(inboxRepository.findByEventId(original.eventId())).thenReturn(Optional.of(
                InboundEventInbox.from(original, InboundEventDisposition.APPLIED, NOW)
        ));

        assertThatThrownBy(() -> service.process(event(
                1, original.eventId(), "different-digest"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("EVENT_ID_CONFLICT");
    }

    @Test
    void sameRevisionWithDifferentEventIsConflict() {
        TrialEligibilityEvent original = event(
                1, "00000000-0000-4000-8000-000000000001", "digest-1"
        );
        when(inboxRepository.findByIdentityRevision(any())).thenReturn(Optional.of(
                InboundEventInbox.from(original, InboundEventDisposition.APPLIED, NOW)
        ));

        assertThatThrownBy(() -> service.process(event(
                1, "00000000-0000-4000-8000-000000000099", "digest-99"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("EVENT_ID_CONFLICT");
    }

    @Test
    void unknownCommitResultRechecksCommittedInboxAndConvergesToDuplicate() {
        TrialEligibilityEvent event = event(
                1, "00000000-0000-4000-8000-000000000001", "digest-1"
        );
        MongoException unknownCommit = new MongoException(251, "unknown commit result");
        unknownCommit.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        reset(transactionExecutor);
        doThrow(unknownCommit)
                .doAnswer(invocation -> {
                    Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                })
                .when(transactionExecutor).execute(any());
        when(inboxRepository.findByEventId(event.eventId())).thenReturn(Optional.of(
                InboundEventInbox.from(event, InboundEventDisposition.APPLIED, NOW)
        ));

        assertThat(service.process(event)).isEqualTo(TrialEligibilityEventOutcome.DUPLICATE);
    }

    @Test
    void transientTransactionErrorRetriesSameImmutableEvent() {
        TrialEligibilityEvent event = event(
                1, "00000000-0000-4000-8000-000000000001", "digest-1"
        );
        MongoException transientFailure = new MongoException(251, "transient transaction");
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

        assertThat(service.process(event)).isEqualTo(TrialEligibilityEventOutcome.APPLIED);
        verify(inboxRepository).insert(any(InboundEventInbox.class));
    }

    private static TrialEligibilityEvent event(long revision, String eventId, String digest) {
        return new TrialEligibilityEvent(
                eventId,
                TrialEligibilityEventType.VERIFIED,
                1,
                "identity",
                NOW,
                "opaque-scope-v1",
                "e8b37a41-bae6-47f1-a770-052e6c5786d4",
                NOW.minusSeconds(1),
                null,
                revision,
                List.of(new TrialEligibilityCandidate(
                        "v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                )),
                digest
        );
    }
}
