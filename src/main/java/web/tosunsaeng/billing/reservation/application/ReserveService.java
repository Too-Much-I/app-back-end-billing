package web.tosunsaeng.billing.reservation.application;

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

import web.tosunsaeng.billing.config.ReservationProperties;
import web.tosunsaeng.billing.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.global.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.reservation.domain.AttemptGroup;
import web.tosunsaeng.billing.reservation.domain.AttemptSession;
import web.tosunsaeng.billing.reservation.domain.BillingSubjectLink;
import web.tosunsaeng.billing.reservation.domain.EntitlementGrant;
import web.tosunsaeng.billing.reservation.domain.EntitlementLedgerEntry;
import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;
import web.tosunsaeng.billing.reservation.domain.ReservationAllocation;
import web.tosunsaeng.billing.reservation.domain.TrialCandidateAlias;
import web.tosunsaeng.billing.reservation.domain.TrialClaim;
import web.tosunsaeng.billing.reservation.infrastructure.AttemptGroupRepository;
import web.tosunsaeng.billing.reservation.infrastructure.AttemptSessionRepository;
import web.tosunsaeng.billing.reservation.infrastructure.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.reservation.infrastructure.EntitlementGrantRepository;
import web.tosunsaeng.billing.reservation.infrastructure.EntitlementLedgerRepository;
import web.tosunsaeng.billing.reservation.infrastructure.IdempotencyCommandRepository;
import web.tosunsaeng.billing.reservation.infrastructure.ReservationAllocationRepository;
import web.tosunsaeng.billing.reservation.infrastructure.ReservationRepository;
import web.tosunsaeng.billing.reservation.infrastructure.TrialCandidateAliasRepository;
import web.tosunsaeng.billing.reservation.infrastructure.TrialClaimRepository;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibility;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityCandidate;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityState;
import web.tosunsaeng.billing.trialeligibility.infrastructure.TrialEligibilityRepository;

@Service
public class ReserveService {

    private static final int MAX_TRANSACTION_ATTEMPTS = 3;

    private final TrialEligibilityRepository eligibilityRepository;
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
            throw InternalApiException.commandProcessing();
        }
        metrics.record(null, "TEMPORARY_FAILURE");
        metrics.recordRetryExhausted();
        throw InternalApiException.temporarilyUnavailable();
    }

    private ReserveResult processOnce(ReserveCommand command, ReserveIds ids) {
        Optional<IdempotencyCommand> existing = commandRepository.findReserve(
                command.userId(), command.operationId()
        );
        if (existing.isPresent()) {
            return classifyExisting(existing.get(), command);
        }
        commandRepository.findActiveReserve(command.userId()).ifPresent(active -> {
            throw InternalApiException.commandProcessing();
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
                .orElseThrow(InternalApiException::entitlementInsufficient);

        ClaimContext claim = resolveClaim(eligibility, command.userId(), now, ids);
        Optional<AttemptGroup> existingGroup = attemptGroupRepository
                .findNonTerminalBySubject(claim.subjectRefId());
        Reservation.Kind kind = determineKind(existingGroup, claim, command.mockExamId());
        String attemptGroupId = existingGroup
                .map(AttemptGroup::getAttemptGroupId)
                .orElse(ids.attemptGroupId());

        if (attemptSessionRepository.findBySessionId(command.sessionId()).isPresent()) {
            throw InternalApiException.reservationStateConflict();
        }

        String allocationId = null;
        if (kind == Reservation.Kind.INITIAL) {
            EntitlementGrant heldGrant = grantRepository.holdOne(claim.grant().getGrantId(), now)
                    .orElseThrow(InternalApiException::entitlementInsufficient);
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
        aliasRepository.deactivateExpiredMatches(candidates, now);
        List<TrialCandidateAlias> matches = aliasRepository.findActiveMatches(candidates, now);
        Set<String> claimIds = new HashSet<>();
        matches.forEach(alias -> claimIds.add(alias.getTrialClaimId()));
        if (claimIds.size() > 1) {
            metrics.recordInvariantViolation();
            throw InternalApiException.temporarilyUnavailable();
        }
        if (claimIds.size() == 1) {
            String claimId = claimIds.iterator().next();
            TrialClaim claim = claimRepository.findById(claimId)
                    .filter(value -> value.getState() == TrialClaim.State.ACTIVE)
                    .filter(value -> value.getRetentionExpiresAt().isAfter(now))
                    .orElseThrow(InternalApiException::temporarilyUnavailable);
            BillingSubjectLink link = subjectLinkRepository.findByClaim(claimId)
                    .filter(BillingSubjectLink::isActive)
                    .filter(value -> value.getRetentionExpiresAt().isAfter(now))
                    .orElseThrow(InternalApiException::temporarilyUnavailable);
            if (!link.getUserId().equals(userId)) {
                throw InternalApiException.entitlementInsufficient();
            }
            addMissingAliases(candidates, claim, now);
            EntitlementGrant grant = grantRepository.findFreeGrantByClaim(claimId)
                    .orElseThrow(InternalApiException::temporarilyUnavailable);
            return new ClaimContext(claim, link.getSubjectRefId(), grant);
        }

        Instant retentionExpiresAt = now.atZone(ZoneOffset.UTC).plusYears(3).toInstant();
        TrialClaim claim = TrialClaim.active(
                ids.trialClaimId(), ids.subjectRefId(), eligibility.getLastEventId(),
                now, retentionExpiresAt
        );
        claimRepository.insert(claim);
        subjectLinkRepository.insert(BillingSubjectLink.active(
                ids.subjectRefId(), ids.trialClaimId(), eligibility.getConsumerScopeId(),
                userId, now, retentionExpiresAt
        ));
        for (TrialEligibilityCandidate candidate : candidates) {
            aliasRepository.insert(TrialCandidateAlias.active(
                    uuid(), candidate.keyVersion(), candidate.value(), ids.trialClaimId(),
                    now, retentionExpiresAt
            ));
        }
        EntitlementGrant grant = EntitlementGrant.freeExamOnce(
                ids.grantId(), ids.trialClaimId(), ids.subjectRefId(), now
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
            Instant now
    ) {
        Set<CandidateKey> existing = new HashSet<>();
        aliasRepository.findActiveByClaim(claim.getTrialClaimId(), now)
                .forEach(alias -> existing.add(new CandidateKey(
                        alias.getKeyVersion(), alias.getCandidate()
                )));
        for (TrialEligibilityCandidate candidate : candidates) {
            CandidateKey key = new CandidateKey(candidate.keyVersion(), candidate.value());
            if (!existing.contains(key)) {
                aliasRepository.insert(TrialCandidateAlias.active(
                        uuid(), candidate.keyVersion(), candidate.value(),
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
            throw InternalApiException.temporarilyUnavailable();
        }
        if (current.getStatus() == AttemptGroup.Status.GRADING) {
            throw InternalApiException.commandProcessing();
        }
        if (current.getStatus() != AttemptGroup.Status.OPEN
                && current.getStatus() != AttemptGroup.Status.RETAKE_AVAILABLE) {
            metrics.recordInvariantViolation();
            throw InternalApiException.temporarilyUnavailable();
        }
        if (!current.getMockExamId().equals(mockExamId)) {
            throw InternalApiException.reservationStateConflict();
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
            throw InternalApiException.idempotencyConflict();
        }
        if (existing.getState() == IdempotencyCommand.State.SUCCEEDED
                && existing.getResponseSnapshot() != null) {
            return new ReserveResult(existing.getResponseSnapshot(), true);
        }
        throw InternalApiException.commandProcessing();
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
            throw InternalApiException.temporarilyUnavailable();
        }
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
