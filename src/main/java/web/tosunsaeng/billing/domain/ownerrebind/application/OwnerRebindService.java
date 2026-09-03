package web.tosunsaeng.billing.domain.ownerrebind.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

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
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.ownerrebind.exception.OwnerRebindException;
import web.tosunsaeng.billing.domain.ownerrebind.repository.OwnerRebindInboxRepository;
import web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.repository.IdempotencyCommandRepository;
import web.tosunsaeng.billing.domain.reservation.repository.ReservationRepository;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@Service
public class OwnerRebindService {

    private static final Logger log = LoggerFactory.getLogger(OwnerRebindService.class);
    private static final int MAX_TRANSACTION_ATTEMPTS = 3;

    private final OwnerRebindInboxRepository inboxRepository;
    private final SubjectOwnerRebindRepository rebindRepository;
    private final BillingSubjectLinkRepository subjectLinkRepository;
    private final TrialEligibilityRepository eligibilityRepository;
    private final TrialCandidateAliasRepository aliasRepository;
    private final TrialClaimRepository claimRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyCommandRepository commandRepository;
    private final AttemptGroupRepository groupRepository;
    private final AttemptSessionRepository sessionRepository;
    private final MongoTransactionExecutor transactionExecutor;
    private final OwnerRebindProperties properties;
    private final OwnerRebindMetrics metrics;
    private final TraceCorrelation traceCorrelation;
    private final Clock clock;

    public OwnerRebindService(
            OwnerRebindInboxRepository inboxRepository,
            SubjectOwnerRebindRepository rebindRepository,
            BillingSubjectLinkRepository subjectLinkRepository,
            TrialEligibilityRepository eligibilityRepository,
            TrialCandidateAliasRepository aliasRepository,
            TrialClaimRepository claimRepository,
            ReservationRepository reservationRepository,
            IdempotencyCommandRepository commandRepository,
            AttemptGroupRepository groupRepository,
            AttemptSessionRepository sessionRepository,
            MongoTransactionExecutor transactionExecutor,
            OwnerRebindProperties properties,
            OwnerRebindMetrics metrics,
            TraceCorrelation traceCorrelation,
            Clock clock
    ) {
        this.inboxRepository = inboxRepository;
        this.rebindRepository = rebindRepository;
        this.subjectLinkRepository = subjectLinkRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.aliasRepository = aliasRepository;
        this.claimRepository = claimRepository;
        this.reservationRepository = reservationRepository;
        this.commandRepository = commandRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.transactionExecutor = transactionExecutor;
        this.properties = properties;
        this.metrics = metrics;
        this.traceCorrelation = traceCorrelation;
        this.clock = clock;
    }

    public OwnerRebindOutcome process(OwnerRebindCommand command) {
        long startedAt = System.nanoTime();
        Instant now = clock.instant();
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                ProcessingResult result = transactionExecutor.execute(
                        () -> processOnce(command, now)
                );
                if (result.conflictCode() != null) {
                    throw conflict(result.conflictCode());
                }
                record(command, result.outcome().name(), startedAt);
                return result.outcome();
            } catch (InternalApiException exception) {
                if ("OWNER_REBIND_PENDING".equals(exception.code())) {
                    metrics.recordPending(pendingReason(exception));
                }
                record(command, outcomeFor(exception), startedAt);
                throw exception;
            } catch (DataAccessException | MongoException exception) {
                ProcessingResult convergence = classifyCommitted(command);
                if (convergence != null) {
                    if (convergence.conflictCode() != null) {
                        record(command, "CONFLICT", startedAt);
                        throw conflict(convergence.conflictCode());
                    }
                    record(command, convergence.outcome().name(), startedAt);
                    return convergence.outcome();
                }
                if (!isRetryable(exception) && attempt == MAX_TRANSACTION_ATTEMPTS) {
                    break;
                }
            }
        }
        metrics.recordRetryExhausted();
        record(command, "TEMPORARY_FAILURE", startedAt);
        throw OwnerRebindException.temporarilyUnavailable();
    }

    private ProcessingResult processOnce(OwnerRebindCommand command, Instant now) {
        Optional<OwnerRebindInbox> existing = inboxRepository.findByEventId(command.eventId());
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), command);
        }

        List<BillingSubjectLink> links = command.isPhoneRejoin()
                ? subjectLinkRepository.findActiveByOwnerAndScope(
                        command.sourceUserId(), command.consumerScopeId(), now,
                        properties.getMaxSubjectsPerEvent() + 1
                )
                : subjectLinkRepository.findActiveByOwner(
                        command.sourceUserId(), now,
                        properties.getMaxSubjectsPerEvent() + 1
                );

        if (links.size() > properties.getMaxSubjectsPerEvent()) {
            metrics.recordInvariantViolation("subject_limit");
            return saveConflict(command, now);
        }
        if (links.isEmpty()) {
            save(command, OwnerRebindDisposition.NOOP, 0, now);
            return ProcessingResult.success(OwnerRebindOutcome.NOOP);
        }

        if (!links.stream().allMatch(link -> activeClaimMatches(link, now))) {
            return saveConflict(command, now);
        }

        if (command.isPhoneRejoin()) {
            PhonePrerequisite prerequisite = validatePhonePrerequisite(command, links, now);
            if (prerequisite == PhonePrerequisite.STALE) {
                save(command, OwnerRebindDisposition.STALE, 0, now);
                return ProcessingResult.success(OwnerRebindOutcome.STALE);
            }
            if (prerequisite == PhonePrerequisite.CONFLICT) {
                return saveConflict(command, now);
            }
        }

        List<String> subjects = links.stream().map(BillingSubjectLink::getSubjectRefId).toList();
        List<Reservation> activeReservations = reservationRepository.findActiveBySubjects(subjects);
        if (!activeReservations.isEmpty()) {
            int retryAfter = activeReservations.stream()
                    .map(Reservation::getExpiresAt)
                    .mapToInt(expiresAt -> retryAfter(now, expiresAt))
                    .max()
                    .orElse(5);
            throw OwnerRebindException.pending(retryAfter);
        }
        if (commandRepository.existsProcessingReserve(command.sourceUserId())) {
            throw OwnerRebindException.pending(5);
        }

        if (command.isPhoneRejoin()) {
            ProcessingResult historyResult = classifyPhoneAttemptHistory(command, links, now);
            if (historyResult != null) {
                return historyResult;
            }
        }

        for (BillingSubjectLink link : links) {
            createFenceIfRequired(command, link, now);
            subjectLinkRepository.rebindOwner(
                            link, command.targetUserId(), now,
                            command.eventKind().name(), command.eventId()
                    )
                    .orElseThrow(OwnerRebindService::concurrentUpdate);
        }
        save(command, OwnerRebindDisposition.APPLIED, links.size(), now);
        return ProcessingResult.success(OwnerRebindOutcome.APPLIED);
    }

    private ProcessingResult classifyPhoneAttemptHistory(
            OwnerRebindCommand command,
            List<BillingSubjectLink> links,
            Instant now
    ) {
        Map<String, BillingSubjectLink> linksByClaim = new HashMap<>();
        for (BillingSubjectLink link : links) {
            if (linksByClaim.put(link.getTrialClaimId(), link) != null) {
                metrics.recordInvariantViolation("phone_attempt_history");
                return saveConflict(command, now);
            }
        }

        List<AttemptGroup> groups = groupRepository.findByClaimIds(
                links.stream().map(BillingSubjectLink::getTrialClaimId).toList()
        );
        Set<String> seenClaims = new HashSet<>();
        for (AttemptGroup group : groups) {
            BillingSubjectLink link = linksByClaim.get(group.getTrialClaimId());
            if (link == null
                    || !link.getSubjectRefId().equals(group.getSubjectRefId())
                    || !seenClaims.add(group.getTrialClaimId())
                    || group.getStatus() == null) {
                metrics.recordInvariantViolation("phone_attempt_history");
                return saveConflict(command, now);
            }
        }

        if (groups.stream().anyMatch(group -> group.getStatus() == AttemptGroup.Status.GRADING)) {
            throw OwnerRebindException.pending(5);
        }
        if (groups.stream().anyMatch(group -> group.getStatus() == AttemptGroup.Status.COMPLETED)) {
            save(command, OwnerRebindDisposition.NOOP, 0, now);
            return ProcessingResult.success(OwnerRebindOutcome.NOOP);
        }
        return null;
    }

    private PhonePrerequisite validatePhonePrerequisite(
            OwnerRebindCommand command,
            List<BillingSubjectLink> links,
            Instant now
    ) {
        TrialEligibility source = eligibilityRepository.findByScopeAndUser(
                command.consumerScopeId(), command.sourceUserId()
        ).orElse(null);
        TrialEligibility target = eligibilityRepository.findByScopeAndUser(
                command.consumerScopeId(), command.targetUserId()
        ).orElse(null);
        if (source == null || target == null
                || source.getBindingRevision() < command.sourceBindingRevision()
                || target.getBindingRevision() < command.targetBindingRevision()) {
            throw OwnerRebindException.pending(5);
        }
        if (source.getBindingRevision() == command.sourceBindingRevision()
                && source.getState() != TrialEligibilityState.REVOKED) {
            return PhonePrerequisite.CONFLICT;
        }
        if (target.getBindingRevision() == command.targetBindingRevision()
                && target.getState() != TrialEligibilityState.VERIFIED) {
            return PhonePrerequisite.CONFLICT;
        }
        if ((source.getBindingRevision() > command.sourceBindingRevision()
                && source.getState() != TrialEligibilityState.REVOKED)
                || (target.getBindingRevision() > command.targetBindingRevision()
                && target.getState() != TrialEligibilityState.VERIFIED)) {
            return PhonePrerequisite.STALE;
        }
        if (source.getState() != TrialEligibilityState.REVOKED
                || target.getState() != TrialEligibilityState.VERIFIED) {
            return PhonePrerequisite.CONFLICT;
        }
        for (BillingSubjectLink link : links) {
            List<TrialCandidateAlias> aliases = aliasRepository.findActiveByClaim(
                    link.getTrialClaimId(), BenefitDefinition.FREE_EXAM_ONCE, now
            );
            if (!matchesAny(target.getCandidates(), aliases)) {
                return PhonePrerequisite.CONFLICT;
            }
        }
        return PhonePrerequisite.READY;
    }

    private boolean activeClaimMatches(BillingSubjectLink link, Instant now) {
        TrialClaim claim = claimRepository.findById(link.getTrialClaimId()).orElse(null);
        return claim != null
                && claim.getState() == TrialClaim.State.ACTIVE
                && claim.getRetentionExpiresAt().isAfter(now)
                && link.getSubjectRefId().equals(claim.getSubjectRefId());
    }

    private void createFenceIfRequired(
            OwnerRebindCommand command,
            BillingSubjectLink link,
            Instant now
    ) {
        AttemptGroup group = groupRepository.findNonTerminalBySubject(link.getSubjectRefId())
                .orElse(null);
        if (group == null || group.getActiveSessionId() == null) {
            return;
        }
        AttemptSession session = sessionRepository.findBySessionId(group.getActiveSessionId())
                .orElse(null);
        if (session == null || session.getState() != AttemptSession.State.ACTIVE
                || session.getProposedAt() == null || !session.getProposedAt().isBefore(now)) {
            return;
        }
        Instant hardCap = min(
                now.plus(properties.getLegacyFenceRetention()),
                link.getRetentionExpiresAt()
        );
        rebindRepository.insert(SubjectOwnerRebind.waitingTerminal(
                command.eventId(), command.eventKind(), link.getSubjectRefId(),
                link.getTrialClaimId(), command.sourceUserId(), group.getAttemptGroupId(),
                session.getSessionId(), link.getOwnerVersion(), now, hardCap
        ));
    }

    private ProcessingResult replay(OwnerRebindInbox inbox, OwnerRebindCommand command) {
        if (!"identity".equals(inbox.getProducer())
                || inbox.getEventKind() != command.eventKind()
                || !inbox.getPayloadDigest().equals(command.payloadDigest())) {
            return ProcessingResult.conflict("EVENT_ID_CONFLICT");
        }
        if (inbox.getDisposition() == OwnerRebindDisposition.CONFLICT) {
            return ProcessingResult.conflict(
                    inbox.getConflictCode() == null
                            ? "OWNER_REBIND_CONFLICT" : inbox.getConflictCode()
            );
        }
        return ProcessingResult.success(OwnerRebindOutcome.DUPLICATE);
    }

    private ProcessingResult saveConflict(OwnerRebindCommand command, Instant now) {
        inboxRepository.insert(OwnerRebindInbox.conflict(
                command,
                "OWNER_REBIND_CONFLICT",
                now,
                now.plus(properties.getInboxRetention())
        ));
        return ProcessingResult.conflict("OWNER_REBIND_CONFLICT");
    }

    private void save(
            OwnerRebindCommand command,
            OwnerRebindDisposition disposition,
            int affectedSubjects,
            Instant now
    ) {
        inboxRepository.insert(OwnerRebindInbox.processed(
                command,
                disposition,
                affectedSubjects,
                now,
                now.plus(properties.getInboxRetention())
        ));
    }

    private ProcessingResult classifyCommitted(OwnerRebindCommand command) {
        try {
            return transactionExecutor.execute(() -> inboxRepository
                    .findByEventId(command.eventId())
                    .map(inbox -> replay(inbox, command))
                    .orElse(null));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void record(OwnerRebindCommand command, String outcome, long startedAt) {
        long durationNanos = Math.max(0, System.nanoTime() - startedAt);
        metrics.record(command.eventKind(), outcome, durationNanos);
        log.info(
                "service=billing operation=owner_rebind_consume eventKind={} outcome={} "
                        + "traceId={} eventId={} durationMs={}",
                command.eventKind().name(),
                outcome.toLowerCase(java.util.Locale.ROOT),
                traceCorrelation.currentTraceId(),
                command.eventId(),
                TimeUnit.NANOSECONDS.toMillis(durationNanos)
        );
    }

    private static boolean matchesAny(
            List<TrialEligibilityCandidate> candidates,
            List<TrialCandidateAlias> aliases
    ) {
        Set<String> candidateKeys = new HashSet<>();
        for (TrialEligibilityCandidate candidate : candidates) {
            candidateKeys.add(candidate.keyVersion() + ":" + candidate.value());
        }
        return aliases.stream().anyMatch(alias -> candidateKeys.contains(
                alias.getKeyVersion() + ":" + alias.getCandidate()
        ));
    }

    private static int retryAfter(Instant now, Instant expiresAt) {
        long millis = Math.max(1, Duration.between(now, expiresAt).toMillis());
        long seconds = Math.max(1, (millis + 999) / 1000);
        return (int) Math.min(300, seconds);
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static OptimisticLockingFailureException concurrentUpdate() {
        return new OptimisticLockingFailureException("Owner link changed concurrently.");
    }

    private static OwnerRebindException conflict(String code) {
        return "EVENT_ID_CONFLICT".equals(code)
                ? OwnerRebindException.eventIdConflict()
                : OwnerRebindException.ownerConflict();
    }

    private static String outcomeFor(InternalApiException exception) {
        return switch (exception.code()) {
            case "EVENT_ID_CONFLICT", "OWNER_REBIND_CONFLICT" -> "CONFLICT";
            case "OWNER_REBIND_PENDING" -> "PENDING";
            default -> "TEMPORARY_FAILURE";
        };
    }

    private static String pendingReason(InternalApiException exception) {
        return exception.retryAfterSeconds() != null && exception.retryAfterSeconds() > 5
                ? "reservation" : "prerequisite";
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

    private enum PhonePrerequisite {
        READY,
        STALE,
        CONFLICT
    }

    private record ProcessingResult(OwnerRebindOutcome outcome, String conflictCode) {
        private static ProcessingResult success(OwnerRebindOutcome outcome) {
            return new ProcessingResult(outcome, null);
        }

        private static ProcessingResult conflict(String code) {
            return new ProcessingResult(null, code);
        }
    }
}
