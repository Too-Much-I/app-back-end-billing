package web.tosunsaeng.billing.domain.payment.worker;

import java.time.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseCore;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.*;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Explicitly constructed C1 worker. No scheduler or external provider calls are registered yet. */
public final class PaymentSourceWorker {
    public record Tick(int claimed, int completed, int deferred, int failed) {}
    private final PaymentSourceWork work;
    private final PaymentStore store;
    private final PurchaseCore core;
    private final PaymentSyncService sync;
    private final Clock clock;
    private final String environment;
    private final DoubleSupplier jitter;
    public PaymentSourceWorker(PaymentStore store, PaymentProperties p, PurchaseCore core, PaymentSyncService sync, Clock clock, DoubleSupplier jitter) {
        this.store = store; this.core = core; this.sync = sync; this.clock = clock; this.jitter = jitter;
        this.environment = p.getEnvironment(); this.work = new PaymentSourceWork(store, p, clock); PaymentSyncSchema.verify(store.database());
    }
    public Tick tick(int batch) {
        int claimed = 0, completed = 0, deferred = 0, failed = 0;
        // Snapshot the candidate IDs once: rescheduling a row cannot loop inside the same tick.
        for (String id : work.due(batch)) {
            PaymentSourceWork.Lease lease = null;
            try {
                lease = work.claim(id); if (lease == null) continue; claimed++;
                if (process(lease)) completed++; else deferred++;
            } catch (RuntimeException failure) {
                failed++;
                if (lease != null) {
                    try {
                        boolean exhausted = !clock.instant().isBefore(lease.retryStartedAt().plus(Duration.ofHours(24)));
                        if (exhausted) {
                            var current = lease;
                            core.handoffWorkerFailure(lease.eventKey(), lease.ticket(), s -> work.guard(s, current));
                            // Preserve unresolved sync/pending; an operator must inspect the parked terminal handoff.
                            work.finish(lease, null, "RETRY_EXHAUSTED");
                        } else work.finish(lease, retryAt(lease), "TEMPORARILY_UNAVAILABLE");
                    } catch (RuntimeException staleOrUnavailable) { /* No unguarded fallback. Expired lease is reclaimed later. */ }
                }
            }
        }
        return new Tick(claimed, completed, deferred, failed);
    }
    private boolean process(PaymentSourceWork.Lease lease) {
        Document row = work.read(lease);
        if ("RECEIVED".equals(row.getString("state"))) {
            if (!clock.instant().isBefore(lease.retryStartedAt().plus(Duration.ofHours(24)))) {
                core.handoffWorkerFailure(lease.eventKey(), lease.ticket(), s -> work.guard(s, lease));
            } else {
                var outcome = core.apply(lease.eventKey(), lease.ticket(), s -> work.guard(s, lease));
                if (outcome == PurchaseCore.Outcome.PURCHASE_BLOCKED) {
                    work.finish(lease, retryAt(lease), "PURCHASE_BLOCKED"); return false;
                }
            }
            row = work.read(lease);
        }
        Document observation = row.get("normalizedObservation", Document.class);
        require(observation != null && Set.of("APPLIED", "QUARANTINED").contains(row.getString("state")));
        Document query = new Document("environment", environment).append("state", "PENDING").append("accountId", row.get("accountId"))
                .append("lifecycleId", row.get("lifecycleId"));
        for (String f : List.of("store", "appId", "lookupKeyVersion")) query.put(f, observation.get(f));
        query.put("$and", List.of(new Document("$or", List.of(new Document("transactionKey", observation.get("transactionKey")),
                new Document("sandboxTransactionKey", observation.get("transactionKey"))))));
        // Do not steal a command already bound to a different durable observation.
        query.put("$or", List.of(new Document("sourceObservationKey", null), new Document("sourceObservationKey", lease.eventKey())));
        List<Document> commands = store.database().getCollection(PaymentSyncSchema.COLLECTION).find(query)
                .sort(new Document("createdAt", 1).append("_id", 1)).limit(101).into(new ArrayList<>());
        boolean deferred = commands.size() > 100;
        for (Document command : commands.subList(0, Math.min(100, commands.size()))) {
            work.renew(lease);
            long version = sync.bindObservation(command.getString("_id"), command.getLong("version"), lease.eventKey(), lease.ticket(), s -> work.guard(s, lease));
            sync.complete(command.getString("_id"), version, lease.ticket(), s -> work.guard(s, lease));
            Document after = store.database().getCollection(PaymentSyncSchema.COLLECTION).find(new Document("_id", command.get("_id"))).first();
            require(after != null); deferred |= "PENDING".equals(after.getString("state"));
        }
        work.finish(lease, deferred ? retryAt(lease) : null, deferred ? "SYNC_PENDING" : null); return !deferred;
    }
    private Instant retryAt(PaymentSourceWork.Lease lease) {
        double random = jitter.getAsDouble(); require(random >= 0 && random < 1);
        long base = Math.min(3600, 5L << (int) Math.min(10, lease.attempts() - 1));
        return clock.instant().plusMillis(Math.min(3_600_000, (long) (base * 1000 * (1 + random))));
    }
}
