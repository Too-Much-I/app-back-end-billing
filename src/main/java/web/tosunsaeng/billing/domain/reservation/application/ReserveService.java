package web.tosunsaeng.billing.domain.reservation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import com.mongodb.MongoException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import web.tosunsaeng.billing.domain.benefit.application.BenefitCatalog;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.reservation.config.ReservationProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementLedgerEntry;
import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.domain.entity.ReservationAllocation;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialCandidateAlias;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptSessionRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementGrantRepository;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementLedgerRepository;
import web.tosunsaeng.billing.domain.reservation.repository.IdempotencyCommandRepository;
import web.tosunsaeng.billing.domain.reservation.repository.ReservationAllocationRepository;
import web.tosunsaeng.billing.domain.reservation.repository.ReservationRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialCandidateAliasRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityCandidate;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityState;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository;

@Service
public class ReserveService {

    private static final int MAX_TRANSACTION_ATTEMPTS = 3;

    private final TrialEligibilityRepository eligibilityRepository;
    private final BenefitCatalog benefitCatalog;
    private final TrialClaimRepository claimRepository;
    private final TrialCandidateAliasRepository aliasRepository;
    private final BillingSubjectLinkRepository subjectLinkRepository;
    private final EntitlementGrantRepository grantRepository;
    private final EntitlementLedgerRepository ledgerRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAllocationRepository allocationRepository;
    private final IdempotencyCommandRepository commandRepository;
    private final AttemptGroupRepository attemptGroupRepository;
    private final AttemptSessionRepository attemptSessionRepository;
    private final MongoTransactionExecutor transactionExecutor;
    private final TrialEligibilityProperties eligibilityProperties;
    private final ReservationProperties reservationProperties;
    private final ReserveMetrics metrics;
    private final Clock clock;

    public ReserveService(
            TrialEligibilityRepository eligibilityRepository,
            BenefitCatalog benefitCatalog,
            TrialClaimRepository claimRepository,
            TrialCandidateAliasRepository aliasRepository,
            BillingSubjectLinkRepository subjectLinkRepository,
            EntitlementGrantRepository grantRepository,
            EntitlementLedgerRepository ledgerRepository,
            ReservationRepository reservationRepository,
            ReservationAllocationRepository allocationRepository,
            IdempotencyCommandRepository commandRepository,
            AttemptGroupRepository attemptGroupRepository,
            AttemptSessionRepository attemptSessionRepository,
            MongoTransactionExecutor transactionExecutor,
            TrialEligibilityProperties eligibilityProperties,
            ReservationProperties reservationProperties,
            ReserveMetrics metrics,
            Clock clock
    ) {
        this.eligibilityRepository = eligibilityRepository;
        this.benefitCatalog = benefitCatalog;
        this.claimRepository = claimRepository;
        this.aliasRepository = aliasRepository;
        this.subjectLinkRepository = subjectLinkRepository;
        this.grantRepository = grantRepository;
        this.ledgerRepository = ledgerRepository;
        this.reservationRepository = reservationRepository;
        this.allocationRepository = allocationRepository;
        this.commandRepository = commandRepository;
        this.attemptGroupRepository = attemptGroupRepository;
        this.attemptSessionRepository = attemptSessionRepository;
        this.transactionExecutor = transactionExecutor;
        this.eligibilityProperties = eligibilityProperties;
        this.reservationProperties = reservationProperties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public ReserveResult reserve(ReserveCommand command) {
        ReserveIds ids = ReserveIds.create();
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                ReserveResult result = transactionExecutor.execute(() -> processOnce(command, ids));
                metrics.record(
                        result.snapshot().reservationKind(),
                        result.replayed() ? "REPLAYED" : "SUCCEEDED"
                );
                return result;
            } catch (InternalApiException exception) {
                recordFailure(exception);
                throw exception;
            } catch (DuplicateKeyException exception) {
                ReserveResult committed = classifyCommitted(command);
                if (committed != null) {
                    metrics.record(committed.snapshot().reservationKind(), "REPLAYED");
                    return committed;
                }
                retryBackoff(attempt);
            } catch (DataAccessException | MongoException exception) {
                ReserveResult committed = classifyCommitted(command);
                if (committed != null) {
                    metrics.record(committed.snapshot().reservationKind(), "REPLAYED");
                    return committed;
                }
                retryBackoff(attempt);
            }
        }
        ReserveResult committed = classifyCommitted(command);
        if (committed != null) {
            metrics.record(committed.snapshot().reservationKind(), "REPLAYED");
            return committed;
        }
        if (commandRepository.findActiveReserve(command.userId()).isPresent()) {
            metrics.record(null, "PROCESSING");
            throw ReservationException.commandProcessing();
        }
        metrics.record(null, "TEMPORARY_FAILURE");
        metrics.recordRetryExhausted();
        throw ReservationException.temporarilyUnavailable();
    }

    private ReserveResult processOnce(ReserveCommand command, ReserveIds ids) {
        Optional<IdempotencyCommand> existing = commandRepository.findReserve(
                command.userId(), command.operationId()
        );
        if (existing.isPresent()) {
            return classifyExisting(existing.get(), command);
        }
        commandRepository.findActiveReserve(command.userId()).ifPresent(active -> {
            throw ReservationException.commandProcessing();
        });

        Instant now = clock.instant();
        IdempotencyCommand commandDocument = IdempotencyCommand.processing(
                ids.commandId(), command.userId(), command.operationId(),
                command.payloadHash(), now
        );
        commandRepository.insert(commandDocument);

        TrialEligibility eligibility = eligibilityRepository.findByScopeAndUser(
                        eligibilityProperties.getExpectedConsumerScopeId(), command.userId()
                )
                .filter(value -> value.getState() == TrialEligibilityState.VERIFIED)
                .filter(value -> !value.getCandidates().isEmpty())
                .orElseThrow(ReservationException::entitlementInsufficient);

        ClaimContext claim = resolveClaim(eligibility, command.userId(), now, ids);
        Optional<AttemptGroup> existingGroup = attemptGroupRepository
                .findNonTerminalBySubject(claim.subjectRefId());
        Reservation.Kind kind = determineKind(existingGroup, claim, command.mockExamId());
        String attemptGroupId = existingGroup
                .map(AttemptGroup::getAttemptGroupId)
                .orElse(ids.attemptGroupId());

        if (attemptSessionRepository.findBySessionId(command.sessionId()).isPresent()) {
            throw ReservationException.stateConflict();
        }

        String allocationId = null;
        if (kind == Reservation.Kind.INITIAL) {
            EntitlementGrant heldGrant = grantRepository.holdOne(claim.grant().getGrantId(), now)
                    .orElseThrow(ReservationException::entitlementInsufficient);
            validateGrantUnits(heldGrant);
            allocationId = ids.allocationId();
            allocationRepository.insert(ReservationAllocation.held(
                    allocationId, ids.reservationId(), heldGrant.getGrantId(), now
            ));
            long sequence = ledgerRepository.nextGrantSequence(heldGrant.getGrantId());
            ledgerRepository.insert(EntitlementLedgerEntry.reserved(
                    ids.reservedLedgerId(), heldGrant.getGrantId(), sequence,
                    claim.subjectRefId(), claim.claim().getTrialClaimId(),
                    ids.reservationId(), allocationId, now
            ));
        } else {
            attemptSessionRepository.abandonActiveBySubject(claim.subjectRefId(), now);
        }

        Instant expiresAt = now.plus(reservationProperties.getHoldDuration());
        Reservation reservation = Reservation.reserved(
                ids.reservationId(), claim.subjectRefId(), command.operationId(),
                command.payloadHash(), kind, attemptGroupId, command.sessionId(),
                command.mockExamId(), now, expiresAt
        );
        reservationRepository.insert(reservation);
        attemptSessionRepository.insert(AttemptSession.proposed(
                command.sessionId(), attemptGroupId, claim.subjectRefId(),
                command.operationId(), now
        ));

        IdempotencyCommand.ResponseSnapshot snapshot = new IdempotencyCommand.ResponseSnapshot(
                command.operationId(), reservation.getReservationId(), kind,
                Reservation.Status.RESERVED, attemptGroupId, command.sessionId(),
                command.mockExamId(), expiresAt
        );
        commandDocument.succeed(reservation.getReservationId(), snapshot);
        commandRepository.save(commandDocument);
        return new ReserveResult(snapshot, false);
    }

    private ClaimContext resolveClaim(
            TrialEligibility eligibility,
            String userId,
            Instant now,
            ReserveIds ids
    ) {
        List<TrialEligibilityCandidate> candidates = eligibility.getCandidates();
        BenefitDefinition definition = benefitCatalog.findActiveFreeExamOnce()
                .orElseThrow(this::catalogUnavailable);
        String benefitCode = definition.getBenefitCode();
        aliasRepository.deactivateExpiredMatches(benefitCode, candidates, now);
        List<TrialCandidateAlias> matches = aliasRepository.findActiveMatches(
                benefitCode, candidates, now
        );
        Set<String> claimIds = new HashSet<>();
        matches.forEach(alias -> claimIds.add(alias.getTrialClaimId()));
        if (claimIds.size() > 1) {
            metrics.recordInvariantViolation();
            throw ReservationException.temporarilyUnavailable();
        }
        if (claimIds.size() == 1) {
            String claimId = claimIds.iterator().next();
            TrialClaim claim = claimRepository.findById(claimId)
                    .filter(value -> value.getState() == TrialClaim.State.ACTIVE)
                    .filter(value -> value.getRetentionExpiresAt().isAfter(now))
                    .orElseThrow(ReservationException::temporarilyUnavailable);
            if (!benefitCode.equals(claim.getBenefitCode())) {
                throw catalogUnavailable();
            }
            BillingSubjectLink link = subjectLinkRepository.findByClaim(claimId)
                    .filter(BillingSubjectLink::isActive)
                    .filter(value -> value.getRetentionExpiresAt().isAfter(now))
                    .orElseThrow(ReservationException::temporarilyUnavailable);
            if (!link.getUserId().equals(userId)) {
                throw ReservationException.entitlementInsufficient();
            }
            addMissingAliases(candidates, claim, benefitCode, now);
            EntitlementGrant grant = grantRepository.findByClaimAndBenefitCode(
                            claimId, benefitCode
                    )
                    .orElseThrow(this::catalogUnavailable);
            validateDefinitionGrant(definition, grant);
            return new ClaimContext(claim, link.getSubjectRefId(), grant);
        }

        Instant retentionExpiresAt = now.atZone(ZoneOffset.UTC).plusYears(3).toInstant();
        TrialClaim claim = TrialClaim.active(
                ids.trialClaimId(), benefitCode, ids.subjectRefId(),
                eligibility.getLastEventId(), now, retentionExpiresAt
        );
        claimRepository.insert(claim);
        subjectLinkRepository.insert(BillingSubjectLink.active(
                ids.subjectRefId(), ids.trialClaimId(), eligibility.getConsumerScopeId(),
                userId, now, retentionExpiresAt
        ));
        for (TrialEligibilityCandidate candidate : candidates) {
            aliasRepository.insert(TrialCandidateAlias.active(
                    uuid(), benefitCode, candidate.keyVersion(), candidate.value(),
                    ids.trialClaimId(), now, retentionExpiresAt
            ));
        }
        EntitlementGrant grant = EntitlementGrant.unitGrant(
                ids.grantId(), benefitCode, "TRIAL_CLAIM", ids.trialClaimId(),
                ids.subjectRefId(), definition.getDefaultGrantUnits(), now
        );
        grantRepository.insert(grant);
        ledgerRepository.insert(EntitlementLedgerEntry.granted(
                ids.grantedLedgerId(), ids.grantId(), ids.subjectRefId(),
                ids.trialClaimId(), now
        ));
        return new ClaimContext(claim, ids.subjectRefId(), grant);
    }

    private void addMissingAliases(
            List<TrialEligibilityCandidate> candidates,
            TrialClaim claim,
            String benefitCode,
            Instant now
    ) {
        Set<CandidateKey> existing = new HashSet<>();
        aliasRepository.findActiveByClaim(claim.getTrialClaimId(), benefitCode, now)
                .forEach(alias -> existing.add(new CandidateKey(
                        alias.getKeyVersion(), alias.getCandidate()
                )));
        for (TrialEligibilityCandidate candidate : candidates) {
            CandidateKey key = new CandidateKey(candidate.keyVersion(), candidate.value());
            if (!existing.contains(key)) {
                aliasRepository.insert(TrialCandidateAlias.active(
                        uuid(), benefitCode, candidate.keyVersion(), candidate.value(),
                        claim.getTrialClaimId(), now, claim.getRetentionExpiresAt()
                ));
            }
        }
    }

    private Reservation.Kind determineKind(
            Optional<AttemptGroup> group,
            ClaimContext claim,
            String mockExamId
    ) {
        if (group.isEmpty()) {
            return Reservation.Kind.INITIAL;
        }
        AttemptGroup current = group.get();
        if (!current.getTrialClaimId().equals(claim.claim().getTrialClaimId())) {
            metrics.recordInvariantViolation();
            throw ReservationException.temporarilyUnavailable();
        }
        if (current.getStatus() == AttemptGroup.Status.GRADING) {
            throw ReservationException.commandProcessing();
        }
        if (current.getStatus() != AttemptGroup.Status.OPEN
                && current.getStatus() != AttemptGroup.Status.RETAKE_AVAILABLE) {
            metrics.recordInvariantViolation();
            throw ReservationException.temporarilyUnavailable();
        }
        if (!current.getMockExamId().equals(mockExamId)) {
            throw ReservationException.stateConflict();
        }
        return Reservation.Kind.REPLACEMENT;
    }

    private ReserveResult classifyCommitted(ReserveCommand command) {
        try {
            return commandRepository.findReserve(command.userId(), command.operationId())
                    .map(existing -> classifyExisting(existing, command))
                    .orElse(null);
        } catch (InternalApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static ReserveResult classifyExisting(
            IdempotencyCommand existing,
            ReserveCommand command
    ) {
        if (!existing.getPayloadHash().equals(command.payloadHash())) {
            throw ReservationException.idempotencyConflict();
        }
        if (existing.getState() == IdempotencyCommand.State.SUCCEEDED
                && existing.getResponseSnapshot() != null) {
            return new ReserveResult(existing.getResponseSnapshot(), true);
        }
        throw ReservationException.commandProcessing();
    }

    private void recordFailure(InternalApiException exception) {
        String outcome = switch (exception.code()) {
            case "ENTITLEMENT_INSUFFICIENT" -> "INSUFFICIENT";
            case "COMMAND_PROCESSING" -> "PROCESSING";
            case "IDEMPOTENCY_KEY_CONFLICT", "RESERVATION_STATE_CONFLICT" -> "CONFLICT";
            default -> "TEMPORARY_FAILURE";
        };
        metrics.record(null, outcome);
    }

    private static void validateGrantUnits(EntitlementGrant grant) {
        if (grant.getAvailableUnits() < 0 || grant.getHeldUnits() < 0
                || grant.getConsumedUnits() < 0
                || grant.getAvailableUnits() + grant.getHeldUnits()
                + grant.getConsumedUnits() != grant.getTotalUnits()) {
            throw ReservationException.temporarilyUnavailable();
        }
    }

    private void validateDefinitionGrant(
            BenefitDefinition definition,
            EntitlementGrant grant
    ) {
        if (!definition.getBenefitCode().equals(grant.getBenefitCode())
                || grant.getTotalUnits() != definition.getDefaultGrantUnits()) {
            throw catalogUnavailable();
        }
        validateGrantUnits(grant);
    }

    private ReservationException catalogUnavailable() {
        metrics.recordInvariantViolation();
        return ReservationException.temporarilyUnavailable();
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static void retryBackoff(int attempt) {
        LockSupport.parkNanos(attempt * 5_000_000L);
    }

    private record ClaimContext(
            TrialClaim claim,
            String subjectRefId,
            EntitlementGrant grant
    ) {
    }

    private record CandidateKey(String keyVersion, String candidate) {
    }

    private record ReserveIds(
            String commandId,
            String reservationId,
            String attemptGroupId,
            String allocationId,
            String trialClaimId,
            String subjectRefId,
            String grantId,
            String grantedLedgerId,
            String reservedLedgerId
    ) {
        static ReserveIds create() {
            return new ReserveIds(
                    uuid(), uuid(), uuid(), uuid(), uuid(), uuid(), uuid(), uuid(), uuid()
            );
        }
    }
}
