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

/** Evidence/job processor. Deletion is supplied only when the separate purge flag is enabled. */
public final class CleanupProcessor {
    public record Due(String id, Instant at) {
        @Override public String toString() { return "CleanupDue[REDACTED]"; }
    }
    public record Lease(String jobId, long version, String token, Subject subject, Local local) {
        @Override public String toString() { return "CleanupLease[REDACTED]"; }
    }
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    private final PaymentRecoveryFence fence;
    private final LocalReader local;
    private final ProviderReader provider;
    private final CoveragePolicy coverage;
    private final CleanupPurger purger;
    record Processing(State state, CleanupPurger.Result purge) {}
    private final CleanupEligibilityEvaluator evaluator = new CleanupEligibilityEvaluator();
    public CleanupProcessor(PaymentStore store, PaymentProperties properties, Clock clock,
                            LocalReader local, ProviderReader provider, CoveragePolicy coverage) {
        this(store, properties, clock, local, provider, coverage, null);
    }
    // Reconciliation alone never deletes; the purge capability is explicit.
    CleanupProcessor(PaymentStore store, PaymentProperties properties, Clock clock,
                     LocalReader local, ProviderReader provider, CoveragePolicy coverage, CleanupPurger purger) {
        this.store = store; this.properties = properties; this.clock = clock;
        this.fence = new PaymentRecoveryFence(store, properties);
        this.local = Objects.requireNonNull(local); this.provider = Objects.requireNonNull(provider);
        this.coverage = Objects.requireNonNull(coverage);
        this.purger = purger;
    }
    boolean canPurge() { return purger != null; }
    public List<Due> due(Due after, int limit) {
        return due(after, limit, false);
    }
    public List<Due> recoverable(Due after, int limit) { return due(after, limit, true); }
    private List<Due> due(Due after, int limit, boolean recoveryOnly) {
        require(limit > 0 && limit <= 100 && properties.getRecovery().isFenceRequired());
        var ticket = fence.capture();
        Date now = Date.from(clock.instant());
        List<Document> clauses = new ArrayList<>();
        clauses.add(new Document("environment", properties.getEnvironment()).append("state", new Document("$in",
                recoveryOnly ? (canPurge() ? List.of("VERIFYING", "READY") : List.of("VERIFYING")) : List.of("WAITING", "BLOCKED", "VERIFYING", "READY"))).append("eligibleAt", new Document("$lte", now))
                .append("nextAttemptAt", new Document("$lte", now)));
        clauses.add(new Document("$or", List.of(new Document("state", new Document("$ne", "VERIFYING")),
                new Document("cleanup.lease.until", new Document("$lte", now)),
                new Document("cleanup.epoch", new Document("$ne", ticket.epoch())))));
        if (after != null) clauses.add(new Document("$or", List.of(
                new Document("nextAttemptAt", new Document("$gt", Date.from(after.at()))),
                new Document("nextAttemptAt", Date.from(after.at())).append("_id", new Document("$gt", after.id())))));
        return jobs().find(new Document("$and", clauses)).sort(new Document("nextAttemptAt", 1).append("_id", 1))
                .limit(limit).map(d -> new Due(d.getString("_id"), d.getDate("nextAttemptAt").toInstant())).into(new ArrayList<>());
    }
    public Lease claim(String id) {
        require(properties.getRecovery().isFenceRequired());
        var ticket = fence.capture();
        String token = UUID.randomUUID().toString();
        return store.transaction(session -> {
            acquire(session, ticket);
            var job = jobs().find(session, key(id)).first();
            if (job == null || "FINANCIAL_RETAINED".equals(job.getString("state"))) return null;
            Instant now = clock.instant();
            require(job.get("eligibleAt") instanceof Date && job.get("nextAttemptAt") instanceof Date);
            if (now.isBefore(job.getDate("eligibleAt").toInstant()) || now.isBefore(job.getDate("nextAttemptAt").toInstant())) return null;
            long version = version(job);
            Document cleanup = cleanup(job, ticket.epoch());
            Document oldLease = cleanup.get("lease", Document.class);
            if (oldLease != null && ticket.epoch().equals(cleanup.getString("epoch"))
                    && now.isBefore(oldLease.getDate("until").toInstant())) return null;
            Subject subject = subject(session, job, ticket.epoch());
            Local evidence = local.read(session, subject);
            require(evidence != null);
            cleanup.put("epoch", ticket.epoch()); cleanup.remove("proof");
            cleanup.put("lease", new Document("token", token).append("until", Date.from(now.plusSeconds(30))));
            require(jobs().updateOne(session, key(id).append("version", version),
                    new Document("$set", new Document("state", "VERIFYING").append("cleanup", cleanup)
                            .append("nextAttemptAt", Date.from(now.plusSeconds(30))))
                            .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            return new Lease(id, version + 1, token, subject, evidence);
        });
    }
    public Lease renew(Lease lease) {
        return store.transaction(session -> {
            var job = owned(session, lease);
            var cleanup = job.get("cleanup", Document.class);
            Date until = Date.from(clock.instant().plusSeconds(30));
            cleanup.get("lease", Document.class).put("until", until);
            require(jobs().updateOne(session, key(lease.jobId()).append("version", lease.version()),
                    new Document("$set", new Document("cleanup", cleanup).append("nextAttemptAt", until))
                            .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            return new Lease(lease.jobId(), lease.version() + 1, lease.token(), lease.subject(), lease.local());
        });
    }
    /** Provider work is outside Mongo transactions. Adapter must honor its deadline; late results are rejected. */
    public State process(String id) {
        return processResult(id).state();
    }
    Processing processResult(String id) {
        if (purger != null) {
            var result = purger.tryPurge(id);
            if (result != CleanupPurger.Result.NOT_READY) return new Processing(null, result);
        }
        Lease lease = claim(id);
        if (lease == null) return new Processing(null, null);
        Instant start = clock.instant(), deadline = start.plusSeconds(5);
        long started = System.nanoTime();
        Provider observation = Provider.unknown(); Coverage proof = null;
        if (lease.local() == Local.NONE && lease.subject().pendingEvidence() == 0) {
            try {
                observation = Objects.requireNonNull(provider.read(lease.subject(), deadline));
                proof = coverage.confirm(lease.subject(), observation, clock.instant());
            } catch (RuntimeException unavailable) {
                observation = Provider.unknown(); // Never retain exception messages/provider payload.
            }
        }
        if (!clock.instant().isBefore(deadline) || System.nanoTime() - started >= 5_000_000_000L) {
            observation = Provider.unknown(); proof = null;
        }
        Decision decision = evaluator.evaluate(lease.subject(), lease.local(), observation, proof, clock.instant());
        State state = complete(lease, decision);
        var result = state == State.READY && purger != null ? purger.tryPurge(id) : null;
        return new Processing(state, result);
    }
    public State complete(Lease lease, Decision decision) {
        require(decision != null && Set.of(State.BLOCKED, State.FINANCIAL_RETAINED, State.READY).contains(decision.state()));
        return store.transaction(session -> {
            var job = owned(session, lease);
            Instant now = clock.instant();
            Document cleanup = job.get("cleanup", Document.class);
            Decision actual = decision;
            Subject current = subject(session, job, lease.subject().epoch());
            if (!current.equals(lease.subject())) actual = Decision.blocked(Reason.VERSION_CHANGED);
            if (actual.state() == State.READY) {
                var control = store.database().getCollection(PaymentRecoveryFence.COLLECTION)
                        .find(session, new Document("environment", properties.getEnvironment()).append("consumer", "billing")).first();
                if (!"OPEN".equals(control.getString("barrier"))) actual = Decision.blocked(Reason.RECOVERY_BLOCKED);
                else if (actual.coverage() == null || !now.isBefore(actual.coverage().validUntil()))
                    actual = Decision.blocked(Reason.PROOF_EXPIRED);
            }
            cleanup.remove("lease"); cleanup.remove("proof"); cleanup.remove("reason");
            cleanup.put("lastCheckedAt", Date.from(now));
            if (actual.reason() != null) cleanup.put("reason", actual.reason().name());
            var set = new Document("state", actual.state().name()).append("cleanup", cleanup);
            var update = new Document("$set", set).append("$inc", new Document("version", 1L));
            if (actual.state() == State.BLOCKED) {
                require(actual.reason() != null);
                if (!cleanup.containsKey("firstUnresolvedAt")) {
                    cleanup.put("firstUnresolvedAt", Date.from(now));
                    cleanup.put("reviewDueAt", Date.from(now.plus(Duration.ofDays(7))));
                }
                set.put("nextAttemptAt", Date.from(now.plus(Duration.ofDays(1))));
            } else if (actual.state() == State.FINANCIAL_RETAINED) {
                update.put("$unset", new Document("nextAttemptAt", ""));
            } else {
                Coverage evidence = actual.coverage();
                Instant until = evidence.validUntil().isBefore(now.plusSeconds(3600)) ? evidence.validUntil() : now.plusSeconds(3600);
                cleanup.put("proof", new Document("checkedAt", Date.from(now)).append("validUntil", Date.from(until))
                        .append("epoch", current.epoch()).append("lifecycleVersion", current.lifecycleVersion())
                        .append("accountVersion", current.accountVersion()).append("cursorActivityAt", date(current.cursorActivityAt()))
                        .append("adapterVersion", evidence.adapterVersion()).append("coverageVersion", evidence.coverageVersion())
                        .append("drainPolicyVersion", evidence.drainPolicyVersion()).append("result", "NO_PURCHASE_CONFIRMED"));
                // PR1 cannot purge. Avoid an immediate READY busy loop.
                set.put("nextAttemptAt", Date.from(purger == null ? now.plusSeconds(3600) : now));
            }
            require(jobs().updateOne(session, key(lease.jobId()).append("version", lease.version()), update).getModifiedCount() == 1);
            return actual.state();
        });
    }
    private Document owned(ClientSession session, Lease lease) {
        require(properties.getRecovery().isFenceRequired());
        acquire(session, new PaymentRecoveryFence.Ticket(lease.subject().epoch()));
        var job = jobs().find(session, key(lease.jobId())).first();
        require(job != null && version(job) == lease.version() && "VERIFYING".equals(job.getString("state")));
        Document cleanup = cleanup(job, lease.subject().epoch());
        require(lease.subject().epoch().equals(cleanup.getString("epoch")));
        var currentLease = cleanup.get("lease", Document.class);
        require(currentLease != null && lease.token().equals(currentLease.getString("token"))
                && clock.instant().isBefore(currentLease.getDate("until").toInstant()));
        return job;
    }
    Subject subject(ClientSession session, Document job, String epoch) {
        var db = store.database();
        var userKey = new Document("environment", properties.getEnvironment()).append("userId", job.getString("userId"));
        var life = db.getCollection("payment_account_lifecycles").find(session, userKey).first();
        require(life != null && Objects.equals(life.get("_id"), job.get("lifecycleId")) && "WITHDRAWN".equals(life.getString("state")));
        long lifeVersion = version(life);
        require(life.get("pendingEvidence") instanceof Long && life.getLong("pendingEvidence") >= 0);
        Instant withdrawn = Instant.parse(life.getString("withdrawnAtCanonical"));
        Instant eligible = withdrawn.plus(Duration.ofDays(15));
        long millis = eligible.toEpochMilli();
        if (eligible.getNano() % 1_000_000 != 0) millis = Math.addExact(millis, 1);
        require(new Date(millis).equals(job.get("eligibleAt")));
        var account = db.getCollection("payment_accounts").find(session, userKey).first();
        List<String> references = new ArrayList<>(); Long accountVersion = null; Instant activity = null;
        String accountId = job.getString("accountId");
        if (accountId == null) require(account == null);
        else {
            require(account != null && accountId.equals(account.getString("_id")) && "WITHDRAWN".equals(account.getString("state")));
            accountVersion = version(account);
            var refs = db.getCollection("purchase_account_refs").find(session,
                    new Document("accountId", accountId)).sort(new Document("_id", 1)).limit(101).into(new ArrayList<>());
            require(!refs.isEmpty() && refs.size() <= 100);
            for (var ref : refs) {
                require(properties.getEnvironment().equals(ref.getString("environment")) && Boolean.FALSE.equals(ref.get("active")));
                references.add(ref.getString("_id"));
            }
            require(references.contains(account.getString("activeRefId")));
            var cursors = db.getCollection("payment_reconcile_cursors").find(session,
                    new Document("accountId", accountId).append("kind", "ACCOUNT_LOOKUP")).limit(2).into(new ArrayList<>());
            require(cursors.size() == 1 && cursors.getFirst().get("lastActivityAt") instanceof Date);
            activity = cursors.getFirst().getDate("lastActivityAt").toInstant();
        }
        return new Subject(properties.getEnvironment(), job.getString("userId"), life.getString("_id"), lifeVersion,
                accountId, accountVersion, references, activity, life.getLong("pendingEvidence"), job.getDate("eligibleAt").toInstant(), epoch);
    }
    private static Document cleanup(Document job, String epoch) {
        var cleanup = job.get("cleanup", Document.class);
        if (cleanup == null) {
            require("WAITING".equals(job.getString("state")));
            return new Document("schemaVersion", 1L).append("epoch", epoch);
        }
        require(Long.valueOf(1).equals(cleanup.get("schemaVersion")));
        if (cleanup.containsKey("firstUnresolvedAt")) require(cleanup.getDate("firstUnresolvedAt").toInstant()
                .plus(Duration.ofDays(7)).equals(cleanup.getDate("reviewDueAt").toInstant()));
        return cleanup;
    }
    private static long version(Document row) {
        require(row.get("version") instanceof Long && row.getLong("version") > 0 && row.getLong("version") < Long.MAX_VALUE);
        return row.getLong("version");
    }
    private static Date date(Instant value) { return value == null ? null : Date.from(value); }
    private void acquire(ClientSession session, PaymentRecoveryFence.Ticket ticket) {
        fence.acquire(session, ticket, PaymentRecoveryFence.Writer.CLEANUP);
        var control = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(session,
                new Document("environment", properties.getEnvironment()).append("consumer", "billing")).first();
        require(control != null && Long.valueOf(4).equals(control.get("schemaVersion")));
    }
    private Document key(String id) { return new Document("_id", id).append("environment", properties.getEnvironment()); }
    private com.mongodb.client.MongoCollection<Document> jobs() { return store.database().getCollection(CleanupSchema.COLLECTION); }
}
