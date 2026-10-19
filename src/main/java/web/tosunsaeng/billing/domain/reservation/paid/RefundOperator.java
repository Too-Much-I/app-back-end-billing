package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** Explicit operator invocation only. Target mutation and idempotent audit commit together. */
public final class RefundOperator {
    public enum Action { OUTBOX_RESUME, FANOUT_RESUME, GROUP_RECHECK }
    public enum Reason { CREDENTIALS_FIXED, DEPENDENCY_RECOVERED, CONTRACT_FIXED, EVIDENCE_RECHECKED }
    public record Command(String operationId, Action action, String target, String group, long version,
                          long generation, String epoch, Reason reason) {
        public Command {
            RefundOperationsReader.uuid(operationId); RefundOperationsReader.uuid(target); RefundOperationsReader.uuid(epoch);
            require(action != null && reason != null && version > 0 && version < Long.MAX_VALUE && generation > 0 && generation < Long.MAX_VALUE);
            if (action == Action.GROUP_RECHECK) { RefundOperationsReader.uuid(group); require(reason == Reason.EVIDENCE_RECHECKED); }
            else require(group == null && reason != Reason.EVIDENCE_RECHECKED);
        }
        @Override public String toString() { return "RefundOperatorCommand[REDACTED]"; }
    }
    private final PaidExamTransactions tx;
    private final PaymentRecoveryFence fence;
    private final Clock clock;
    private final String environment, paymentEnvironment, scope;
    public RefundOperator(PaidExamTransactions tx, PaymentStore store, String environment, String scope, Clock clock) {
        require(Set.of("test", "staging", "production").contains(environment) && scope != null && scope.matches("[A-Za-z0-9_.:-]{1,128}"));
        this.tx = tx; this.environment = environment; this.scope = scope; this.clock = clock;
        paymentEnvironment = environment.equals("production") ? "PRODUCTION" : "SANDBOX";
        PaymentProperties p = new PaymentProperties(); p.setEnvironment(paymentEnvironment); p.getRecovery().setFenceRequired(true);
        fence = new PaymentRecoveryFence(store, p);
        RefundFanoutSchema.verify(store.database()); RefundOperatorSchema.verify(store.database());
    }
    public String result(String operationId) {
        RefundOperationsReader.uuid(operationId);
        return tx.execute(c -> {
            Document receipt = c.operations().findById(operationId, Document.class, RefundOperatorSchema.COLLECTION);
            if (receipt == null) return "NOT_FOUND";
            require(environment.equals(receipt.getString("environment")));
            return receipt.getString("result");
        });
    }
    public String execute(Command command, String actorDigest, boolean apply, boolean isolated) {
        require(actorDigest != null && actorDigest.matches("[0-9a-f]{64}") && (!apply || isolated));
        String digest = RefundFanout.digest(new Document("environment", environment).append("scope", scope)
                .append("action", command.action().name()).append("target", command.target()).append("group", command.group())
                .append("version", command.version()).append("generation", command.generation()).append("epoch", command.epoch())
                .append("reason", command.reason().name()).append("actor", actorDigest).toJson());
        return tx.execute(c -> {
            Document receipt = c.operations().findById(command.operationId(), Document.class, RefundOperatorSchema.COLLECTION);
            if (receipt != null) { require(digest.equals(receipt.getString("digest"))); return receipt.getString("result"); }
            var ticket = new PaymentRecoveryFence.Ticket(command.epoch());
            fence.check(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            String collection = command.action() == Action.OUTBOX_RESUME ? RefundFanout.OUTBOX : "purchases";
            String field = command.action() == Action.OUTBOX_RESUME ? "work" : "refundWork";
            Document key = new Document("_id", command.target()).append("environment", command.action() == Action.OUTBOX_RESUME ? environment : paymentEnvironment);
            Document target = c.operations().findOne(new BasicQuery(key), Document.class, collection);
            require(target != null);
            Document work = target.get(field, Document.class);
            require(work != null && Objects.equals(work.getLong("leaseVersion"), command.version()) && Objects.equals(work.getLong("generation"), command.generation())
                    && (work.getDate("until") == null || !work.getDate("until").toInstant().isAfter(clock.instant())));
            Document group = null;
            if (command.action() == Action.GROUP_RECHECK) {
                require("REVOKED".equals(target.getString("accessState")) && "READY".equals(work.getString("state")));
                group = c.operations().findById(command.group(), Document.class, "attempt_groups");
                require(group != null && group.get("refundReview", Document.class) != null
                        && "OPEN".equals(group.get("refundReview", Document.class).getString("state"))
                        && target.get("refund", Document.class).getString("effectId").equals(group.get("refundReview", Document.class).getString("effectId")));
            } else {
                require(Set.of("BLOCKED_AUTH", "REVIEW_REQUIRED").contains(work.getString("state")));
                if ("BLOCKED_AUTH".equals(work.getString("state"))) require(command.reason() == Reason.CREDENTIALS_FIXED);
                if (command.action() == Action.FANOUT_RESUME) require("REVOKED".equals(target.getString("accessState")));
                else require(RefundFanout.digest(target.getString("body")).equals(target.getString("digest")) && scope.equals(target.getString("consumerScopeId")));
                // A manual resume cannot bypass a valid provider Retry-After deadline.
                if (command.action() == Action.OUTBOX_RESUME) require(work.getDate("nextAttemptAt") == null || !work.getDate("nextAttemptAt").toInstant().isAfter(clock.instant()));
            }
            if (!apply) return "DRY_RUN_READY";
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            Instant now = clock.instant(); String result = "RESUMED";
            if (command.action() == Action.GROUP_RECHECK) {
                RefundFanout.reconcileGroup(c, target, group, now, environment, scope, paymentEnvironment);
                Document updated = c.operations().findById(command.group(), Document.class, "attempt_groups");
                result = "RESOLVED".equals(updated.get("refundReview", Document.class).getString("state")) ? "RESOLVED" : "STILL_OPEN";
            }
            var update = new Update().inc(field + ".leaseVersion", 1L).inc(field + ".generation", 1L)
                    .set(field + ".owner", null).set(field + ".until", null);
            if (command.action() != Action.GROUP_RECHECK) update.set(field + ".state", "READY").set(field + ".failure", null)
                    .set(field + ".attempts", 0L).set(field + ".retryStartedAt", Date.from(now))
                    .set(field + ".nextAttemptAt", "DONE".equals(work.getString("phase")) ? null : Date.from(now));
            require(c.operations().updateFirst(new BasicQuery(key.append(field + ".leaseVersion", command.version()).append(field + ".generation", command.generation())), update, collection).getModifiedCount() == 1);
            c.operations().insert(new Document("_id", command.operationId()).append("digest", digest).append("actorDigest", actorDigest)
                    .append("targetDigest", RefundFanout.digest(command.target())).append("environment", environment)
                    .append("action", command.action().name()).append("reason", command.reason().name())
                    .append("generation", command.generation() + 1).append("result", result).append("completedAt", Date.from(now)), RefundOperatorSchema.COLLECTION);
            return result;
        });
    }
    private static void require(boolean condition) { if (!condition) throw new IllegalStateException("Refund operator precondition failed"); }
}
