package web.tosunsaeng.billing.reservation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import com.mongodb.MongoException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import web.tosunsaeng.billing.config.ReservationProperties;
import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.global.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.reservation.domain.AttemptGroup;
import web.tosunsaeng.billing.reservation.domain.AttemptSession;
import web.tosunsaeng.billing.reservation.domain.EntitlementGrant;
import web.tosunsaeng.billing.reservation.domain.EntitlementLedgerEntry;
import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;
import web.tosunsaeng.billing.reservation.domain.ReservationAllocation;
import web.tosunsaeng.billing.reservation.infrastructure.AttemptGroupRepository;
import web.tosunsaeng.billing.reservation.infrastructure.AttemptSessionRepository;
import web.tosunsaeng.billing.reservation.infrastructure.EntitlementGrantRepository;
import web.tosunsaeng.billing.reservation.infrastructure.EntitlementLedgerRepository;
import web.tosunsaeng.billing.reservation.infrastructure.IdempotencyCommandRepository;
import web.tosunsaeng.billing.reservation.infrastructure.ReservationAllocationRepository;
import web.tosunsaeng.billing.reservation.infrastructure.ReservationRepository;

@Service
public class ReservationLifecycleService {

    private static final int MAX_TRANSACTION_ATTEMPTS = 3;

    private final ReservationRepository reservationRepository;
    private final ReservationAllocationRepository allocationRepository;
    private final EntitlementGrantRepository grantRepository;
    private final EntitlementLedgerRepository ledgerRepository;
    private final AttemptGroupRepository groupRepository;
    private final AttemptSessionRepository sessionRepository;
    private final IdempotencyCommandRepository commandRepository;
    private final MongoTransactionExecutor transactionExecutor;
    private final ReservationProperties properties;
    private final ReservationLifecycleMetrics metrics;
    private final Clock clock;

    public ReservationLifecycleService(
            ReservationRepository reservationRepository,
            ReservationAllocationRepository allocationRepository,
            EntitlementGrantRepository grantRepository,
            EntitlementLedgerRepository ledgerRepository,
            AttemptGroupRepository groupRepository,
            AttemptSessionRepository sessionRepository,
            IdempotencyCommandRepository commandRepository,
            MongoTransactionExecutor transactionExecutor,
            ReservationProperties properties,
            ReservationLifecycleMetrics metrics,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.allocationRepository = allocationRepository;
        this.grantRepository = grantRepository;
        this.ledgerRepository = ledgerRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.commandRepository = commandRepository;
        this.transactionExecutor = transactionExecutor;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public LifecycleResult confirm(ConfirmCommand command) {
        LifecycleIds ids = LifecycleIds.create();
        Instant now = clock.instant();
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                LifecycleResult result = transactionExecutor.execute(
                        () -> confirmOnce(command, ids, now)
                );
                metrics.record("CONFIRM", null, result.replayed() ? "REPLAYED" : "SUCCEEDED");
                return result;
            } catch (InternalApiException exception) {
                metrics.record("CONFIRM", null, "REJECTED");
                throw exception;
            } catch (DuplicateKeyException | MongoException exception) {
                LifecycleResult committed = classifyCommitted(
                        command.userId(), command.operationId(), "CONFIRM", command.payloadHash()
                );
                if (committed != null) {
                    metrics.record("CONFIRM", null, "REPLAYED");
                    return committed;
                }
                retryBackoff(attempt);
            } catch (DataAccessException exception) {
                LifecycleResult committed = classifyCommitted(
                        command.userId(), command.operationId(), "CONFIRM", command.payloadHash()
                );
                if (committed != null) {
                    metrics.record("CONFIRM", null, "REPLAYED");
                    return committed;
                }
                retryBackoff(attempt);
            }
        }
        metrics.recordRetryExhausted("CONFIRM");
        throw InternalApiException.temporarilyUnavailable();
    }

    public LifecycleResult cancel(CancelCommand command) {
        LifecycleIds ids = LifecycleIds.create();
        Instant now = clock.instant();
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                LifecycleResult result = transactionExecutor.execute(
                        () -> cancelOnce(command, ids, now)
                );
                metrics.record("CANCEL", null, result.replayed() ? "REPLAYED" : "SUCCEEDED");
                return result;
            } catch (InternalApiException exception) {
                metrics.record("CANCEL", null, "REJECTED");
                throw exception;
            } catch (DuplicateKeyException | MongoException exception) {
                LifecycleResult committed = classifyCommitted(
                        command.userId(), command.operationId(), "CANCEL", command.payloadHash()
                );
                if (committed != null) {
                    metrics.record("CANCEL", null, "REPLAYED");
                    return committed;
                }
                retryBackoff(attempt);
            } catch (DataAccessException exception) {
                LifecycleResult committed = classifyCommitted(
                        command.userId(), command.operationId(), "CANCEL", command.payloadHash()
                );
                if (committed != null) {
                    metrics.record("CANCEL", null, "REPLAYED");
                    return committed;
                }
                retryBackoff(attempt);
            }
        }
        metrics.recordRetryExhausted("CANCEL");
        throw InternalApiException.temporarilyUnavailable();
    }

    public ReservationStatusResult status(String userId, String operationId) {
        IdempotencyCommand reserve = commandRepository.findReserve(userId, operationId)
                .filter(value -> value.getState() == IdempotencyCommand.State.SUCCEEDED)
                .orElseThrow(InternalApiException::operationNotFound);
        Reservation reservation = reservationRepository.findById(reserve.getReservationId())
                .orElseThrow(InternalApiException::operationNotFound);
        AttemptGroup.Status groupStatus = groupRepository.findById(
                reservation.getAttemptGroupId()
        ).map(AttemptGroup::getStatus).orElse(null);
        return new ReservationStatusResult(
                operationId, reservation.getReservationId(), reservation.getReservationKind(),
                reservation.getStatus(), reservation.getAttemptGroupId(), groupStatus,
                reservation.getProposedSessionId(), reservation.getMockExamId(),
                reservation.getExpiresAt(), reservation.getTerminalAt()
        );
    }

    public boolean expire(String reservationId, Instant now) {
        String ledgerId = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                Boolean expired = transactionExecutor.execute(
                        () -> expireOnce(reservationId, now, ledgerId)
                );
                if (Boolean.TRUE.equals(expired)) {
                    metrics.record("EXPIRE", null, "SUCCEEDED");
                }
                return Boolean.TRUE.equals(expired);
            } catch (InternalApiException exception) {
                metrics.record("EXPIRE", null, "INVARIANT_FAILURE");
                throw exception;
            } catch (DataAccessException | MongoException exception) {
                Reservation live = reservationRepository.findById(reservationId).orElse(null);
                if (live != null && live.getStatus() == Reservation.Status.EXPIRED) {
                    return true;
                }
                retryBackoff(attempt);
            }
        }
        metrics.recordRetryExhausted("EXPIRE");
        throw InternalApiException.temporarilyUnavailable();
    }

    private LifecycleResult confirmOnce(
            ConfirmCommand command,
            LifecycleIds ids,
            Instant now
    ) {
        IdempotencyCommand reserveCommand = requireReserve(
                command.userId(), command.operationId(), command.reservationId()
        );
        Optional<IdempotencyCommand> existing = commandRepository.find(
                command.userId(), command.operationId(), "CONFIRM"
        );
        if (existing.isPresent()) {
            return classifyExisting(existing.get(), command.payloadHash());
        }

        Reservation reservation = requireReservation(command.reservationId());
        validateReservationLink(reservation, reserveCommand, command.userId(), command.operationId());
        if (!reservation.getProposedSessionId().equals(command.sessionId())) {
            throw InternalApiException.reservationStateConflict();
        }
        AttemptSession session = requireMatchingSession(reservation);

        IdempotencyCommand lifecycleCommand = IdempotencyCommand.processingLifecycle(
                ids.commandId(), command.userId(), command.operationId(), "CONFIRM",
                command.payloadHash(), now
        );
        commandRepository.insert(lifecycleCommand);
        reservationRepository.transitionReserved(
                reservation.getReservationId(), reservation.getVersion(),
                Reservation.Status.CONFIRMED, now
        ).orElseThrow(InternalApiException::reservationStateConflict);

        AttemptGroup group;
        if (reservation.getReservationKind() == Reservation.Kind.INITIAL) {
            ReservationAllocation allocation = requireHeldAllocation(reservation);
            EntitlementGrant grant = requireGrant(allocation.getGrantId());
            allocationRepository.transitionHeld(
                    allocation.getAllocationId(), allocation.getVersion(),
                    ReservationAllocation.Status.CONSUMED, now
            ).orElseThrow(InternalApiException::temporarilyUnavailable);
            EntitlementGrant consumed = grantRepository.consumeHeldOne(
                    grant.getGrantId(), grant.getVersion(), now
            ).orElseThrow(InternalApiException::temporarilyUnavailable);
            validateGrantUnits(consumed);
            long sequence = ledgerRepository.nextGrantSequence(grant.getGrantId());
            EntitlementLedgerEntry ledger = EntitlementLedgerEntry.consumed(
                    ids.ledgerId(), grant.getGrantId(), sequence, reservation.getSubjectRefId(),
                    grant.getSourceId(), reservation.getReservationId(),
                    allocation.getAllocationId(), now
            );
            ledgerRepository.insert(ledger);
            group = groupRepository.insert(AttemptGroup.open(
                    reservation.getAttemptGroupId(), reservation.getSubjectRefId(),
                    grant.getSourceId(), ledger.getLedgerEventId(), reservation.getMockExamId(),
                    reservation.getProposedSessionId(), now
            ));
        } else {
            AttemptGroup current = groupRepository.findById(reservation.getAttemptGroupId())
                    .orElseThrow(InternalApiException::temporarilyUnavailable);
            validateReplacementGroup(current, reservation);
            group = groupRepository.openWithSession(
                    current.getAttemptGroupId(), current.getVersion(),
                    reservation.getProposedSessionId(), now
            ).orElseThrow(InternalApiException::reservationStateConflict);
        }
        sessionRepository.activateProposed(
                session.getSessionId(), session.getAttemptGroupId(), session.getSubjectRefId(),
                session.getOperationId(), session.getVersion(), now
        ).orElseThrow(InternalApiException::temporarilyUnavailable);

        Instant purgeAt = now.plus(properties.getTerminalCommandRetention());
        reserveCommand.finishReserveLifecycle(now, purgeAt);
        commandRepository.save(reserveCommand);
        IdempotencyCommand.LifecycleResponseSnapshot snapshot = lifecycleSnapshot(
                command.operationId(), reservation, Reservation.Status.CONFIRMED,
                group.getStatus(), now
        );
        lifecycleCommand.succeedLifecycle(reservation.getReservationId(), snapshot, now, purgeAt);
        commandRepository.save(lifecycleCommand);
        return new LifecycleResult(snapshot, false);
    }

    private LifecycleResult cancelOnce(
            CancelCommand command,
            LifecycleIds ids,
            Instant now
    ) {
        IdempotencyCommand reserveCommand = requireReserve(
                command.userId(), command.operationId(), command.reservationId()
        );
        Optional<IdempotencyCommand> existing = commandRepository.find(
                command.userId(), command.operationId(), "CANCEL"
        );
        if (existing.isPresent()) {
            return classifyExisting(existing.get(), command.payloadHash());
        }
        Reservation reservation = requireReservation(command.reservationId());
        validateReservationLink(reservation, reserveCommand, command.userId(), command.operationId());
        AttemptSession session = requireMatchingSession(reservation);

        IdempotencyCommand lifecycleCommand = IdempotencyCommand.processingLifecycle(
                ids.commandId(), command.userId(), command.operationId(), "CANCEL",
                command.payloadHash(), now
        );
        commandRepository.insert(lifecycleCommand);
        reservationRepository.transitionReserved(
                reservation.getReservationId(), reservation.getVersion(),
                Reservation.Status.CANCELED, now
        ).orElseThrow(InternalApiException::reservationStateConflict);
        releaseInitial(reservation, ids.ledgerId(), now);
        failSession(session, now);

        Instant purgeAt = now.plus(properties.getTerminalCommandRetention());
        reserveCommand.finishReserveLifecycle(now, purgeAt);
        commandRepository.save(reserveCommand);
        IdempotencyCommand.LifecycleResponseSnapshot snapshot = lifecycleSnapshot(
                command.operationId(), reservation, Reservation.Status.CANCELED, null, now
        );
        lifecycleCommand.succeedLifecycle(reservation.getReservationId(), snapshot, now, purgeAt);
        commandRepository.save(lifecycleCommand);
        return new LifecycleResult(snapshot, false);
    }

    private boolean expireOnce(String reservationId, Instant now, String ledgerId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != Reservation.Status.RESERVED
                || reservation.getExpiresAt().isAfter(now)) {
            return false;
        }
        IdempotencyCommand reserveCommand = commandRepository
                .findReserveByReservationId(reservationId)
                .orElseThrow(InternalApiException::temporarilyUnavailable);
        AttemptSession session = requireMatchingSession(reservation);
        if (reservationRepository.transitionReserved(
                reservationId, reservation.getVersion(), Reservation.Status.EXPIRED, now
        ).isEmpty()) {
            return false;
        }
        releaseInitial(reservation, ledgerId, now);
        failSession(session, now);
        reserveCommand.finishReserveLifecycle(
                now, now.plus(properties.getTerminalCommandRetention())
        );
        commandRepository.save(reserveCommand);
        return true;
    }

    private void releaseInitial(Reservation reservation, String ledgerId, Instant now) {
        if (reservation.getReservationKind() == Reservation.Kind.REPLACEMENT) {
            return;
        }
        ReservationAllocation allocation = requireHeldAllocation(reservation);
        EntitlementGrant grant = requireGrant(allocation.getGrantId());
        allocationRepository.transitionHeld(
                allocation.getAllocationId(), allocation.getVersion(),
                ReservationAllocation.Status.RELEASED, now
        ).orElseThrow(InternalApiException::temporarilyUnavailable);
        EntitlementGrant released = grantRepository.releaseHeldOne(
                grant.getGrantId(), grant.getVersion(), now
        ).orElseThrow(InternalApiException::temporarilyUnavailable);
        validateGrantUnits(released);
        ledgerRepository.insert(EntitlementLedgerEntry.released(
                ledgerId, grant.getGrantId(), ledgerRepository.nextGrantSequence(grant.getGrantId()),
                reservation.getSubjectRefId(), grant.getSourceId(),
                reservation.getReservationId(), allocation.getAllocationId(), now
        ));
    }

    private void failSession(AttemptSession session, Instant now) {
        sessionRepository.failProposed(
                session.getSessionId(), session.getAttemptGroupId(), session.getSubjectRefId(),
                session.getOperationId(), session.getVersion(), now
        ).orElseThrow(InternalApiException::temporarilyUnavailable);
    }

    private IdempotencyCommand requireReserve(
            String userId,
            String operationId,
            String reservationId
    ) {
        Optional<IdempotencyCommand> matchingOperation = commandRepository.findReserve(
                userId, operationId
        );
        if (matchingOperation.isEmpty()) {
            if (commandRepository.findReserveByReservationId(reservationId).isPresent()) {
                throw InternalApiException.reservationStateConflict();
            }
            throw InternalApiException.operationNotFound();
        }
        IdempotencyCommand command = matchingOperation
                .filter(value -> value.getState() == IdempotencyCommand.State.SUCCEEDED)
                .filter(value -> value.getResponseSnapshot() != null)
                .orElseThrow(InternalApiException::operationNotFound);
        if (!reservationId.equals(command.getReservationId())
                || !reservationId.equals(command.getResponseSnapshot().reservationId())) {
            throw InternalApiException.reservationStateConflict();
        }
        return command;
    }

    private Reservation requireReservation(String reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(InternalApiException::reservationStateConflict);
    }

    private static void validateReservationLink(
            Reservation reservation,
            IdempotencyCommand reserveCommand,
            String userId,
            String operationId
    ) {
        if (!reservation.getOperationId().equals(operationId)
                || !reserveCommand.getUserId().equals(userId)
                || !reservation.getCallerService().equals("LEARNING_CORE")) {
            throw InternalApiException.reservationStateConflict();
        }
    }

    private AttemptSession requireMatchingSession(Reservation reservation) {
        AttemptSession session = sessionRepository.findBySessionId(
                reservation.getProposedSessionId()
        ).orElseThrow(InternalApiException::reservationStateConflict);
        if (!session.getAttemptGroupId().equals(reservation.getAttemptGroupId())
                || !session.getSubjectRefId().equals(reservation.getSubjectRefId())
                || !session.getOperationId().equals(reservation.getOperationId())) {
            throw InternalApiException.reservationStateConflict();
        }
        return session;
    }

    private ReservationAllocation requireHeldAllocation(Reservation reservation) {
        return allocationRepository.findByReservationId(reservation.getReservationId())
                .filter(value -> value.getStatus() == ReservationAllocation.Status.HELD)
                .orElseThrow(InternalApiException::temporarilyUnavailable);
    }

    private EntitlementGrant requireGrant(String grantId) {
        return grantRepository.findById(grantId)
                .orElseThrow(InternalApiException::temporarilyUnavailable);
    }

    private static void validateReplacementGroup(
            AttemptGroup group,
            Reservation reservation
    ) {
        if (!group.getSubjectRefId().equals(reservation.getSubjectRefId())
                || !group.getMockExamId().equals(reservation.getMockExamId())
                || group.getConsumptionLedgerEventId() == null
                || (group.getStatus() != AttemptGroup.Status.OPEN
                && group.getStatus() != AttemptGroup.Status.RETAKE_AVAILABLE)) {
            throw InternalApiException.reservationStateConflict();
        }
    }

    private static void validateGrantUnits(EntitlementGrant grant) {
        if (grant.getAvailableUnits() < 0 || grant.getHeldUnits() < 0
                || grant.getConsumedUnits() < 0
                || grant.getAvailableUnits() + grant.getHeldUnits()
                + grant.getConsumedUnits() != grant.getTotalUnits()) {
            throw InternalApiException.temporarilyUnavailable();
        }
    }

    private static IdempotencyCommand.LifecycleResponseSnapshot lifecycleSnapshot(
            String operationId,
            Reservation reservation,
            Reservation.Status status,
            AttemptGroup.Status groupStatus,
            Instant terminalAt
    ) {
        return new IdempotencyCommand.LifecycleResponseSnapshot(
                operationId, reservation.getReservationId(), status,
                reservation.getAttemptGroupId(), groupStatus,
                reservation.getProposedSessionId(), terminalAt
        );
    }

    private LifecycleResult classifyCommitted(
            String userId,
            String operationId,
            String commandType,
            String payloadHash
    ) {
        try {
            return commandRepository.find(userId, operationId, commandType)
                    .map(existing -> classifyExisting(existing, payloadHash))
                    .orElse(null);
        } catch (InternalApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static LifecycleResult classifyExisting(
            IdempotencyCommand existing,
            String payloadHash
    ) {
        if (!existing.getPayloadHash().equals(payloadHash)) {
            throw InternalApiException.idempotencyConflict();
        }
        if (existing.getState() == IdempotencyCommand.State.SUCCEEDED
                && existing.getLifecycleResponseSnapshot() != null) {
            return new LifecycleResult(existing.getLifecycleResponseSnapshot(), true);
        }
        throw InternalApiException.commandProcessing();
    }

    private record LifecycleIds(String commandId, String ledgerId) {
        static LifecycleIds create() {
            return new LifecycleIds(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        }
    }

    private static void retryBackoff(int attempt) {
        LockSupport.parkNanos(attempt * 5_000_000L);
    }
}
