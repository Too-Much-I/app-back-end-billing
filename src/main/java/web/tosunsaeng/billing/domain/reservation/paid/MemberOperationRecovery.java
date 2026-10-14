package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;
import web.tosunsaeng.billing.global.exception.InternalApiException;

/** MEMBER-owned admission boundary. The paid resolver supplies its writes in this SAME transaction.
 * Not used by the legacy trial resolver, and never invoked by a status worker to create a reservation.
 */
public final class MemberOperationRecovery {
    @FunctionalInterface public interface Admission {
        /** Return the inserted reservation ID, or empty for the exact pre-creation context rejection.
         * No external calls, nested transaction, or side effects outside Context are permitted. */
        Optional<String> reserve(PaidExamTransactions.Context context, ReserveCommand command);
    }
    public record Result(Document responseSnapshot, Document source) {
        public Result { responseSnapshot = copy(responseSnapshot); source = copy(source); }
        @Override public Document responseSnapshot() { return copy(responseSnapshot); }
        @Override public Document source() { return copy(source); }
        @Override public String toString() { return "RecoveredReservation[REDACTED]"; }
    }
    private final PaidExamTransactions transactions;
    private final PaymentProperties properties;
    private final PaymentRecoveryFence recoveryFence;
    private final String environment, issuer;
    private final Clock clock;
    public MemberOperationRecovery(PaidExamTransactions transactions, PaymentStore store, PaymentProperties properties,
            String environment, String issuer, Clock clock) {
        this.transactions = transactions; this.properties = properties; this.environment = environment; this.issuer = issuer; this.clock = clock;
        boolean mapping = Set.of("test", "staging").contains(environment) && "SANDBOX".equals(properties.getEnvironment())
                || "production".equals(environment) && "PRODUCTION".equals(properties.getEnvironment());
        if (!mapping || issuer == null || issuer.isBlank() || !properties.getRecovery().isFenceRequired())
            throw new IllegalArgumentException("Member recovery requires trusted environment, issuer and recovery fence");
        recoveryFence = new PaymentRecoveryFence(store, properties);
    }
    public Result reserve(ReserveCommand command, Admission admission) {
        Objects.requireNonNull(admission);
        var key = key(command.userId(), command.operationId());
        Document request = request(command);
        Document receipt = translated(() -> {
            var ticket = recoveryFence.capture();
            return transactions.execute(context -> {
                recoveryFence.acquire(context.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                Document account = currentAccount(context, key, true);
                Document existing = context.paid().receipt(key).orElse(null);
                boolean newlyCreated = existing == null;
                if (existing != null) {
                    owner(existing, account);
                    if (!command.payloadHash().equals(existing.getString("payloadHash"))) throw ReservationException.idempotencyConflict();
                    if (!Long.valueOf(2).equals(existing.get("schemaVersion")) || !request.equals(existing.get("requestSnapshot")))
                        throw ReservationException.temporarilyUnavailable(); // Legacy/missing evidence must never be reconstructed.
                    consistency(context, existing);
                    if (!"WAITING_MEMBER_EVIDENCE".equals(existing.getString("state"))) return existing;
                } else {
                    // This path cannot replace an operation whose command cache or durable reservation already exists.
                    if (context.operations().exists(new BasicQuery(new Document("callerService", "LEARNING_CORE")
                            .append("operationId", key.operationId()).append("subjectRefId", account.getString("paidSubjectRefId"))), "reservations"))
                        throw ReservationException.temporarilyUnavailable();
                    existing = context.paid().insertWaiting(key, command.payloadHash(), owner(account), clock.instant(), request);
                }
                Document evidence = account.get("memberEvidence", Document.class);
                if (evidence == null) return existing;
                if (!VerifiedMemberEvidence.matches(evidence, key.userId(), properties.getEnvironment(), issuer))
                    throw ReservationException.temporarilyUnavailable();
                Optional<String> reservationId = admission.reserve(context, command);
                boolean saved;
                if (reservationId.isEmpty()) {
                    consistency(context, existing); // Terminal receipt cannot coexist with a reservation.
                    saved = context.paid().rejectWaiting(key, command.payloadHash(), existing.getLong("version"), clock.instant());
                } else {
                    Reservation reservation = context.reservations().findById(reservationId.get()).orElseThrow(ReservationException::temporarilyUnavailable);
                    if (!command.operationId().equals(reservation.getOperationId()) || !command.payloadHash().equals(reservation.getPayloadHash())
                            || !command.sessionId().equals(reservation.getProposedSessionId()) || !command.mockExamId().equals(reservation.getMockExamId())
                            || !account.getString("paidSubjectRefId").equals(reservation.getSubjectRefId())
                            || reservation.getSourceSnapshot() == null || reservation.getStatus() != Reservation.Status.RESERVED)
                        throw ReservationException.temporarilyUnavailable();
                    saved = context.paid().reserveWaiting(key, command.payloadHash(), existing.getLong("version"), reservationId.get(),
                            reservation.getSourceSnapshot(), snapshot(reservation));
                }
                if (!saved) throw ReservationException.temporarilyUnavailable();
                if (newlyCreated) {
                    // A staged row resolved in this transaction was never an observed MEMBER wait.
                    if (context.operations().updateFirst(new BasicQuery(new Document("_id", existing.get("_id"))
                                    .append("version", existing.getLong("version") + 1)),
                            new Update().set("waitingSince", null), PaidExamStore.RECEIPTS).getMatchedCount() != 1)
                        throw ReservationException.temporarilyUnavailable();
                }
                return context.paid().receipt(key).orElseThrow(ReservationException::temporarilyUnavailable);
            });
        });
        // Throw only AFTER the receipt commit, including a terminal rejection.
        outcome(receipt, false);
        return new Result(receipt.get("responseSnapshot", Document.class), receipt.get("source", Document.class));
    }
    /** Existing body lookup; no evidence refresh, lifecycle increment, reservation, or receipt write. */
    public Optional<ReservationStatusResult> status(String userId, String operationId) {
        var key = key(userId, operationId);
        return translated(() -> {
            var ticket = recoveryFence.capture();
            return transactions.execute(context -> {
                recoveryFence.check(context.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                Document receipt = context.paid().receipt(key).orElse(null);
                if (receipt == null) return Optional.empty(); // Existing free status remains authoritative for its own operations.
                Document account = currentAccount(context, key, false);
                owner(receipt, account);
                consistency(context, receipt);
                outcome(receipt, true);
                Reservation r = context.reservations().findById(receipt.getString("reservationId"))
                        .orElseThrow(ReservationException::temporarilyUnavailable);
                var groupStatus = context.groups().findById(r.getAttemptGroupId()).map(g -> g.getStatus()).orElse(null);
                return Optional.of(new ReservationStatusResult(operationId, r.getReservationId(), r.getReservationKind(), r.getStatus(),
                        r.getAttemptGroupId(), groupStatus, r.getProposedSessionId(), r.getMockExamId(), r.getContinuationReason(),
                        r.getContinuationId(), r.getExpiresAt(), r.getTerminalAt(),
                        EntitlementSourceSnapshot.Type.valueOf(receipt.get("source", Document.class).getString("type"))));
            });
        });
    }
    private Document currentAccount(PaidExamTransactions.Context context, PaidExamStore.Key key, boolean write) {
        var query = new Document("environment", properties.getEnvironment()).append("userId", key.userId());
        Document lifecycle = context.operations().findOne(new BasicQuery(query), Document.class, "payment_account_lifecycles");
        Document account = context.operations().findOne(new BasicQuery(query), Document.class, "payment_accounts");
        if (lifecycle != null && "WITHDRAWN".equals(lifecycle.getString("state"))
                || account != null && "WITHDRAWN".equals(account.getString("state"))) throw ReservationException.operationOwnerMismatch();
        if (lifecycle == null || account == null || !"ACTIVE".equals(lifecycle.getString("state"))
                || !"ACTIVE".equals(account.getString("state"))) throw ReservationException.temporarilyUnavailable();
        for (Document row : List.of(lifecycle, account))
            if (!(row.get("version") instanceof Long v) || v < 1 || v == Long.MAX_VALUE) throw ReservationException.temporarilyUnavailable();
        if (write) {
            // The withdrawal applier updates this same lifecycle document in its transaction.
            if (context.operations().updateFirst(new BasicQuery(new Document("_id", lifecycle.get("_id"))
                            .append("state", "ACTIVE").append("version", lifecycle.getLong("version"))),
                    new Update().inc("version", 1), "payment_account_lifecycles").getModifiedCount() != 1)
                throw ReservationException.temporarilyUnavailable();
        }
        return account;
    }
    private static Document owner(Document account) {
        return new Document("kind", "MEMBER_ACCOUNT").append("reference", account.getString("_id"))
                .append("version", account.getLong("version")).append("epoch", null);
    }
    private static void owner(Document receipt, Document account) {
        Document owner = receipt.get("owner", Document.class);
        if (owner == null || !"MEMBER_ACCOUNT".equals(owner.getString("kind")) || owner.get("epoch") != null
                || !Objects.equals(account.getString("_id"), owner.getString("reference"))) throw ReservationException.operationOwnerMismatch();
        if (!(owner.get("version") instanceof Long v) || v < 1 || account.getLong("version") < v)
            throw ReservationException.temporarilyUnavailable();
        // Account version advances naturally on evidence refresh; it is not an identity/owner epoch.
    }
    private static void consistency(PaidExamTransactions.Context context, Document receipt) {
        String state = receipt.getString("state");
        Document accountOwner = receipt.get("owner", Document.class);
        Document account = context.operations().findById(accountOwner.getString("reference"), Document.class, "payment_accounts");
        if (account == null) throw ReservationException.temporarilyUnavailable();
        List<Reservation> reservations = context.operations().find(new BasicQuery(new Document("callerService", "LEARNING_CORE")
                .append("subjectRefId", account.getString("paidSubjectRefId")).append("operationId", receipt.getString("operationId"))).limit(2), Reservation.class);
        if (Set.of("WAITING_MEMBER_EVIDENCE", "FAILED_TERMINAL").contains(state)) {
            if (!reservations.isEmpty()) throw ReservationException.temporarilyUnavailable();
        } else {
            if (reservations.size() != 1 || !Objects.equals(reservations.getFirst().getReservationId(), receipt.getString("reservationId"))
                    || !Objects.equals(reservations.getFirst().getPayloadHash(), receipt.getString("payloadHash"))
                    || reservations.getFirst().getSourceSnapshot() == null)
                throw ReservationException.temporarilyUnavailable();
            // Snapshot is the original reserve outcome; later lifecycle/source consumption binding must not rewrite it.
            Document originalSource = receipt.get("source", Document.class);
            Document currentSource = reservations.getFirst().getSourceSnapshot().toDocument();
            if (originalSource == null) throw ReservationException.temporarilyUnavailable();
            if (originalSource.get("consumptionId") == null) currentSource.put("consumptionId", null);
            if (!originalSource.equals(currentSource)) throw ReservationException.temporarilyUnavailable();
            Document expected = snapshot(reservations.getFirst());
            Document stored = receipt.get("responseSnapshot", Document.class);
            if (stored == null || !stored.equals(expected)) throw ReservationException.temporarilyUnavailable();
        }
    }
    private static void outcome(Document receipt, boolean status) {
        switch (receipt.getString("state")) {
            case "WAITING_MEMBER_EVIDENCE" -> throw ReservationException.memberEvidencePending();
            case "FAILED_TERMINAL" -> {
                if (!"TRANSITION_CONTEXT_CONFLICT".equals(receipt.getString("errorCode"))) throw ReservationException.temporarilyUnavailable();
                throw status ? ReservationException.rejectedBeforeCreation() : ReservationException.transitionContextConflict();
            }
            case "RESERVED", "CONFIRMED", "CANCELED", "EXPIRED" -> { }
            default -> throw ReservationException.temporarilyUnavailable();
        }
    }
    private PaidExamStore.Key key(String userId, String operationId) { return new PaidExamStore.Key(environment, userId, operationId, "RESERVE"); }
    private static Document request(ReserveCommand c) {
        for (String token : List.of(c.sessionId(), c.mockExamId()))
            if (token.isBlank() || token.length() > 128 || !token.equals(token.strip()) || token.chars().anyMatch(Character::isISOControl))
                throw InternalApiException.invalidRequest();
        if ((c.continuationReason() == null) != (c.continuationId() == null)
                || (c.continuationReason() == null) != (c.expectedAttemptGroupId() == null)) throw InternalApiException.invalidRequest();
        ReserveRequest request = new ReserveRequest(c.userId(), c.sessionId(), c.mockExamId(), c.continuationReason(), c.continuationId(), c.expectedAttemptGroupId());
        if (!new ReservePayloadHasher().hash(request).equals(c.payloadHash())) throw InternalApiException.invalidRequest();
        return new Document("userId", c.userId()).append("sessionId", c.sessionId()).append("mockExamId", c.mockExamId())
                .append("continuationReason", c.continuationReason() == null ? null : c.continuationReason().name())
                .append("continuationId", c.continuationId()).append("expectedAttemptGroupId", c.expectedAttemptGroupId());
    }
    private static Document snapshot(Reservation r) {
        return new Document("operationId", r.getOperationId()).append("reservationId", r.getReservationId())
                .append("reservationKind", r.getReservationKind().name()).append("reservationStatus", "RESERVED")
                .append("attemptGroupId", r.getAttemptGroupId()).append("sessionId", r.getProposedSessionId()).append("mockExamId", r.getMockExamId())
                .append("continuationReason", r.getContinuationReason() == null ? null : r.getContinuationReason().name())
                .append("continuationId", r.getContinuationId()).append("expiresAt", Date.from(r.getExpiresAt()));
    }
    private static Document copy(Document value) { return Document.parse(value.toJson()); }
    private static <T> T translated(Supplier<T> work) {
        try { return work.get(); }
        catch (InternalApiException expected) { throw expected; }
        catch (RuntimeException unavailable) { throw ReservationException.temporarilyUnavailable(); }
    }
}
