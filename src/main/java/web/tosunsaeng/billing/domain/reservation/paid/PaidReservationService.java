package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.*;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.*;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.global.exception.InternalApiException;

/** Paid/member-grant admission. Every participant uses the caller's single ClientSession. */
public final class PaidReservationService {
    private final PaidExamTransactions tx;
    private final MemberOperationRecovery recovery;
    private final PaymentRecoveryFence fence;
    private final PaymentProperties payment;
    private final String environment, trialScope;
    private final Clock clock;
    public PaidReservationService(PaidExamTransactions tx, MemberOperationRecovery recovery, PaymentStore store,
            PaymentProperties payment, String environment, String trialScope, Clock clock) {
        this.tx = tx; this.recovery = recovery; this.fence = new PaymentRecoveryFence(store, payment);
        this.payment = payment; this.environment = environment; this.trialScope = trialScope; this.clock = clock;
    }
    public MemberOperationRecovery.Result reserve(ReserveCommand command) {
        // Successful replay happens inside recovery before mutable context and source selection.
        Ids ids = new Ids();
        return recovery.reserve(command, (c, cmd) -> admit(c, cmd, ids), (c, account) -> activePaid(c, account) != null
                || !memberGrants(c, command.userId()).isEmpty() || memberReplacement(c, command));
    }
    private boolean memberReplacement(PaidExamTransactions.Context c, ReserveCommand command) {
        if (command.restartContext() == null) return false;
        return c.groups().findById(command.restartContext().previousAttemptGroupId()).map(g -> g.getSourceSnapshot() != null
                && g.getSourceSnapshot().trialClaimId() == null).orElse(false);
    }
    private Optional<String> admit(PaidExamTransactions.Context c, ReserveCommand command, Ids ids) {
        Instant now = clock.instant();
        Document account = account(c, command.userId());
        Document guard = guard(c, command.userId(), account);
        if (guard.getString("pendingOperationId") != null) throw ReservationException.commandProcessing();
        AttemptGroup previous;
        try { previous = previous(c, command, account, guard); }
        catch (ContextConflict rejected) { return Optional.empty(); }
        Document paid = activePaid(c, account);
        EntitlementSourceSnapshot source;
        String subject = account.getString("paidSubjectRefId"); Long epoch = null;
        Reservation.Kind kind = Reservation.Kind.INITIAL;
        boolean restart = command.continuationReason() != null || command.restartContext() != null
                && command.restartContext().startReason() == RestartContext.StartReason.RESTART_INCOMPLETE;
        if (paid != null) {
            source = paidSource(c, account, paid, now);
            if (restart && previous != null && !previous.isReplacementBlocked() && samePaid(previous.getSourceSnapshot(), source))
                kind = Reservation.Kind.REPLACEMENT;
        } else if (restart && previous != null && !previous.isReplacementBlocked()
                && previous.getSourceSnapshot() != null && previous.getSourceSnapshot().type() == EntitlementSourceSnapshot.Type.EXAM_PASS) {
            source = previous.getSourceSnapshot();
            EntitlementGrant grant = requireGrant(c, source.grantId());
            require("ACTIVE".equals(grant.getState()) && source.consumptionId() != null);
            subject = previous.getSubjectRefId(); epoch = trialEpoch(c, command.userId(), subject, now, true);
            if (source.trialClaimId() != null) verifyTrial(c, command.userId(), source.trialClaimId(), now);
            if (source.trialClaimId() == null) require(command.userId().equals(grant.getOwnerUserId()) && environment.equals(grant.getEnvironment()));
            kind = Reservation.Kind.REPLACEMENT;
        } else {
            List<EntitlementGrant> available = availableGrants(c, command.userId(), now);
            EntitlementGrant grant;
            if (!available.isEmpty()) {
                grant = available.getFirst();
                if ("TRIAL_CLAIM".equals(grant.getSourceType())) {
                    verifyTrial(c, command.userId(), grant.getSourceId(), now);
                    subject = grant.getSubjectRefId(); epoch = trialEpoch(c, command.userId(), subject, now, true);
                }
            }
            else {
                var trial = new PaidTrialFunding(c.operations()).resolve(trialScope, command.userId(), now, ids.trial);
                grant = trial.grant(); subject = trial.subjectRefId();
                epoch = trialEpoch(c, command.userId(), subject, now, true);
            }
            if (!"ACTIVE".equals(grant.getState()) || grant.getAvailableUnits() < 1) throw ReservationException.entitlementInsufficient();
            source = new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.EXAM_PASS, grant.getGrantId(), null,
                    "TRIAL_CLAIM".equals(grant.getSourceType()) ? grant.getSourceId() : null, null, null, null, now, null, null);
        }
        if (kind == Reservation.Kind.REPLACEMENT) {
            if (!Set.of(AttemptGroup.Status.OPEN, AttemptGroup.Status.RETAKE_AVAILABLE).contains(previous.getStatus())
                    || !previous.getMockExamId().equals(command.mockExamId())) return Optional.empty();
            // A replacement retains the exact immutable source, including selection time.
            source = previous.getSourceSnapshot();
        }
        if (c.sessions().findBySessionId(command.sessionId()).isPresent()) throw ReservationException.stateConflict();
        String reservationId = ids.reservation, groupId = kind == Reservation.Kind.REPLACEMENT ? previous.getAttemptGroupId() : ids.group;
        if (!c.paid().acquire(environment, command.userId(), guard.getLong("version"), command.operationId(), reservationId, groupId))
            throw ReservationException.commandProcessing();
        if (source.type() == EntitlementSourceSnapshot.Type.EXAM_PASS && kind == Reservation.Kind.INITIAL) {
            EntitlementGrant held = c.grants().holdOne(source.grantId(), now).orElseThrow(ReservationException::entitlementInsufficient);
            units(held);
            var allocation = ReservationAllocation.held(ids.allocation, reservationId, held.getGrantId(), now);
            c.allocations().insert(allocation);
            c.ledger().insert(EntitlementLedgerEntry.reserved(ids.ledger, held.getGrantId(), c.ledger().nextGrantSequence(held.getGrantId()),
                    subject, source.trialClaimId(), reservationId, allocation.getAllocationId(), now));
        }
        Long previousEpoch = previous == null ? null : trialEpoch(c, command.userId(), previous.getSubjectRefId(), now, true);
        Reservation reservation = Reservation.reserved(reservationId, subject, command.operationId(), command.payloadHash(), kind,
                groupId, command.sessionId(), command.mockExamId(), command.continuationReason(), command.continuationId(), now, now.plusSeconds(300))
                .withSourceSnapshot(source).withTransitionContext(command.restartContext(), previous == null ? null : previous.getAttemptGroupId(), previousEpoch);
        if (epoch != null) reservation.withSessionOwnerEpoch(epoch);
        c.reservations().insert(reservation);
        AttemptSession session = AttemptSession.proposed(command.sessionId(), groupId, subject, command.operationId(), now)
                .withSourceSnapshot(source).withPaidRuntime();
        if (epoch != null) session.withSessionOwnerEpoch(epoch);
        c.sessions().insert(session);
        usage(c, reservation, "EXAM_RESERVED", now, ids.usage);
        return Optional.of(reservationId);
    }
    private AttemptGroup previous(PaidExamTransactions.Context c, ReserveCommand command, Document account, Document guard) {
        String id = command.restartContext() != null ? command.restartContext().previousAttemptGroupId() : command.expectedAttemptGroupId();
        if (id == null) {
            if (guard.getString("currentGroupId") != null || c.groups().findNonTerminalBySubject(account.getString("paidSubjectRefId")).isPresent()) throw new ContextConflict();
            // Existing linked trials cannot silently become an unrelated INITIAL without context.
            for (BillingSubjectLink link : new BillingSubjectLinkRepository(c.operations()).findActiveByOwnerAndScope(command.userId(), trialScope, clock.instant(), 101))
                if (c.groups().findNonTerminalBySubject(link.getSubjectRefId()).isPresent()) throw new ContextConflict();
            return null;
        }
        AttemptGroup group = c.groups().findById(id).orElseThrow(ContextConflict::new);
        if (group.getSourceSnapshot() == null) throw ReservationException.temporarilyUnavailable();
        Document row = doc(c, "attempt_groups", id);
        if (row.getBoolean("deleted", false) || row.getBoolean("lifecycleDenied", false) || group.isReplacementBlocked()) throw new ContextConflict();
        Long epoch = trialEpoch(c, command.userId(), group.getSubjectRefId(), clock.instant(), true);
        if (epoch == null && !account.getString("paidSubjectRefId").equals(group.getSubjectRefId())) throw ReservationException.operationOwnerMismatch();
        if (command.continuationReason() != null) {
            BillingSubjectLink link = new BillingSubjectLinkRepository(c.operations()).findBySubjectRefId(group.getSubjectRefId()).orElseThrow(ContextConflict::new);
            if (epoch == null || !"PHONE_REJOIN".equals(link.getOwnerTransitionReason()) || !command.continuationId().equals(link.getOwnerTransitionId())
                    || !command.mockExamId().equals(group.getMockExamId())
                    || !Set.of(AttemptGroup.Status.OPEN, AttemptGroup.Status.RETAKE_AVAILABLE).contains(group.getStatus())) throw new ContextConflict();
        } else {
            var restart = command.restartContext();
            AttemptSession original = c.sessions().findBySessionId(restart.previousSessionId()).orElseThrow(ContextConflict::new);
            if (group.getActiveSessionId() != null && !group.getActiveSessionId().equals(original.getSessionId())) throw new ContextConflict();
            if (!original.getAttemptGroupId().equals(id) || command.sessionId().equals(original.getSessionId())
                    || !original.getSubjectRefId().equals(group.getSubjectRefId())
                    || epoch != null && !epoch.equals(original.getSessionOwnerEpoch())) throw new ContextConflict();
            if (guard.getString("currentGroupId") != null && (!id.equals(guard.getString("currentGroupId"))
                    || !original.getSessionId().equals(guard.getString("currentSessionId")))) throw new ContextConflict();
            if (restart.startReason() == RestartContext.StartReason.RESTART_INCOMPLETE && (!command.mockExamId().equals(group.getMockExamId())
                    || !Set.of(AttemptGroup.Status.OPEN, AttemptGroup.Status.RETAKE_AVAILABLE).contains(group.getStatus()))) throw new ContextConflict();
            if (restart.startReason() == RestartContext.StartReason.NEW_AFTER_COMPLETION && group.getStatus() != AttemptGroup.Status.COMPLETED)
                throw new ContextConflict();
        }
        return group;
    }
    public LifecycleResult confirm(ConfirmCommand command) {
        return lifecycle(command.userId(), command.operationId(), command.reservationId(), command.payloadHash(), "CONFIRM", command.sessionId(), false);
    }
    public LifecycleResult cancel(CancelCommand command) {
        return lifecycle(command.userId(), command.operationId(), command.reservationId(), command.payloadHash(), "CANCEL", null, false);
    }
    public boolean expire(String userId, String operationId, String reservationId) {
        return lifecycle(userId, operationId, reservationId, null, "EXPIRE", null, true).snapshot().reservationStatus() == Reservation.Status.EXPIRED;
    }
    private LifecycleResult lifecycle(String userId, String operationId, String reservationId, String hash, String action, String sessionId, boolean expiry) {
        Ids ids = new Ids();
        Document result = translated(() -> {
            var ticket = fence.capture();
            return tx.execute(c -> {
                fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                var reserveKey = new PaidExamStore.Key(environment, userId, operationId, "RESERVE");
                Document receipt = c.paid().receipt(reserveKey).orElseThrow(ReservationException::operationNotFound);
                Document account = expiry ? account(c, userId) : recovery.currentAccount(c, reserveKey, true);
                MemberOperationRecovery.owner(receipt, account);
                if (!reservationId.equals(receipt.getString("reservationId"))) throw ReservationException.stateConflict();
                Reservation r = c.reservations().findById(reservationId).orElseThrow(ReservationException::temporarilyUnavailable);
                if (!MemberOperationRecovery.owns(c, account, r)) throw ReservationException.operationOwnerMismatch();
                if (sessionId != null && !sessionId.equals(r.getProposedSessionId())) throw ReservationException.stateConflict();
                if (!expiry) {
                    Document replay = c.paid().receipt(new PaidExamStore.Key(environment, userId, operationId, action)).orElse(null);
                    if (replay != null) {
                        if (!Objects.equals(hash, replay.getString("payloadHash"))) throw ReservationException.idempotencyConflict();
                        return replay;
                    }
                }
                Instant now = clock.instant();
                if (r.getStatus() != Reservation.Status.RESERVED) {
                    if (expiry) return lifecycleRow(r, null);
                    if ("CANCEL".equals(action) && r.getStatus() == Reservation.Status.CANCELED) {
                        Document row = lifecycleRow(r, null);
                        c.paid().insertLifecycle(receipt, action, hash, row, r.getStatus(), null, r.getTerminalAt());
                        return row;
                    }
                    throw ReservationException.stateConflict();
                }
                if (expiry && now.isBefore(r.getExpiresAt())) return lifecycleRow(r, null);
                Document guard = existingGuard(c, userId);
                require(operationId.equals(guard.getString("pendingOperationId")) && reservationId.equals(guard.getString("pendingReservationId")));
                boolean expired = !now.isBefore(r.getExpiresAt());
                boolean revoked = !sourceAllowed(c, account, r, now);
                String rejection = "CONFIRM".equals(action) && revoked ? "ENTITLEMENT_REVOKED" : null;
                Reservation.Status target = rejection != null || "CANCEL".equals(action) ? Reservation.Status.CANCELED
                        : expired || expiry ? Reservation.Status.EXPIRED : Reservation.Status.CONFIRMED;
                if (target == Reservation.Status.CONFIRMED) finishConfirm(c, account, guard, r, now, ids);
                else finishRelease(c, guard, r, userId, now, ids);
                c.reservations().transitionReserved(reservationId, r.getVersion(), target, now).orElseThrow(ReservationException::temporarilyUnavailable);
                if (rejection != null) c.operations().updateFirst(new BasicQuery(new Document("_id", reservationId)), new Update().set("confirmRejection", rejection), Reservation.class);
                Reservation finalReservation = c.reservations().findById(reservationId).orElseThrow();
                usage(c, finalReservation, "EXAM_" + target.name(), now, ids.usage);
                c.paid().finishReserve(reserveKey, receipt.getLong("version"), target, now);
                Document row = lifecycleRow(finalReservation, rejection);
                if (!expiry) c.paid().insertLifecycle(receipt, action, hash, row, target, rejection, now);
                return row;
            });
        });
        String rejection = result.getString("errorCode");
        if ("ENTITLEMENT_REVOKED".equals(rejection)) throw ReservationException.entitlementRevoked();
        Document snapshot = result.get("responseSnapshot", Document.class);
        Reservation.Status status = Reservation.Status.valueOf(snapshot.getString("reservationStatus"));
        if (!expiry && "CONFIRM".equals(action) && status != Reservation.Status.CONFIRMED) throw ReservationException.stateConflict();
        return new LifecycleResult(new IdempotencyCommand.LifecycleResponseSnapshot(operationId, reservationId, status,
                snapshot.getString("attemptGroupId"), status == Reservation.Status.CONFIRMED ? AttemptGroup.Status.OPEN : null,
                snapshot.getString("sessionId"), snapshot.getDate("terminalAt") == null ? null : snapshot.getDate("terminalAt").toInstant()), false);
    }
    private void finishConfirm(PaidExamTransactions.Context c, Document account, Document guard, Reservation r, Instant now, Ids ids) {
        EntitlementSourceSnapshot source = r.getSourceSnapshot();
        if (source.type() == EntitlementSourceSnapshot.Type.EXAM_PASS && r.getReservationKind() == Reservation.Kind.INITIAL) {
            var allocation = c.allocations().findByReservationId(r.getReservationId()).orElseThrow(ReservationException::temporarilyUnavailable);
            var grant = requireGrant(c, source.grantId());
            c.allocations().transitionHeld(allocation.getAllocationId(), allocation.getVersion(), ReservationAllocation.Status.CONSUMED, now).orElseThrow(ReservationException::temporarilyUnavailable);
            units(c.grants().consumeHeldOne(grant.getGrantId(), grant.getVersion(), now).orElseThrow(ReservationException::temporarilyUnavailable));
            var consumption = EntitlementLedgerEntry.consumed(ids.ledger, grant.getGrantId(), c.ledger().nextGrantSequence(grant.getGrantId()),
                    r.getSubjectRefId(), source.trialClaimId(), r.getReservationId(), allocation.getAllocationId(), now);
            c.ledger().insert(consumption); source = source.bindConsumption(consumption.getLedgerEventId());
            c.operations().updateFirst(new BasicQuery(new Document("_id", r.getReservationId())), new Update().set("sourceSnapshot", source), Reservation.class);
            c.operations().updateFirst(new BasicQuery(new Document("_id", r.getProposedSessionId())), new Update().set("sourceSnapshot", source), AttemptSession.class);
        }
        AttemptGroup previous = r.getPreviousGroupId() == null ? null : c.groups().findById(r.getPreviousGroupId()).orElseThrow(ReservationException::temporarilyUnavailable);
        if (previous != null) {
            if (previous.isReplacementBlocked()) throw ReservationException.transitionContextConflict();
            Long epoch = trialEpoch(c, account.getString("userId"), previous.getSubjectRefId(), now, true);
            if (!Objects.equals(epoch, r.getPreviousOwnerEpoch())) throw ReservationException.operationOwnerMismatch();
            if (r.getReservationKind() == Reservation.Kind.INITIAL) {
                restore(c, guard, account.getString("userId"), previous, source, now, ids);
                blockPrevious(c, previous, r.getOperationId(), now);
            } else if (previous.isReplacementBlocked() || previous.getStatus() == AttemptGroup.Status.COMPLETED)
                throw ReservationException.transitionContextConflict();
            allowOldCompletion(c, previous.getActiveSessionId());
        }
        if (r.getReservationKind() == Reservation.Kind.INITIAL) {
            c.groups().insert(AttemptGroup.open(r.getAttemptGroupId(), r.getSubjectRefId(), source.trialClaimId(), source.consumptionId(),
                    r.getMockExamId(), r.getProposedSessionId(), now).withSourceSnapshot(source));
        } else {
            AttemptGroup group = c.groups().findById(r.getAttemptGroupId()).orElseThrow(ReservationException::temporarilyUnavailable);
            c.groups().openWithSession(group.getAttemptGroupId(), group.getVersion(), r.getProposedSessionId(), now).orElseThrow(ReservationException::stateConflict);
        }
        var proposed = c.sessions().findBySessionId(r.getProposedSessionId()).orElseThrow(ReservationException::temporarilyUnavailable);
        c.sessions().activateProposed(proposed.getSessionId(), proposed.getAttemptGroupId(), proposed.getSubjectRefId(), proposed.getOperationId(), proposed.getVersion(), now)
                .orElseThrow(ReservationException::temporarilyUnavailable);
        Document latestGuard = existingGuard(c, account.getString("userId"));
        require(c.paid().confirmGuard(environment, account.getString("userId"), latestGuard.getLong("version"), r.getOperationId(), r.getReservationId(), r.getAttemptGroupId(), r.getProposedSessionId()));
    }
    private void restore(PaidExamTransactions.Context c, Document guard, String userId, AttemptGroup old, EntitlementSourceSnapshot source, Instant now, Ids ids) {
        if (source.type() != EntitlementSourceSnapshot.Type.SUBSCRIPTION || old.isReplacementBlocked() || old.getStatus() == AttemptGroup.Status.COMPLETED) return;
        Document oldRow = doc(c, "attempt_groups", old.getAttemptGroupId());
        if (oldRow.getBoolean("deleted", false) || oldRow.getBoolean("lifecycleDenied", false)) return;
        var oldSource = old.getSourceSnapshot();
        if (oldSource == null || oldSource.type() != EntitlementSourceSnapshot.Type.EXAM_PASS || oldSource.trialClaimId() == null || oldSource.consumptionId() == null) return;
        var grant = requireGrant(c, oldSource.grantId());
        require("TRIAL_CLAIM".equals(grant.getSourceType()) && oldSource.trialClaimId().equals(grant.getSourceId())
                && oldSource.consumptionId().equals(old.getConsumptionLedgerEventId()));
        Document consumption = doc(c, "entitlement_ledger", oldSource.consumptionId());
        require("CONSUMED".equals(consumption.getString("eventType")) && grant.getGrantId().equals(consumption.getString("aggregateId"))
                && oldSource.trialClaimId().equals(consumption.getString("trialClaimId")) && old.getSubjectRefId().equals(consumption.getString("subjectRefId")));
        if (c.operations().exists(new BasicQuery(new Document("environment", environment).append("sourceType", "TRIAL_PAID_RESTORATION")
                .append("sourceId", oldSource.consumptionId())), "entitlement_grants")) return;
        long order = c.paid().nextGrantOrder(environment, userId, guard.getLong("version"));
        var restored = EntitlementGrant.restoredTrial(ids.restoration, environment, userId, oldSource.consumptionId(), order, now);
        c.grants().insert(restored); c.ledger().insert(EntitlementLedgerEntry.restoredTrial(ids.restorationLedger, restored, now));
    }
    private void blockPrevious(PaidExamTransactions.Context c, AttemptGroup old, String operationId, Instant now) {
        require(c.operations().updateFirst(new BasicQuery(new Document("_id", old.getAttemptGroupId()).append("version", old.getVersion())),
                new Update().set("replacementBlocked", true).set("replacementBlockedBy", operationId).set("replacementBlockedAt", now)
                        .unset("openGuard").inc("version", 1), AttemptGroup.class).getModifiedCount() == 1);
    }
    private void allowOldCompletion(PaidExamTransactions.Context c, String sessionId) {
        if (sessionId == null) return;
        c.operations().updateFirst(new BasicQuery(new Document("_id", sessionId).append("state", "ACTIVE")),
                new Update().set("completionAllowed", true).unset("activeGuard").inc("version", 1), AttemptSession.class);
    }
    private void finishRelease(PaidExamTransactions.Context c, Document guard, Reservation r, String userId, Instant now, Ids ids) {
        if (r.getSourceSnapshot().type() == EntitlementSourceSnapshot.Type.EXAM_PASS && r.getReservationKind() == Reservation.Kind.INITIAL) {
            var allocation = c.allocations().findByReservationId(r.getReservationId()).orElseThrow(ReservationException::temporarilyUnavailable);
            var grant = requireGrant(c, allocation.getGrantId());
            // Current MEMBER grants have no revoke writer; do not resurrect a non-ACTIVE grant.
            require("ACTIVE".equals(grant.getState()));
            c.allocations().transitionHeld(allocation.getAllocationId(), allocation.getVersion(), ReservationAllocation.Status.RELEASED, now).orElseThrow(ReservationException::temporarilyUnavailable);
            units(c.grants().releaseHeldOne(grant.getGrantId(), grant.getVersion(), now).orElseThrow(ReservationException::temporarilyUnavailable));
            c.ledger().insert(EntitlementLedgerEntry.released(ids.ledger, grant.getGrantId(), c.ledger().nextGrantSequence(grant.getGrantId()),
                    r.getSubjectRefId(), r.getSourceSnapshot().trialClaimId(), r.getReservationId(), allocation.getAllocationId(), now));
        }
        var session = c.sessions().findBySessionId(r.getProposedSessionId()).orElseThrow(ReservationException::temporarilyUnavailable);
        c.sessions().failProposed(session.getSessionId(), session.getAttemptGroupId(), session.getSubjectRefId(), session.getOperationId(), session.getVersion(), now)
                .orElseThrow(ReservationException::temporarilyUnavailable);
        require(c.paid().release(environment, userId, guard.getLong("version"), r.getOperationId(), r.getReservationId()));
    }
    private Document activePaid(PaidExamTransactions.Context c, Document account) {
        // Shared lifecycle/recovery write fence already serializes even an empty timeline with purchase application.
        List<Document> rows = c.operations().find(new BasicQuery(new Document("accountId", account.getString("_id")).append("revokedAt", null)
                .append("startsAt", new Document("$lte", Date.from(clock.instant()))).append("endsAt", new Document("$gt", Date.from(clock.instant())))).limit(2),
                Document.class, "subscription_entitlements");
        if (rows.size() > 1) throw ReservationException.temporarilyUnavailable();
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private EntitlementSourceSnapshot paidSource(PaidExamTransactions.Context c, Document account, Document entitlement, Instant now) {
        Document purchase = doc(c, "purchases", entitlement.getString("purchaseId"));
        require(account.getString("_id").equals(purchase.getString("accountId")) && payment.getEnvironment().equals(purchase.getString("environment")));
        if (!"VERIFIED".equals(purchase.getString("purchaseState")) || !"ALLOWED".equals(purchase.getString("accessState"))
                || !"READY".equals(purchase.getString("processingStatus"))) throw ReservationException.temporarilyUnavailable();
        touch(c, "purchases", purchase); touch(c, "subscription_entitlements", entitlement);
        return new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.SUBSCRIPTION, null, null, null, account.getString("_id"),
                purchase.getString("_id"), entitlement.getString("_id"), now, entitlement.getDate("startsAt").toInstant(), entitlement.getDate("endsAt").toInstant());
    }
    private boolean sourceAllowed(PaidExamTransactions.Context c, Document account, Reservation r, Instant now) {
        var source = r.getSourceSnapshot(); require(source != null);
        if (source.type() == EntitlementSourceSnapshot.Type.SUBSCRIPTION) {
            require(account.getString("_id").equals(source.accountId()));
            Document purchase = doc(c, "purchases", source.purchaseId()), entitlement = doc(c, "subscription_entitlements", source.entitlementId());
            require(source.accountId().equals(purchase.getString("accountId")) && source.accountId().equals(entitlement.getString("accountId"))
                    && source.purchaseId().equals(entitlement.getString("purchaseId")));
            if (!"READY".equals(purchase.getString("processingStatus"))) throw ReservationException.temporarilyUnavailable();
            touch(c, "purchases", purchase); touch(c, "subscription_entitlements", entitlement);
            return "VERIFIED".equals(purchase.getString("purchaseState")) && "ALLOWED".equals(purchase.getString("accessState")) && entitlement.get("revokedAt") == null;
        }
        EntitlementGrant grant = requireGrant(c, source.grantId());
        if (source.trialClaimId() != null) {
            Long epoch = trialEpoch(c, account.getString("userId"), r.getSubjectRefId(), now, true);
            if (!Objects.equals(epoch, r.getSessionOwnerEpoch())) throw ReservationException.operationOwnerMismatch();
        } else require(account.getString("userId").equals(grant.getOwnerUserId()) && environment.equals(grant.getEnvironment()));
        return "ACTIVE".equals(grant.getState());
    }
    private void verifyTrial(PaidExamTransactions.Context c, String userId, String claimId, Instant now) {
        var eligibility = new web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository(c.operations())
                .findByScopeAndUser(trialScope, userId)
                .filter(web.tosunsaeng.billing.domain.entitlement.trial.application.TrialEntitlementPolicy::verified)
                .orElseThrow(ReservationException::entitlementInsufficient);
        var aliases = new web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialCandidateAliasRepository(c.operations())
                .findActiveMatches("FREE_EXAM_ONCE", eligibility.getCandidates(), now);
        Set<String> claims = new HashSet<>(); aliases.forEach(a -> claims.add(a.getTrialClaimId()));
        if (claims.size() != 1 || !claims.contains(claimId)) throw ReservationException.entitlementInsufficient();
        new web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository(c.operations()).findById(claimId)
                .filter(claim -> claim.getState() == web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim.State.ACTIVE
                        && claim.getRetentionExpiresAt().isAfter(now)).orElseThrow(ReservationException::entitlementInsufficient);
    }
    private List<EntitlementGrant> availableGrants(PaidExamTransactions.Context c, String userId, Instant now) {
        List<EntitlementGrant> grants = new ArrayList<>(memberGrants(c, userId));
        for (BillingSubjectLink link : new BillingSubjectLinkRepository(c.operations()).findActiveByOwnerAndScope(userId, trialScope, now, 101)) {
            try { verifyTrial(c, userId, link.getTrialClaimId(), now); }
            catch (ReservationException ineligible) {
                if (!"ENTITLEMENT_INSUFFICIENT".equals(ineligible.code())) throw ineligible;
                continue;
            }
            c.grants().findByClaimAndBenefitCode(link.getTrialClaimId(), "FREE_EXAM_ONCE")
                    .filter(g -> "ACTIVE".equals(g.getState()) && g.getAvailableUnits() > 0).ifPresent(grants::add);
        }
        if (grants.stream().anyMatch(g -> g.getCreatedAt() == null)) throw ReservationException.temporarilyUnavailable();
        grants.sort(Comparator.comparing(EntitlementGrant::getCreatedAt)
                .thenComparing(g -> g.getGrantOrder() == null ? 0L : g.getGrantOrder()).thenComparing(EntitlementGrant::getGrantId));
        return grants;
    }
    private List<EntitlementGrant> memberGrants(PaidExamTransactions.Context c, String userId) {
        return c.operations().find(new BasicQuery(new Document("environment", environment).append("ownerUserId", userId)
                .append("state", "ACTIVE").append("availableUnits", new Document("$gt", 0)))
                .with(Sort.by("grantOrder")).limit(1), EntitlementGrant.class);
    }
    private Long trialEpoch(PaidExamTransactions.Context c, String userId, String subject, Instant now, boolean write) {
        var repo = new BillingSubjectLinkRepository(c.operations());
        BillingSubjectLink link = repo.findBySubjectRefId(subject).orElse(null);
        if (link == null) return null;
        if (!link.isActive() || !userId.equals(link.getUserId()) || !trialScope.equals(link.getConsumerScopeId()) || !link.getRetentionExpiresAt().isAfter(now))
            throw ReservationException.operationOwnerMismatch();
        if (write) link = repo.bindSession(link, now).orElseThrow(ReservationException::temporarilyUnavailable);
        return link.getSessionOwnerEpoch();
    }
    private Document guard(PaidExamTransactions.Context c, String userId, Document account) {
        Document guard = c.operations().findOne(new BasicQuery(new Document("environment", environment).append("userId", userId)), Document.class, PaidExamStore.GUARDS);
        return guard == null ? c.paid().createGuard(environment, userId, new Document("kind", "MEMBER_ACCOUNT")
                .append("reference", account.getString("_id")).append("version", account.getLong("version")).append("epoch", null)) : guard;
    }
    private Document existingGuard(PaidExamTransactions.Context c, String userId) {
        Document guard = c.operations().findOne(new BasicQuery(new Document("environment", environment).append("userId", userId)), Document.class, PaidExamStore.GUARDS);
        require(guard != null); return guard;
    }
    private Document account(PaidExamTransactions.Context c, String userId) {
        Document account = c.operations().findOne(new BasicQuery(new Document("environment", payment.getEnvironment()).append("userId", userId)), Document.class, "payment_accounts");
        require(account != null); return account;
    }
    private void usage(PaidExamTransactions.Context c, Reservation r, String type, Instant now, String ledgerId) {
        var source = r.getSourceSnapshot(); if (source.type() != EntitlementSourceSnapshot.Type.SUBSCRIPTION) return;
        c.operations().insert(new Document("_id", ledgerId).append("effectKey", type + ":" + environment + ":" + r.getReservationId())
                .append("type", type).append("purchaseId", source.purchaseId()).append("entitlementId", source.entitlementId())
                .append("occurredAt", Date.from(now)).append("sourceKind", "EXAM_OPERATION")
                .append("operationId", r.getOperationId()).append("reservationId", r.getReservationId())
                .append("detail", new Document("attemptGroupId", r.getAttemptGroupId()).append("sessionId", r.getProposedSessionId())), "payment_ledger");
    }
    private static Document lifecycleRow(Reservation r, String rejection) {
        return new Document("errorCode", rejection).append("responseSnapshot", new Document("operationId", r.getOperationId())
                .append("reservationId", r.getReservationId()).append("reservationStatus", r.getStatus().name())
                .append("attemptGroupId", r.getAttemptGroupId()).append("sessionId", r.getProposedSessionId())
                .append("terminalAt", r.getTerminalAt() == null ? null : Date.from(r.getTerminalAt())));
    }
    private static boolean samePaid(EntitlementSourceSnapshot a, EntitlementSourceSnapshot b) {
        return a != null && a.type() == EntitlementSourceSnapshot.Type.SUBSCRIPTION && a.entitlementId().equals(b.entitlementId()) && a.purchaseId().equals(b.purchaseId());
    }
    private static Document doc(PaidExamTransactions.Context c, String collection, String id) {
        Document row = c.operations().findById(id, Document.class, collection); require(row != null); return row;
    }
    private static void touch(PaidExamTransactions.Context c, String collection, Document row) {
        require(row.get("version") instanceof Long v && v > 0 && v < Long.MAX_VALUE);
        require(c.operations().updateFirst(new BasicQuery(new Document("_id", row.get("_id")).append("version", row.getLong("version"))),
                new Update().inc("version", 1), collection).getModifiedCount() == 1);
    }
    private static EntitlementGrant requireGrant(PaidExamTransactions.Context c, String id) { return c.grants().findById(id).orElseThrow(ReservationException::temporarilyUnavailable); }
    private static void units(EntitlementGrant grant) { require(grant.getAvailableUnits() >= 0 && grant.getHeldUnits() >= 0 && grant.getConsumedUnits() >= 0
            && grant.getTotalUnits() == grant.getAvailableUnits() + grant.getHeldUnits() + grant.getConsumedUnits()); }
    private static void require(boolean condition) { if (!condition) throw ReservationException.temporarilyUnavailable(); }
    private static final class Ids {
        final String reservation = uuid(), group = uuid(), allocation = uuid(), ledger = uuid(), usage = uuid(),
                restoration = uuid(), restorationLedger = uuid();
        final PaidTrialFunding.ReserveIds trial = PaidTrialFunding.ReserveIds.create();
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static <T> T translated(Supplier<T> work) { try { return work.get(); } catch (InternalApiException expected) { throw expected; }
        catch (RuntimeException unavailable) { throw ReservationException.temporarilyUnavailable(); } }
    private static final class ContextConflict extends RuntimeException { }
}
