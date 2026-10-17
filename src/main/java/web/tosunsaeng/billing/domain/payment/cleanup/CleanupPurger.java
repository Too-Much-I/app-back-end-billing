package web.tosunsaeng.billing.domain.payment.cleanup;

import com.mongodb.client.ClientSession;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Conditional runtime capability. No HTTP entry point; default OFF. */
final class CleanupPurger {
    enum Result { PURGED, ALREADY_ABSENT, NOT_READY, RECHECK, BLOCKED }
    /** Local, bounded policy check only: no network in a Mongo transaction. Unknown must return false. */
    @FunctionalInterface interface FinalPolicy {
        boolean valid(Subject subject, Coverage recorded, Instant checkedAt, Instant now);
    }
    record Lease(String jobId, long version, String token, Subject subject) {
        @Override public String toString() { return "PurgeLease[REDACTED]"; }
    }
    private record Target(String collection, Document row) {}
    private static final class UnsafeTargets extends RuntimeException {
        final Reason reason;
        UnsafeTargets(Reason reason) { super("Cleanup target unavailable"); this.reason = reason; }
    }
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    private final LocalReader local;
    private final FinalPolicy policy;
    private final PaymentRecoveryFence fence;
    private final CleanupProcessor reader;

    CleanupPurger(PaymentStore store, PaymentProperties properties, Clock clock, LocalReader local, FinalPolicy policy) {
        this.store = store; this.properties = properties; this.clock = clock;
        this.local = Objects.requireNonNull(local); this.policy = Objects.requireNonNull(policy);
        fence = new PaymentRecoveryFence(store, properties);
        reader = new CleanupProcessor(store, properties, clock, local, unavailableProvider(), unavailableCoverage());
    }
    Result tryPurge(String id) {
        Lease lease = claim(id);
        return lease == null ? Result.NOT_READY : purge(lease);
    }
    Lease claim(String id) {
        require(properties.getRecovery().isFenceRequired());
        var ticket = fence.capture();
        String token = UUID.randomUUID().toString();
        return store.transaction(session -> {
            Document job = jobs().find(session, key(id)).first();
            if (job == null || !"READY".equals(job.getString("state"))) return null;
            acquire(session, ticket);
            long version = version(job);
            Instant now = clock.instant();
            if (now.isBefore(job.getDate("eligibleAt").toInstant()) || now.isBefore(job.getDate("nextAttemptAt").toInstant())) return null;
            Document cleanup = job.get("cleanup", Document.class);
            Document oldLease = cleanup.get("lease", Document.class);
            if (ticket.epoch().equals(cleanup.getString("epoch")) && oldLease != null
                    && now.isBefore(oldLease.getDate("until").toInstant())) return null;
            Subject subject = reader.subject(session, job, ticket.epoch());
            if (!validProof(cleanup, subject, now)) {
                recheck(session, job); return null;
            }
            require(jobs().updateOne(session, key(id).append("state", "READY").append("version", version),
                    new Document("$set", new Document("cleanup.lease", new Document("token", token)
                            .append("until", Date.from(now.plusSeconds(30))))
                            .append("nextAttemptAt", Date.from(now.plusSeconds(30))))
                            .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            return new Lease(id, version + 1, token, subject);
        });
    }
    Result purge(Lease lease) {
        return store.transaction(session -> {
            acquire(session, new PaymentRecoveryFence.Ticket(lease.subject().epoch()));
            Document job = jobs().find(session, key(lease.jobId())).first();
            if (job == null) {
                // No receipt: absence is NOT an exact successful deletion acknowledgement.
                require(absent(session, lease));
                return Result.ALREADY_ABSENT;
            }
            Document cleanup = owned(job, lease);
            Subject current = reader.subject(session, job, lease.subject().epoch());
            if (!current.equals(lease.subject()) || !validProof(cleanup, current, clock.instant())) {
                recheck(session, job); return Result.RECHECK;
            }
            Local evidence = local.read(session, current);
            if (evidence != Local.NONE || current.pendingEvidence() != 0) {
                block(session, job, evidence == Local.FOUND ? Reason.PURCHASE_FOUND
                                : evidence == Local.UNKNOWN ? Reason.LOCAL_EVIDENCE_UNAVAILABLE : Reason.PENDING_EVIDENCE,
                        evidence == Local.FOUND); return Result.BLOCKED;
            }
            List<Target> targets;
            try { targets = targets(session, job, current); }
            catch (UnsafeTargets unsafe) { block(session, job, unsafe.reason, false); return Result.BLOCKED; }
            if (targets.size() > 100) {
                block(session, job, Reason.DOCUMENT_LIMIT, false); return Result.BLOCKED;
            }
            var lives = store.database().getCollection("payment_account_lifecycles");
            require(lives.updateOne(session, new Document("_id", current.lifecycleId()).append("environment", current.environment())
                    .append("userId", current.userId()).append("state", "WITHDRAWN").append("version", current.lifecycleVersion())
                    .append("pendingEvidence", 0L), new Document("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            // Exact row matches also detect an uncoordinated update to a selected ref/cursor.
            for (Target target : targets) {
                Document exact = new Document(target.row());
                if (target.collection().equals("payment_account_lifecycles")) exact.put("version", current.lifecycleVersion() + 1);
                require(store.database().getCollection(target.collection()).deleteOne(session, exact).getDeletedCount() == 1);
            }
            // If the bounded DB work used up the lease/proof, abort ALL deletes, never partially commit.
            owned(job, lease);
            require(validProof(cleanup, current, clock.instant()));
            return Result.PURGED;
        });
    }
    private List<Target> targets(ClientSession session, Document job, Subject subject) {
        List<Target> result = new ArrayList<>();
        if (subject.accountId() != null) {
            var refs = rows(session, "purchase_account_refs", new Document("accountId", subject.accountId()), 101);
            targetCheck(refs.size() == subject.references().size(), Reason.DATA_INCONSISTENT);
            for (var ref : refs) {
                targetCheck(subject.references().contains(ref.getString("_id")) && subject.environment().equals(ref.getString("environment"))
                        && Boolean.FALSE.equals(ref.get("active")), Reason.DATA_INCONSISTENT);
                result.add(new Target("purchase_account_refs", ref));
            }
            var cursors = rows(session, "payment_reconcile_cursors", new Document("accountId", subject.accountId()), 101);
            targetCheck(cursors.size() == 1, Reason.DATA_INCONSISTENT);
            Document cursor = cursors.getFirst();
            targetCheck("ACCOUNT_LOOKUP".equals(cursor.getString("kind"))
                    && subject.cursorActivityAt().equals(cursor.getDate("lastActivityAt").toInstant()), Reason.DATA_INCONSISTENT);
            targetCheck(!cursor.containsKey("lease") && !cursor.containsKey("leaseOwner") && !cursor.containsKey("leaseUntil"), Reason.PENDING_EVIDENCE);
            Document lookup = cursor.get("lookup", Document.class);
            targetCheck(lookup == null || lookup.getString("owner") == null, Reason.PENDING_EVIDENCE);
            version(cursor);
            result.add(new Target("payment_reconcile_cursors", cursor));
            Document account = single(session, "payment_accounts", new Document("_id", subject.accountId()));
            require(subject.environment().equals(account.getString("environment")) && subject.userId().equals(account.getString("userId")));
            result.add(new Target("payment_accounts", account));
        }
        result.add(new Target(CleanupSchema.COLLECTION, job));
        result.add(new Target("payment_account_lifecycles", single(session, "payment_account_lifecycles", new Document("_id", subject.lifecycleId()))));
        return result;
    }
    private boolean validProof(Document cleanup, Subject subject, Instant now) {
        Document proof = cleanup.get("proof", Document.class);
        if (proof == null || !subject.epoch().equals(cleanup.getString("epoch")) || !subject.epoch().equals(proof.getString("epoch"))) return false;
        Instant checked = proof.getDate("checkedAt").toInstant(), until = proof.getDate("validUntil").toInstant();
        if (checked.isAfter(now) || !now.isBefore(until) || until.isAfter(checked.plusSeconds(3600))
                || now.isBefore(subject.eligibleAt()) || subject.pendingEvidence() != 0
                || !"NO_PURCHASE_CONFIRMED".equals(proof.getString("result"))
                || !Objects.equals(proof.get("lifecycleVersion"), subject.lifecycleVersion())
                || !Objects.equals(proof.get("accountVersion"), subject.accountVersion())
                || !Objects.equals(proof.get("cursorActivityAt"), subject.cursorActivityAt() == null ? null : Date.from(subject.cursorActivityAt()))) return false;
        return policy.valid(subject, new Coverage(proof.getString("adapterVersion"), proof.getString("coverageVersion"),
                proof.getString("drainPolicyVersion"), until), checked, now);
    }
    private Document owned(Document job, Lease lease) {
        require(version(job) == lease.version() && "READY".equals(job.getString("state")));
        Document cleanup = job.get("cleanup", Document.class), lock = cleanup.get("lease", Document.class);
        require(lease.subject().epoch().equals(cleanup.getString("epoch")) && lock != null
                && lease.token().equals(lock.getString("token")) && clock.instant().isBefore(lock.getDate("until").toInstant()));
        return cleanup;
    }
    private void recheck(ClientSession session, Document job) {
        require(jobs().updateOne(session, key(job.getString("_id")).append("version", version(job)),
                new Document("$set", new Document("state", "WAITING").append("nextAttemptAt", Date.from(clock.instant())))
                        .append("$unset", new Document("cleanup.proof", "").append("cleanup.lease", ""))
                        .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
    }
    private void block(ClientSession session, Document job, Reason reason, boolean financial) {
        Document cleanup = job.get("cleanup", Document.class);
        cleanup.remove("lease"); cleanup.remove("proof"); cleanup.put("reason", reason.name());
        Date now = Date.from(clock.instant()); cleanup.put("lastCheckedAt", now);
        if (!financial && !cleanup.containsKey("firstUnresolvedAt")) {
            cleanup.put("firstUnresolvedAt", now); cleanup.put("reviewDueAt", Date.from(clock.instant().plus(Duration.ofDays(7))));
        }
        Document set = new Document("state", financial ? "FINANCIAL_RETAINED" : "BLOCKED").append("cleanup", cleanup);
        Document update = new Document("$set", set).append("$inc", new Document("version", 1L));
        if (financial) update.append("$unset", new Document("nextAttemptAt", ""));
        else set.put("nextAttemptAt", Date.from(clock.instant().plus(Duration.ofDays(1))));
        require(jobs().updateOne(session, key(job.getString("_id")).append("version", version(job)), update).getModifiedCount() == 1);
    }
    private boolean absent(ClientSession session, Lease lease) {
        Subject s = lease.subject();
        if (!rows(session, "payment_account_lifecycles", subjectKey(s), 1).isEmpty()
                || !rows(session, "payment_accounts", subjectKey(s), 1).isEmpty()
                || !rows(session, CleanupSchema.COLLECTION, subjectKey(s), 1).isEmpty()) return false;
        if (s.accountId() == null) return true;
        return rows(session, "purchase_account_refs", new Document("accountId", s.accountId()), 1).isEmpty()
                && rows(session, "payment_reconcile_cursors", new Document("accountId", s.accountId()), 1).isEmpty();
    }
    private Document subjectKey(Subject s) { return new Document("environment", s.environment()).append("userId", s.userId()); }
    private static void targetCheck(boolean safe, Reason reason) { if (!safe) throw new UnsafeTargets(reason); }
    private void acquire(ClientSession session, PaymentRecoveryFence.Ticket ticket) {
        fence.acquire(session, ticket, PaymentRecoveryFence.Writer.PURGE);
        Document control = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(session,
                new Document("environment", properties.getEnvironment()).append("consumer", "billing")).first();
        require(Long.valueOf(4).equals(control.get("schemaVersion")));
    }
    private List<Document> rows(ClientSession session, String collection, Document query, int limit) {
        return store.database().getCollection(collection).find(session, query).limit(limit).into(new ArrayList<>());
    }
    private Document single(ClientSession session, String collection, Document query) {
        var rows = rows(session, collection, query, 2); require(rows.size() == 1); return rows.getFirst();
    }
    private static long version(Document row) {
        require(row.get("version") instanceof Long && row.getLong("version") > 0 && row.getLong("version") < Long.MAX_VALUE);
        return row.getLong("version");
    }
    private Document key(String id) { return new Document("_id", id).append("environment", properties.getEnvironment()); }
    private com.mongodb.client.MongoCollection<Document> jobs() { return store.database().getCollection(CleanupSchema.COLLECTION); }
}
