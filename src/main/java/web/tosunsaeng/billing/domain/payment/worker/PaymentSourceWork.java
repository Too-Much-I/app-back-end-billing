package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Durable operational metadata on the receipt itself. No additional payment state ledger. */
public final class PaymentSourceWork {
    public static final String COLLECTION = "payment_provider_inbox";
    public record Lease(String id, String eventKey, String owner, long version, long generation,
                        PaymentRecoveryFence.Ticket ticket, Instant retryStartedAt, long attempts) {
        @Override public String toString() { return "PaymentSourceLease[REDACTED]"; }
    }
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final Clock clock;
    private final String environment;
    public PaymentSourceWork(PaymentStore store, PaymentProperties p, Clock clock) {
        require(p.getRecovery().isFenceRequired()); this.store = store; this.fence = new PaymentRecoveryFence(store, p);
        this.clock = clock; this.environment = p.getEnvironment(); PurchaseCoreSchema.verify(store.database());
    }
    /** Two bounded indexed streams, interleaved so completed purchases waiting for sync cannot starve. */
    public List<String> due(int limit) {
        require(limit >= 1 && limit <= 100); Date now = Date.from(clock.instant());
        List<Document> fresh = rows().find(env().append("state", "RECEIVED").append("work", new Document("$exists", false)))
                .sort(new Document("receivedAt", 1).append("_id", 1)).limit(limit).into(new ArrayList<>());
        List<Document> running = rows().find(env().append("work.nextAttemptAt", new Document("$type", "date").append("$lte", now))
                .append("$or", List.of(new Document("work.owner", null), new Document("work.until", new Document("$lte", now)))))
                .sort(new Document("work.nextAttemptAt", 1).append("_id", 1)).limit(limit).into(new ArrayList<>());
        List<String> ids = new ArrayList<>();
        for (int i = 0; ids.size() < limit && (i < fresh.size() || i < running.size()); i++) {
            if (i < running.size()) ids.add(running.get(i).getString("_id"));
            if (ids.size() < limit && i < fresh.size()) ids.add(fresh.get(i).getString("_id"));
        }
        return ids;
    }
    public Lease claim(String id) {
        var ticket = fence.capture(); String owner = UUID.randomUUID().toString();
        return store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            Document row = rows().find(s, env().append("_id", id)).first(); if (row == null) return null;
            require(Set.of("RECEIVED", "APPLIED", "QUARANTINED").contains(row.getString("state")));
            Document old = row.get("work", Document.class); Instant now = clock.instant();
            if (old == null && !"RECEIVED".equals(row.getString("state"))) return null;
            if (old != null && (old.getDate("nextAttemptAt") == null || old.getDate("nextAttemptAt").toInstant().isAfter(now)
                    || old.getString("owner") != null && old.getDate("until").toInstant().isAfter(now))) return null;
            long version = old == null ? 1 : increment(old, "leaseVersion"), generation = old == null ? 1 : old.getLong("generation");
            long attempts = old == null ? 1 : increment(old, "attempts");
            Date started = old == null ? Date.from(now) : old.getDate("retryStartedAt");
            Document work = new Document("owner", owner).append("until", Date.from(now.plusSeconds(30)))
                    .append("leaseVersion", version).append("generation", generation).append("fenceVersion", 0L)
                    .append("nextAttemptAt", Date.from(now)).append("retryStartedAt", started).append("attempts", attempts).append("failure", null);
            Document match = env().append("_id", id).append("version", row.getLong("version"));
            match.append("work", old == null ? new Document("$exists", false) : old);
            require(rows().updateOne(s, match, new Document("$set", new Document("work", work))).getModifiedCount() == 1);
            return new Lease(id, row.getString("providerEventKey"), owner, version, generation, ticket, started.toInstant(), attempts);
        });
    }
    /** This write conflicts with a concurrent reclaim; reading the lease alone would be insufficient. */
    public void guard(ClientSession s, Lease lease) {
        require(s.hasActiveTransaction());
        require(rows().updateOne(s, owned(lease).append("work.fenceVersion", new Document("$lt", Long.MAX_VALUE)),
                new Document("$inc", new Document("work.fenceVersion", 1L))).getModifiedCount() == 1);
    }
    public void renew(Lease lease) {
        store.transaction(s -> { fence.acquire(s, lease.ticket(), PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            guard(s, lease); require(rows().updateOne(s, owned(lease), new Document("$set", new Document("work.until", Date.from(clock.instant().plusSeconds(30))))).getMatchedCount() == 1); return null; });
    }
    public void finish(Lease lease, Instant next, String failure) {
        store.transaction(s -> { fence.acquire(s, lease.ticket(), PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard(s, lease);
            require(rows().updateOne(s, owned(lease), new Document("$set", new Document("work.owner", null).append("work.until", null)
                    .append("work.nextAttemptAt", next == null ? null : Date.from(next)).append("work.failure", failure))).getModifiedCount() == 1); return null; });
    }
    public Document read(Lease lease) { Document row = rows().find(env().append("_id", lease.id())).first(); require(row != null); return row; }
    private Document owned(Lease l) { return env().append("_id", l.id()).append("work.owner", l.owner()).append("work.leaseVersion", l.version())
            .append("work.generation", l.generation()).append("work.until", new Document("$gt", Date.from(clock.instant()))); }
    private Document env() { return new Document("environment", environment); }
    private MongoCollection<Document> rows() { return store.database().getCollection(COLLECTION); }
    private static long increment(Document d, String field) { long n = d.getLong(field); require(n > 0 && n < Long.MAX_VALUE); return n + 1; }
}
