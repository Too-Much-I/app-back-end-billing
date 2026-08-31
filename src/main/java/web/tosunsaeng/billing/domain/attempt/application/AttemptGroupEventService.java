package web.tosunsaeng.billing.domain.attempt.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroupEventInbox;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventDisposition;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.exception.AttemptGroupEventException;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupEventInboxRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptSessionRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@Service
public class AttemptGroupEventService {

    private static final Logger log = LoggerFactory.getLogger(AttemptGroupEventService.class);
    private static final int MAX_TRANSACTION_ATTEMPTS = 3;
    private static final String OPERATION = "attempt_group_event_consume";

    private final AttemptGroupEventInboxRepository inboxRepository;
    private final AttemptGroupRepository groupRepository;
    private final AttemptSessionRepository sessionRepository;
    private final BillingSubjectLinkRepository subjectLinkRepository;
    private final MongoTransactionExecutor transactionExecutor;
    private final AttemptGroupEventMetrics metrics;
    private final TraceCorrelation traceCorrelation;
    private final Clock clock;

    public AttemptGroupEventService(
            AttemptGroupEventInboxRepository inboxRepository,
            AttemptGroupRepository groupRepository,
            AttemptSessionRepository sessionRepository,
            BillingSubjectLinkRepository subjectLinkRepository,
            MongoTransactionExecutor transactionExecutor,
            AttemptGroupEventMetrics metrics,
            TraceCorrelation traceCorrelation,
            Clock clock
    ) {
        this.inboxRepository = inboxRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.subjectLinkRepository = subjectLinkRepository;
        this.transactionExecutor = transactionExecutor;
        this.metrics = metrics;
        this.traceCorrelation = traceCorrelation;
        this.clock = clock;
    }

    public AttemptGroupEventOutcome process(AttemptGroupStatusEvent event) {
        long startedAt = System.nanoTime();
        Instant receivedAt = clock.instant();
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                ProcessingResult result = transactionExecutor.execute(
                        () -> processOnce(event, receivedAt)
                );
                if (result.terminalConflict()) {
                    metrics.recordTerminalConflict();
                }
                record(event, result.outcome().name(), startedAt, receivedAt);
                return result.outcome();
            } catch (InternalApiException exception) {
                record(event, outcomeFor(exception), startedAt, receivedAt);
                throw exception;
            } catch (DataAccessException | MongoException exception) {
                AttemptGroupEventOutcome convergence;
                try {
                    convergence = classifyCommitted(event);
                } catch (InternalApiException conflict) {
                    record(event, outcomeFor(conflict), startedAt, receivedAt);
                    throw conflict;
                }
                if (convergence != null) {
                    record(event, convergence.name(), startedAt, receivedAt);
                    return convergence;
                }
                if (!isRetryable(exception) && attempt == MAX_TRANSACTION_ATTEMPTS) {
                    break;
                }
            }
        }
        metrics.recordTransactionRetryExhausted();
        record(event, "TEMPORARY_FAILURE", startedAt, receivedAt);
        throw AttemptGroupEventException.temporarilyUnavailable();
    }

    private ProcessingResult processOnce(AttemptGroupStatusEvent event, Instant receivedAt) {
        Optional<AttemptGroupEventInbox> existing = inboxRepository.findByEventId(event.eventId());
        if (existing.isPresent()) {
            return sameEvent(existing.get(), event)
                    ? ProcessingResult.duplicate()
                    : eventIdConflict();
        }

        AttemptGroup group = groupRepository.findById(event.attemptGroupId())
                .orElseThrow(AttemptGroupEventException::projectionNotReady);
        AttemptSession session = sessionRepository.findBySessionId(event.sessionId())
                .orElseThrow(AttemptGroupEventException::projectionNotReady);

        validateRelationships(group, session, event);
        if (session.getState() != AttemptSession.State.ACTIVE) {
            return saveStale(event, group, receivedAt);
        }
        if (!event.sessionId().equals(group.getActiveSessionId())) {
            throw AttemptGroupEventException.targetConflict();
        }

        Optional<BillingSubjectLink> subjectLink = subjectLinkRepository.findBySubjectRefId(
                group.getSubjectRefId()
        );
        boolean activeLink = subjectLink
                .filter(BillingSubjectLink::isActive)
                .filter(link -> link.getRetentionExpiresAt().isAfter(receivedAt))
                .isPresent();
        if (activeLink) {
            BillingSubjectLink link = subjectLink.orElseThrow();
            if (!group.getTrialClaimId().equals(link.getTrialClaimId())
                    || !event.userId().equals(link.getUserId())) {
                throw AttemptGroupEventException.targetConflict();
            }
        } else if (event.targetStatus() != AttemptGroupEventTarget.COMPLETED) {
            return saveStale(event, group, receivedAt);
        }

        if (!canApply(group.getStatus(), event.targetStatus())) {
            return saveStale(event, group, receivedAt);
        }

        inboxRepository.insert(AttemptGroupEventInbox.from(
                event, AttemptGroupEventDisposition.APPLIED, receivedAt
        ));
        applyTransition(group, session, event, receivedAt);
        return ProcessingResult.applied();
    }

    private void applyTransition(
            AttemptGroup group,
            AttemptSession session,
            AttemptGroupStatusEvent event,
            Instant receivedAt
    ) {
        if (event.targetStatus() == AttemptGroupEventTarget.GRADING) {
            groupRepository.markGrading(
                    group.getAttemptGroupId(), session.getSessionId(),
                    group.getVersion(), receivedAt
            ).orElseThrow(AttemptGroupEventService::concurrentUpdate);
            return;
        }
        if (event.targetStatus() == AttemptGroupEventTarget.COMPLETED) {
            groupRepository.markCompleted(
                    group.getAttemptGroupId(), session.getSessionId(), group.getVersion(),
                    event.occurredAt(), receivedAt
            ).orElseThrow(AttemptGroupEventService::concurrentUpdate);
            sessionRepository.completeActive(
                    session.getSessionId(), group.getAttemptGroupId(), group.getSubjectRefId(),
                    session.getVersion(), event.occurredAt()
            ).orElseThrow(AttemptGroupEventService::concurrentUpdate);
            return;
        }
        groupRepository.markRetakeAvailable(
                group.getAttemptGroupId(), session.getSessionId(),
                group.getVersion(), receivedAt
        ).orElseThrow(AttemptGroupEventService::concurrentUpdate);
        sessionRepository.failActive(
                session.getSessionId(), group.getAttemptGroupId(), group.getSubjectRefId(),
                session.getVersion(), event.occurredAt()
        ).orElseThrow(AttemptGroupEventService::concurrentUpdate);
    }

    private ProcessingResult saveStale(
            AttemptGroupStatusEvent event,
            AttemptGroup group,
            Instant receivedAt
    ) {
        inboxRepository.insert(AttemptGroupEventInbox.from(
                event, AttemptGroupEventDisposition.STALE, receivedAt
        ));
        boolean terminalConflict = (group.getStatus() == AttemptGroup.Status.COMPLETED
                && event.targetStatus() == AttemptGroupEventTarget.RETAKE_AVAILABLE)
                || (group.getStatus() == AttemptGroup.Status.RETAKE_AVAILABLE
                && event.targetStatus() == AttemptGroupEventTarget.COMPLETED);
        return ProcessingResult.stale(terminalConflict);
    }

    private AttemptGroupEventOutcome classifyCommitted(AttemptGroupStatusEvent event) {
        try {
            return transactionExecutor.execute(() -> {
                Optional<AttemptGroupEventInbox> inbox = inboxRepository.findByEventId(
                        event.eventId()
                );
                if (inbox.isEmpty()) {
                    return null;
                }
                if (sameEvent(inbox.get(), event)) {
                    return AttemptGroupEventOutcome.DUPLICATE;
                }
                throw AttemptGroupEventException.eventIdConflict();
            });
        } catch (InternalApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void record(
            AttemptGroupStatusEvent event,
            String outcome,
            long startedAt,
            Instant receivedAt
    ) {
        long durationNanos = Math.max(0, System.nanoTime() - startedAt);
        long durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(durationNanos);
        long eventAgeMs = Math.max(0, Duration.between(event.occurredAt(), receivedAt).toMillis());
        metrics.record(event, outcome, durationNanos, receivedAt);
        log.info(
                "service=billing operation={} outcome={} traceId={} eventId={} durationMs={} eventAgeMs={}",
                OPERATION,
                outcome.toLowerCase(java.util.Locale.ROOT),
                traceCorrelation.currentTraceId(),
                event.eventId(),
                durationMs,
                eventAgeMs
        );
    }

    private static void validateRelationships(
            AttemptGroup group,
            AttemptSession session,
            AttemptGroupStatusEvent event
    ) {
        if (!event.attemptGroupId().equals(group.getAttemptGroupId())
                || !group.getAttemptGroupId().equals(session.getAttemptGroupId())
                || !group.getSubjectRefId().equals(session.getSubjectRefId())) {
            throw AttemptGroupEventException.targetConflict();
        }
    }

    private static boolean canApply(
            AttemptGroup.Status current,
            AttemptGroupEventTarget target
    ) {
        if (current == AttemptGroup.Status.OPEN) {
            return true;
        }
        return current == AttemptGroup.Status.GRADING
                && target != AttemptGroupEventTarget.GRADING;
    }

    private static boolean sameEvent(
            AttemptGroupEventInbox inbox,
            AttemptGroupStatusEvent event
    ) {
        return event.producer().equals(inbox.getProducer())
                && event.payloadDigest().equals(inbox.getPayloadDigest());
    }

    private static ProcessingResult eventIdConflict() {
        throw AttemptGroupEventException.eventIdConflict();
    }

    private static OptimisticLockingFailureException concurrentUpdate() {
        return new OptimisticLockingFailureException("Attempt projection changed concurrently.");
    }

    private static String outcomeFor(InternalApiException exception) {
        return switch (exception.code()) {
            case "EVENT_ID_CONFLICT", "EVENT_TARGET_CONFLICT" -> "CONFLICT";
            case "ATTEMPT_PROJECTION_NOT_READY" -> "PROJECTION_NOT_READY";
            default -> "TEMPORARY_FAILURE";
        };
    }

    private static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException) {
                return true;
            }
            if (current instanceof MongoException mongoException) {
                return mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                        || mongoException.hasErrorLabel(
                        MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL
                );
            }
            current = current.getCause();
        }
        return false;
    }

    private record ProcessingResult(
            AttemptGroupEventOutcome outcome,
            boolean terminalConflict
    ) {
        private static ProcessingResult applied() {
            return new ProcessingResult(AttemptGroupEventOutcome.APPLIED, false);
        }

        private static ProcessingResult duplicate() {
            return new ProcessingResult(AttemptGroupEventOutcome.DUPLICATE, false);
        }

        private static ProcessingResult stale(boolean terminalConflict) {
            return new ProcessingResult(AttemptGroupEventOutcome.STALE, terminalConflict);
        }
    }

}
