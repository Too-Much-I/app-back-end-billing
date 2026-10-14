package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.provider.PurchaseReferenceCipher;
import web.tosunsaeng.billing.domain.payment.provider.RevenueCatFailure;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseLookupKey;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** One lease per account, shared by all pending sync commands. No duplicate financial state. */
public final class PaymentAccountLookupWork {
    public record Lease(String id, String accountId, String reference, String owner, long version, long generation,
                        long attempts, long pages, String cursor, String failure, PaymentRecoveryFence.Ticket ticket,
                        boolean periodic, Instant scanStartedAt) {
        @Override public String toString() { return "PaymentAccountLookupLease[REDACTED]"; }
    }
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final Clock clock;
    private final String environment, project;
    private final PurchaseLookupKey lookup;
    private final PurchaseReferenceCipher cipher;
    public PaymentAccountLookupWork(PaymentStore store, PaymentProperties p, Clock clock, String project,
                                    PurchaseLookupKey lookup, PurchaseReferenceCipher cipher) {
        require(p.getRecovery().isFenceRequired());
        this.store = store; this.fence = new PaymentRecoveryFence(store, p); this.clock = clock; this.environment = p.getEnvironment();
        this.project = project; this.lookup = lookup; this.cipher = cipher; PaymentAccountLookupSchema.verify(store.database());
    }
    public List<String> due(int limit) {
        require(limit >= 1 && limit <= 100); Date now = Date.from(clock.instant());
        Document due = new Document("kind", "ACCOUNT_LOOKUP").append("nextAttemptAt", new Document("$type", "date").append("$lte", now))
                .append("$or", List.of(new Document("lookup.owner", null), new Document("lookup.until", new Document("$lte", now))));
        // Start from pending commands, not every historical account. Empty queues do not scan idle cursors.
        return commands().aggregate(List.of(new Document("$match", new Document("environment", environment).append("state", "PENDING")),
                new Document("$group", new Document("_id", "$accountId")),
                new Document("$lookup", new Document("from", PaymentAccountLookupSchema.COLLECTION).append("localField", "_id").append("foreignField", "accountId")
                        .append("pipeline", List.of(new Document("$match", due))).append("as", "cursor")),
                new Document("$unwind", "$cursor"), new Document("$sort", new Document("cursor.nextAttemptAt", 1).append("cursor._id", 1)),
                new Document("$limit", limit), new Document("$project", new Document("_id", "$cursor._id"))))
                .into(new ArrayList<>()).stream().map(d -> d.getString("_id")).toList();
    }
    public Lease claim(String id) {
        return claim(id, false);
    }
    /** Recent activity/purchases and all active/scheduled rights, independently of app sync. */
    public List<String> duePeriodic(int limit) {
        require(limit >= 1 && limit <= 100); Date now = Date.from(clock.instant()), recent = Date.from(clock.instant().minus(Duration.ofDays(90)));
        Document due = new Document("kind", "ACCOUNT_LOOKUP").append("nextAttemptAt", new Document("$type", "date").append("$lte", now))
                .append("$and", List.of(new Document("$or", List.of(new Document("lookup.owner", null), new Document("lookup.until", new Document("$lte", now)))),
                        new Document("$or", List.of(new Document("reconcileAt", null), new Document("reconcileAt", new Document("$lte", now))))));
        return rows().aggregate(List.of(new Document("$match", due),
                join("payment_accounts", "accountId", "_id", new Document("environment", environment), "account"),
                new Document("$match", new Document("account.0", new Document("$exists", true))),
                join("subscription_entitlements", "accountId", "accountId", new Document("endsAt", new Document("$gt", now)).append("revokedAt", null), "active"),
                join("purchases", "accountId", "accountId", new Document("environment", environment).append("lastVerifiedAt", new Document("$gte", recent)), "recent"),
                new Document("$match", new Document("$or", List.of(new Document("lastActivityAt", new Document("$gte", recent)),
                        new Document("active.0", new Document("$exists", true)), new Document("recent.0", new Document("$exists", true))))),
                new Document("$sort", new Document("reconcileAt", 1).append("lastActivityAt", -1).append("_id", 1)), new Document("$limit", limit),
                new Document("$project", new Document("_id", 1)))).into(new ArrayList<>()).stream().map(d -> d.getString("_id")).toList();
    }
    private Document join(String collection, String local, String foreign, Document match, String as) {
        return new Document("$lookup", new Document("from", collection).append("localField", local).append("foreignField", foreign)
                .append("pipeline", List.of(new Document("$match", match), new Document("$limit", 1))).append("as", as));
    }
    public Lease claim(String id, boolean periodic) {
        var ticket = fence.capture(); String owner = UUID.randomUUID().toString();
        return store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            Document row = rows().find(s, new Document("_id", id)).first(); if (row == null) return null;
            Instant now = clock.instant(); Document old = row.get("lookup", Document.class);
            if (row.getDate("nextAttemptAt") == null || row.getDate("nextAttemptAt").toInstant().isAfter(now)
                    || old != null && old.getString("owner") != null && old.getDate("until").toInstant().isAfter(now)) return null;
            String account = row.getString("accountId");
            if (!periodic && commands().find(s, pending(account)).first() == null) return null;
            if (periodic && row.getDate("reconcileAt") != null && row.getDate("reconcileAt").toInstant().isAfter(now)) return null;
            Document a = store.database().getCollection("payment_accounts").find(s, new Document("_id", account).append("environment", environment)).first(); require(a != null);
            String ref = old == null || old.getLong("pages") == 0 ? a.getString("activeRefId") : old.getString("reference");
            require(Objects.equals(ref, a.getString("activeRefId")));
            long version = old == null ? 1 : increment(old, "leaseVersion"), generation = old == null ? 1 : old.getLong("generation");
            String oldFailure = old == null ? null : old.getString("failure");
            long attempts = old == null || "IDLE".equals(oldFailure) || oldFailure != null && oldFailure.startsWith("RESUMED_")
                    ? 1 : increment(old, "attempts"), pages = old == null ? 0 : old.getLong("pages");
            Document cursor = old == null ? null : old.get("cursor", Document.class);
            String decoded = null, failure = old == null ? null : old.getString("failure");
            try { decoded = decode(account, cursor); }
            catch (RevenueCatFailure corruptOrUnavailableKey) { failure = "PROVIDER_CONTRACT"; }
            Document work = new Document("owner", owner).append("until", Date.from(now.plusSeconds(30)))
                    .append("leaseVersion", version).append("generation", generation).append("attempts", attempts).append("fenceVersion", 0L)
                    .append("reference", ref).append("pages", pages).append("cursor", cursor).append("failure", failure);
            long rowVersion = row.getLong("version"); require(rowVersion > 0 && rowVersion < Long.MAX_VALUE);
            Instant started = row.getDate("reconcileStartedAt") == null ? now : row.getDate("reconcileStartedAt").toInstant();
            Document changes = new Document("lookup", work);
            if (periodic) changes.append("reconcileStartedAt", Date.from(started));
            require(rows().updateOne(s, new Document("_id", id).append("version", rowVersion), new Document("$set", changes)
                    .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            var lease = new Lease(id, account, ref, owner, version, generation, attempts, pages, decoded, failure, ticket, periodic, started);
            guard(s, lease); return lease;
        });
    }
    public void guard(ClientSession s, Lease l) {
        require(s.hasActiveTransaction());
        require(store.database().getCollection("payment_accounts").find(s, new Document("_id", l.accountId()).append("environment", environment)
                .append("activeRefId", l.reference())).first() != null);
        require(store.database().getCollection("purchase_account_refs").find(s, new Document("_id", l.reference()).append("accountId", l.accountId())
                .append("environment", environment)).first() != null);
        require(rows().updateOne(s, owned(l).append("lookup.fenceVersion", new Document("$lt", Long.MAX_VALUE)).append("version", new Document("$lt", Long.MAX_VALUE)),
                new Document("$inc", new Document("lookup.fenceVersion", 1L).append("version", 1L))).getModifiedCount() == 1);
    }
    public void renew(Lease l) {
        store.transaction(s -> { fence.acquire(s, l.ticket(), PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard(s, l);
            require(rows().updateOne(s, owned(l), new Document("$set", new Document("lookup.until", Date.from(clock.instant().plusSeconds(30))))).getMatchedCount() == 1); return null; });
    }
    /** Checkpoint only after all page observations are durable. Null nextCursor ends this traversal. */
    public void checkpoint(Lease l, String nextCursor, Instant nextAttemptAt) {
        require(nextCursor == null || !nextCursor.equals(l.cursor()));
        Document encrypted = encode(l.accountId(), nextCursor);
        finish(l, nextAttemptAt, null, new Document("lookup.cursor", encrypted).append("lookup.pages", nextCursor == null ? 0L : l.pages() + 1));
    }
    public void defer(Lease l, Instant nextAttemptAt, String reason) { finish(l, nextAttemptAt, reason, new Document()); }
    public void markHandoff(Lease l, String reason) {
        store.transaction(s -> { fence.acquire(s, l.ticket(), PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard(s, l);
            require(rows().updateOne(s, owned(l), new Document("$set", new Document("lookup.failure", reason))).getMatchedCount() == 1); return null; });
    }
    private void finish(Lease l, Instant next, String reason, Document extra) {
        store.transaction(s -> { fence.acquire(s, l.ticket(), PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard(s, l);
            String previous = rows().find(s, owned(l)).first().get("lookup", Document.class).getString("failure");
            boolean handoff = previous != null && Set.of("BLOCKED_AUTH", "PROVIDER_REVIEW", "PROVIDER_CONTRACT", "PAGE_LIMIT").contains(previous);
            extra.append("nextAttemptAt", next == null ? null : Date.from(next)).append("lookup.owner", null).append("lookup.until", null).append("lookup.failure", handoff ? previous : reason);
            boolean hasPending = commands().find(s, pending(l.accountId())).limit(1).first() != null;
            if (l.periodic() && reason == null && !handoff) {
                boolean finished = extra.get("lookup.cursor") == null;
                long delay = hasPending ? 300 : store.database().getCollection("subscription_entitlements")
                        .find(s, new Document("accountId", l.accountId()).append("endsAt", new Document("$gt", Date.from(clock.instant()))).append("revokedAt", null)).limit(1).first() != null ? 21600 : 86400;
                extra.append("reconcileAt", Date.from(clock.instant().plusSeconds(finished ? delay : 0)))
                        .append("reconcileStartedAt", finished ? null : Date.from(l.scanStartedAt()));
            }
            if (reason == null && !handoff && !hasPending && (!l.periodic() || extra.get("lookup.cursor") == null))
                extra.append("nextAttemptAt", Date.from(clock.instant())).append("lookup.cursor", null).append("lookup.pages", 0L)
                        .append("lookup.attempts", 1L).append("lookup.failure", "IDLE").append("reconcileStartedAt", null);
            require(rows().updateOne(s, owned(l), new Document("$set", extra)).getModifiedCount() == 1); return null; });
    }
    public Document pending(String account) { return new Document("environment", environment).append("accountId", account).append("state", "PENDING"); }
    public List<Document> commands(Lease l) { return commands().find(pending(l.accountId())).sort(new Document("createdAt", 1).append("_id", 1)).limit(100).into(new ArrayList<>()); }
    public boolean hasPending(Lease l) { return commands().find(pending(l.accountId())).limit(1).first() != null; }
    public boolean hasUnbound(Lease l) { return commands().find(pending(l.accountId()).append("sourceObservationKey", null)).limit(1).first() != null; }
    public boolean hasExpired(Lease l) {
        Date cutoff = Date.from(clock.instant().minus(Duration.ofHours(24)));
        return commands().find(pending(l.accountId()).append("$or", List.of(new Document("retryStartedAt", new Document("$lte", cutoff)),
                new Document("retryStartedAt", new Document("$exists", false)).append("createdAt", new Document("$lte", cutoff))))).limit(1).first() != null;
    }
    private Document owned(Lease l) { return new Document("_id", l.id()).append("accountId", l.accountId()).append("lookup.owner", l.owner())
            .append("lookup.leaseVersion", l.version()).append("lookup.generation", l.generation()).append("lookup.until", new Document("$gt", Date.from(clock.instant()))); }
    private PurchaseReferenceCipher.Context context(String account) { return new PurchaseReferenceCipher.Context(environment, project, "account-page", lookup.transaction(environment, "ACCOUNT_LOOKUP", project, account)); }
    private Document encode(String account, String value) {
        if (value == null) return null; var e = cipher.encrypt(context(account), value);
        return new Document("revision", (long) e.revision()).append("keyId", e.keyId()).append("nonce", e.nonce()).append("ciphertext", e.ciphertext());
    }
    private String decode(String account, Document e) {
        if (e == null) return null;
        return cipher.decrypt(context(account), new PurchaseReferenceCipher.Envelope(Math.toIntExact(e.getLong("revision")), e.getString("keyId"), e.getString("nonce"), e.getString("ciphertext")));
    }
    private MongoCollection<Document> rows() { return store.database().getCollection(PaymentAccountLookupSchema.COLLECTION); }
    private MongoCollection<Document> commands() { return store.database().getCollection(PaymentSyncSchema.COLLECTION); }
    private static long increment(Document d, String field) { long n = d.getLong(field); require(n > 0 && n < Long.MAX_VALUE); return n + 1; }
}
