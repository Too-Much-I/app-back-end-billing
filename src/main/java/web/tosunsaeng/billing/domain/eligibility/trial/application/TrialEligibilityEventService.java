package web.tosunsaeng.billing.domain.eligibility.trial.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.mongodb.MongoException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.eligibility.trial.exception.TrialEligibilityException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.InboundEventDisposition;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.InboundEventInbox;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.InboundEventInboxRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository;

@Service
public class TrialEligibilityEventService {

    private static final int MAX_TRANSACTION_ATTEMPTS = 3;

    private final InboundEventInboxRepository inboxRepository;
    private final TrialEligibilityRepository eligibilityRepository;
    private final MongoTransactionExecutor transactionExecutor;
    private final TrialEligibilityMetrics metrics;
    private final Clock clock;

    public TrialEligibilityEventService(
            InboundEventInboxRepository inboxRepository,
            TrialEligibilityRepository eligibilityRepository,
            MongoTransactionExecutor transactionExecutor,
            TrialEligibilityMetrics metrics,
            Clock clock
    ) {
        this.inboxRepository = inboxRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.transactionExecutor = transactionExecutor;
        this.metrics = metrics;
        this.clock = clock;
    }

    public TrialEligibilityEventOutcome process(TrialEligibilityEvent event) {
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                TrialEligibilityEventOutcome outcome = transactionExecutor.execute(() -> processOnce(event));
                metrics.record(event, outcome.name());
                return outcome;
            } catch (InternalApiException exception) {
                if ("EVENT_ID_CONFLICT".equals(exception.code())) {
                    metrics.record(event, "CONFLICT");
                }
                throw exception;
            } catch (DuplicateKeyException exception) {
                TrialEligibilityEventOutcome convergence = classifyCommitted(event);
                if (convergence != null) {
                    metrics.record(event, convergence.name());
                    return convergence;
                }
            } catch (DataAccessException | MongoException exception) {
                TrialEligibilityEventOutcome convergence = classifyCommitted(event);
                if (convergence != null) {
                    metrics.record(event, convergence.name());
                    return convergence;
                }
                if (!isRetryable(exception) && attempt == MAX_TRANSACTION_ATTEMPTS) {
                    break;
                }
            }
        }
        metrics.record(event, "TEMPORARY_FAILURE");
        metrics.recordTransactionRetryExhausted();
        throw TrialEligibilityException.temporarilyUnavailable();
    }

    private TrialEligibilityEventOutcome processOnce(TrialEligibilityEvent event) {
        Optional<InboundEventInbox> byEventId = inboxRepository.findByEventId(event.eventId());
        if (byEventId.isPresent()) {
            return sameDigest(byEventId.get(), event)
                    ? TrialEligibilityEventOutcome.DUPLICATE
                    : conflict();
        }

        Optional<InboundEventInbox> byRevision = inboxRepository.findByIdentityRevision(event);
        if (byRevision.isPresent()) {
            return byRevision.get().getEventId().equals(event.eventId())
                    && sameDigest(byRevision.get(), event)
                    ? TrialEligibilityEventOutcome.DUPLICATE
                    : conflict();
        }

        TrialEligibility current = eligibilityRepository
                .findByScopeAndUser(event.consumerScopeId(), event.userId())
                .orElse(null);
        if (current != null && event.bindingRevision() == current.getBindingRevision()) {
            return current.getLastEventId().equals(event.eventId())
                    && current.getLastPayloadDigest().equals(event.payloadDigest())
                    ? TrialEligibilityEventOutcome.DUPLICATE
                    : conflict();
        }

        Instant receivedAt = clock.instant();
        if (current != null && event.bindingRevision() < current.getBindingRevision()) {
            inboxRepository.insert(InboundEventInbox.from(
                    event, InboundEventDisposition.STALE, receivedAt
            ));
            return TrialEligibilityEventOutcome.STALE;
        }

        if ((current == null && event.bindingRevision() > 1)
                || (current != null && event.bindingRevision() > current.getBindingRevision() + 1)) {
            metrics.recordRevisionGap();
        }
        inboxRepository.insert(InboundEventInbox.from(
                event, InboundEventDisposition.APPLIED, receivedAt
        ));
        eligibilityRepository.replace(TrialEligibility.applied(current, event, receivedAt));
        return TrialEligibilityEventOutcome.APPLIED;
    }

    private TrialEligibilityEventOutcome classifyCommitted(TrialEligibilityEvent event) {
        try {
            return transactionExecutor.execute(() -> {
                Optional<InboundEventInbox> byEventId = inboxRepository.findByEventId(event.eventId());
                if (byEventId.isPresent()) {
                    return sameDigest(byEventId.get(), event)
                            ? TrialEligibilityEventOutcome.DUPLICATE
                            : conflict();
                }
                Optional<InboundEventInbox> byRevision = inboxRepository.findByIdentityRevision(event);
                if (byRevision.isPresent()) {
                    return byRevision.get().getEventId().equals(event.eventId())
                            && sameDigest(byRevision.get(), event)
                            ? TrialEligibilityEventOutcome.DUPLICATE
                            : conflict();
                }
                return null;
            });
        } catch (InternalApiException exception) {
            if ("EVENT_ID_CONFLICT".equals(exception.code())) {
                metrics.record(event, "CONFLICT");
            }
            throw exception;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean sameDigest(InboundEventInbox inbox, TrialEligibilityEvent event) {
        return inbox.getPayloadDigest().equals(event.payloadDigest());
    }

    private static TrialEligibilityEventOutcome conflict() {
        throw TrialEligibilityException.eventConflict();
    }

    private static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MongoException mongoException) {
                return mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                        || mongoException.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
            }
            current = current.getCause();
        }
        return false;
    }
}
