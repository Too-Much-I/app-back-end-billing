package web.tosunsaeng.billing.domain.payment.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.ClientSession;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalApplier;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;
import static web.tosunsaeng.billing.domain.payment.recovery.RecoveryPayloadDecoder.*;

/**
 * Durable snapshot processor. The separately gated worker requires an authenticated client bean.
 * HTTP is outside Mongo transactions; all late responses must reacquire the original epoch/lease.
 */
public final class WithdrawalSnapshotProcessor {
    public static final String RUNS = "payment_recovery_snapshots";
    public static final String ROWS = "payment_recovery_snapshot_rows";
    public static final String PAGES = "payment_recovery_pages";
    public static final String OPERATIONS = "payment_recovery_operations";
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    private final IdentityWithdrawalRecoveryClient client;
    private final PaymentRecoveryFence fence;
    private final PaymentWithdrawalApplier applier;
    private final ObjectMapper json = new ObjectMapper();
    private Verification verification;
    private static final class Verification {
        final Dispatch dispatch; final Document run; final MessageDigest digest = sha();
        String after; long count;
        Verification(Dispatch dispatch, Document run) { this.dispatch = dispatch; this.run = run; }
    }
    private final RecoveryRetryPolicy retry = new RecoveryRetryPolicy(() -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());
    private record Dispatch(PaymentRecoveryFence.Ticket ticket, String operationId, String leaseId) {
        @Override public String toString() { return "SnapshotDispatch[REDACTED]"; }
    }
    public WithdrawalSnapshotProcessor(PaymentStore store, PaymentProperties properties, Clock clock,
                                       IdentityWithdrawalRecoveryClient client) {
        this.store = store; this.properties = properties; this.clock = clock; this.client = client;
        fence = new PaymentRecoveryFence(store, properties);
        applier = new PaymentWithdrawalApplier(store, properties, clock);
    }

    /** Explicit logical start, not an automatic new-run retry. Same operation never creates another run. */
    public void start(String streamId, String operationId, boolean historicalUncertainty) {
        startAtEpoch(streamId, operationId, historicalUncertainty, fence.capture());
    }
    public void startAtEpoch(String streamId, String operationId, boolean historicalUncertainty, PaymentRecoveryFence.Ticket ticket) {
        enabled(); id(streamId); id(operationId);
        require(ticket != null);
        store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            Document control = control(session);
            require(Set.of(2L, 3L, 4L).contains(control.get("schemaVersion")));
            Document old = control.get("snapshotJob", Document.class);
            Document operationKey = scope().append("operationId", operationId);
            Document existing = store.database().getCollection(OPERATIONS).find(session, operationKey).first();
            if (existing != null) {
                require(streamId.equals(existing.getString("streamId")) && ticket.epoch().equals(existing.getString("epoch")));
                require(old != null && operationId.equals(old.getString("operationId")));
                return null;
            }
            // Explicit start cannot bypass a failed job; automatic tick owns bounded transient retries.
            require(old == null || "COMPLETE".equals(old.getString("state")));
            createJob(session, ticket, control, streamId, operationId, historicalUncertainty, false, 1L);
            return null;
        });
    }

    private void createJob(ClientSession session, PaymentRecoveryFence.Ticket ticket, Document control,
                           String streamId, String operationId, boolean uncertain, boolean automatic, long runNumber) {
            Instant now = clock.instant();
            Document operationKey = scope().append("operationId", operationId);
            Document job = new Document("operationId", operationId).append("streamId", streamId)
                    .append("state", "REQUESTING").append("startedAt", Date.from(now))
                    .append("expiresAt", Date.from(now.plus(Duration.ofDays(7))))
                    .append("leaseId", null).append("leaseUntil", Date.from(now))
                    .append("snapshotId", null).append("version", 1L)
                    .append("automatic", automatic).append("runNumber", runNumber)
                    .append("failures", 0L).append("attempts", 0L).append("blocked", false)
                    .append("failureCode", null).append("nextAttemptAt", Date.from(now)).append("buildOverdue", false);
            store.database().getCollection(OPERATIONS).insertOne(session,
                    new Document(operationKey).append("_id", UUID.randomUUID().toString()).append("epoch", ticket.epoch())
                            .append("streamId", streamId).append("state", "ACTIVE").append("createdAt", Date.from(now))
                            .append("retentionExpiresAt", Date.from(now.plus(Duration.ofDays(120)))));
            store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, scope(),
                    new Document("$set", new Document("snapshotJob", job).append("historicalUncertainty",
                            uncertain || control.getBoolean("historicalUncertainty", false)))
                            .append("$unset", new Document("feedJob", "")));
    }

    /** One bounded page/status dispatch. Initial stream installation remains an explicit operation. */
    public synchronized void tick() {
        discardExpiredVerification();
        enabled();
        var ticket = fence.capture();
        boolean execute = store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            Document c = control(session);
            require(Long.valueOf(4).equals(c.get("schemaVersion")));
            Document job = c.get("snapshotJob", Document.class), feed = c.get("feedJob", Document.class);
            boolean gap = feed != null && "SNAPSHOT_REQUIRED".equals(feed.getString("state"));
            if (job == null || "COMPLETE".equals(job.getString("state"))) {
                if (!gap) return false;
                if (job != null && job.getBoolean("automatic", false)) {
                    job.put("blocked", true); job.put("failureCode", "REPEATED_GAP"); saveJob(session, job);
                    blockFeed(session); return false;
                }
                if (recentRuns(session) >= 3) { blockFeed(session); return false; }
                createJob(session, ticket, c, feed.getString("streamId"), UUID.randomUUID().toString(), true, true, 1L);
                return true;
            }
            // Missing retry fields are a migration error, never an implicit budget reset.
            require(job.get("attempts") instanceof Long && job.get("runNumber") instanceof Long);
            Document binding = store.database().getCollection(OPERATIONS).find(session,
                    scope().append("operationId", job.getString("operationId"))).first();
            require(binding != null && ticket.epoch().equals(binding.getString("epoch")));
            if (job.getBoolean("blocked", false)) return false;
            if (terminal(job)) {
                if (!job.getBoolean("automatic", false) || !Set.of("SNAPSHOT_BUILD_TIMEOUT", "SOURCE_EXPIRED")
                        .contains(Objects.toString(job.get("failureCode"), ""))) return false;
                if (job.getLong("runNumber") >= 3 || recentRuns(session) >= 3) {
                    job.put("blocked", true); job.put("failureCode", "RUN_BUDGET_EXHAUSTED"); saveJob(session, job); return false;
                }
                if (clock.instant().isBefore(job.getDate("nextAttemptAt").toInstant())) return false;
                createJob(session, ticket, c, job.getString("streamId"), UUID.randomUUID().toString(), true, true, job.getLong("runNumber") + 1);
                return true;
            }
            if (!job.getBoolean("automatic", false)) { job.put("automatic", true); saveJob(session, job); }
            return true;
        });
        if (execute) step();
        else verification = null; // Do not retain a user's key after the job becomes terminal/blocked.
    }
    private long recentRuns(ClientSession session) {
        return store.database().getCollection(OPERATIONS).find(session,
                scope().append("operator", new Document("$exists", false)).append("createdAt", new Document("$gt", Date.from(clock.instant().minus(Duration.ofDays(1))))))
                .limit(3).into(new ArrayList<>()).size();
    }
    private void blockFeed(ClientSession session) {
        store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, scope(),
                new Document("$set", new Document("feedJob.state", "BLOCKED_CONTRACT")));
    }

    /** Executes one HTTP operation or one verification pass; BUILDING polling is scheduled by the caller. */
    public synchronized String step() {
        discardExpiredVerification();
        enabled();
        var ticket = fence.capture();
        Dispatch dispatch = verification != null ? verification.dispatch : store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            Document control = control(session);
            require(Set.of(2L, 3L, 4L).contains(control.get("schemaVersion")));
            Document job = control.get("snapshotJob", Document.class);
            require(job != null);
            if (terminal(job) || job.getBoolean("blocked", false)) return null;
            if (!clock.instant().isBefore(job.getDate("expiresAt").toInstant())) {
                job.put("failureCode", "LOCAL_EXPIRED"); finish(session, job, "EXPIRED"); return null;
            }
            if ((job.getBoolean("automatic", false) || properties.getRecovery().isHttpEnabled())
                    && clock.instant().isBefore(job.getDate("nextAttemptAt").toInstant())) return null;
            if (job.get("leaseId") != null && clock.instant().isBefore(job.getDate("leaseUntil").toInstant())) return null;
            if (job.getBoolean("automatic", false) || properties.getRecovery().isHttpEnabled()) {
                require(job.getLong("attempts") < Long.MAX_VALUE);
                job.put("attempts", job.getLong("attempts") + 1);
                job.put("failures", Math.min(20, job.getLong("failures") + 1));
                job.put("nextAttemptAt", Date.from(retry.failed(clock.instant(), job.getLong("failures"))));
                if ("POLLING".equals(job.getString("state")) && !clock.instant().isBefore(job.getDate("startedAt").toInstant().plusSeconds(300)))
                    job.put("buildOverdue", true);
            }
            String lease = UUID.randomUUID().toString();
            job.put("leaseId", lease); job.put("leaseUntil", Date.from(clock.instant().plusSeconds(60)));
            saveJob(session, job);
            return new Dispatch(ticket, job.getString("operationId"), lease);
        });
        if (dispatch == null) return state();
        try {
            Document job = job(dispatch);
            switch (job.getString("state")) {
                case "REQUESTING" -> acceptStatus(dispatch,
                        client.createSnapshot(job.getString("streamId"), dispatch.operationId()));
                case "POLLING" -> acceptStatus(dispatch,
                        client.snapshotStatus(job.getString("snapshotId"), job.getString("streamId")));
                case "APPLYING" -> {
                    Document run = run(dispatch, job.getString("snapshotId"));
                    SnapshotPage page = client.snapshotPage(run.getString("snapshotId"), run.getString("nextCursor"));
                    applyPage(dispatch, page);
                }
                case "VERIFYING" -> verifyAndInstall(dispatch);
                default -> throw new RecoveryContractException();
            }
        } catch (RecoveryContractException | web.tosunsaeng.billing.global.exception.InternalApiException invalid) {
            verification = null;
            markFailed(dispatch);
            throw invalid;
        } catch (RecoveryRemoteException remote) {
            verification = null;
            remoteFailure(dispatch, remote);
        } catch (RuntimeException failure) {
            verification = null;
            throw failure;
        } finally {
            if (verification == null) release(dispatch);
        }
        return state();
    }
    public String state() {
        Document c = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(scope()).first();
        require(c != null && c.get("snapshotJob") instanceof Document);
        return c.get("snapshotJob", Document.class).getString("state");
    }

    /** Privacy cleanup remains callable with snapshot execution OFF; fencing still must be installed. */
    public int purgeExpiredBatch() {
        require(properties.getRecovery().isFenceRequired());
        var ticket = fence.capture();
        return store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            Document expired = store.database().getCollection(RUNS).find(session,
                    scope().append("expiresAt", new Document("$lte", Date.from(clock.instant()))))
                    .sort(new Document("expiresAt", 1).append("_id", 1)).first();
            if (expired == null) return 0;
            updateRun(session, expired, new Document("nextCursor", null).append("lastUserId", null));
            Document rowKey = scope().append("epoch", expired.getString("epoch")).append("snapshotId", expired.getString("snapshotId"));
            Document pageKey = scope().append("epoch", expired.getString("epoch")).append("runId", expired.getString("snapshotId"))
                    .append("kind", "SNAPSHOT");
            int deleted = deleteTwenty(session, ROWS, rowKey) + deleteTwenty(session, PAGES, pageKey);
            Document job = control(session).get("snapshotJob", Document.class);
            if (job != null && expired.getString("operationId").equals(job.getString("operationId")) && !terminal(job))
                finish(session, job, "EXPIRED");
            if (store.database().getCollection(ROWS).countDocuments(session, rowKey) == 0
                    && store.database().getCollection(PAGES).countDocuments(session, pageKey) == 0) {
                store.database().getCollection(RUNS).deleteOne(session, new Document("_id", expired.get("_id")));
                deleted++;
            }
            return deleted;
        });
    }
    private int deleteTwenty(ClientSession session, String collection, Document key) {
        var target = store.database().getCollection(collection);
        List<String> ids = target.find(session, key).sort(new Document("_id", 1)).limit(20)
                .into(new ArrayList<>()).stream().map(d -> d.getString("_id")).toList();
        if (ids.isEmpty()) return 0;
        return Math.toIntExact(target.deleteMany(session, new Document("_id", new Document("$in", ids))).getDeletedCount());
    }

    private void acceptStatus(Dispatch d, SnapshotStatus status) {
        check(status != null); id(status.snapshotId());
        store.transaction(session -> {
            Document job = lockedJob(session, d);
            check(Set.of("REQUESTING", "POLLING").contains(job.getString("state")));
            check(job.get("snapshotId") == null || status.snapshotId().equals(job.getString("snapshotId")));
            job.put("snapshotId", status.snapshotId());
            switch (status.status()) {
                case "BUILDING" -> {
                    check(status.manifest() == null && status.failureCode() == null);
                    job.put("state", "POLLING"); success(job, 5);
                    if (status.retryAt() != null && (job.getDate("nextAttemptAt") == null
                            || status.retryAt().isAfter(job.getDate("nextAttemptAt").toInstant()))) {
                        long millis = status.retryAt().toEpochMilli();
                        job.put("nextAttemptAt", new Date(Math.addExact(millis, status.retryAt().getNano() % 1_000_000 == 0 ? 0 : 1)));
                    }
                    saveJob(session, job);
                }
                case "FAILED", "EXPIRED" -> {
                    check(status.manifest() == null);
                    if ("FAILED".equals(status.status())) check(Set.of("SNAPSHOT_HISTORY_LOST", "SNAPSHOT_BUILD_TIMEOUT", "SNAPSHOT_BUILD_FAILED").contains(status.failureCode()));
                    else check(status.failureCode() == null);
                    job.put("failureCode", "EXPIRED".equals(status.status()) ? "SOURCE_EXPIRED" : status.failureCode());
                    finish(session, job, status.status());
                }
                case "READY" -> {
                    Manifest m = status.manifest();
                    check(m != null && status.failureCode() == null);
                    // Revalidate even a fake/in-process adapter: records themselves are not proof of validation.
                    check(m.snapshotId().equals(status.snapshotId()) && m.streamId().equals(job.getString("streamId")));
                    check(m.highWater() >= 0 && m.itemCount() >= 0 && m.pageCount() == Math.max(1,
                            m.itemCount() / 20 + (m.itemCount() % 20 == 0 ? 0 : 1)));
                    check(m.seconds() >= 0 && m.seconds() <= 4294967295L && m.increment() >= 0 && m.increment() <= 4294967295L);
                    check(m.contentDigest().matches("[0-9a-f]{64}"));
                    Instant expires = m.expiresAt().isBefore(job.getDate("expiresAt").toInstant())
                            ? m.expiresAt() : job.getDate("expiresAt").toInstant();
                    // Floor to BSON milliseconds intentionally expires conservatively, never later.
                    Date expiry = Date.from(expires);
                    if (!clock.instant().isBefore(expiry.toInstant())) { finish(session, job, "EXPIRED"); return null; }
                    Document run = scoped(d).append("_id", UUID.randomUUID().toString())
                            .append("snapshotId", m.snapshotId()).append("operationId", d.operationId())
                            .append("streamId", m.streamId()).append("tSeconds", m.seconds()).append("tIncrement", m.increment())
                            .append("highWater", m.highWater()).append("itemCount", m.itemCount()).append("pageCount", m.pageCount())
                            .append("digest", m.contentDigest()).append("expiresAt", expiry)
                            .append("receivedCount", 0L).append("pagesApplied", 0L).append("lastUserId", null)
                            .append("nextCursor", null).append("version", 1L).append("state", "APPLYING");
                    store.database().getCollection(RUNS).insertOne(session, run);
                    job.put("expiresAt", expiry); job.put("state", "APPLYING"); success(job, 1); saveJob(session, job);
                    if (!"COMPLETE".equals(m.legacyCoverage()) || !m.knownGaps().isEmpty())
                        store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, scope(),
                                new Document("$set", new Document("historicalUncertainty", true)));
                }
                default -> throw new RecoveryContractException();
            }
            return null;
        });
    }

    private void applyPage(Dispatch d, SnapshotPage page) {
        check(page != null);
        store.transaction(session -> {
            Document job = lockedJob(session, d);
            check("APPLYING".equals(job.getString("state")));
            Document run = run(session, d, job.getString("snapshotId"));
            check("APPLYING".equals(run.getString("state")));
            long index = run.getLong("pagesApplied"), received = run.getLong("receivedCount");
            long remaining = run.getLong("itemCount") - received;
            check(remaining >= 0 && page.items().size() == Math.min(20, remaining));
            boolean last = index == run.getLong("pageCount") - 1;
            check(index < run.getLong("pageCount") && page.done() == last && page.done() == (page.nextCursor() == null));
            if (!last) {
                check(page.nextCursor().matches("[!-~]{1,2048}") && !page.nextCursor().equals(run.getString("nextCursor")));
            }
            String previous = run.getString("lastUserId");
            for (WithdrawalFact fact : page.items()) {
                check(fact != null && (previous == null || previous.compareTo(fact.userId()) < 0));
                check(!fact.withdrawnAt().isAfter(clock.instant().plusSeconds(60)));
                applier.applyFact(session, fact);
                store.database().getCollection(ROWS).insertOne(session, scoped(d).append("_id", UUID.randomUUID().toString())
                        .append("snapshotId", run.getString("snapshotId")).append("userId", fact.userId())
                        .append("withdrawnAt", fact.withdrawnAt().toString()).append("expiresAt", run.getDate("expiresAt")));
                previous = fact.userId();
            }
            String digest = receipt(run.getString("nextCursor"), page);
            store.database().getCollection(PAGES).insertOne(session, scoped(d).append("_id", UUID.randomUUID().toString())
                    .append("kind", "SNAPSHOT").append("runId", run.getString("snapshotId")).append("pageIndex", index)
                    .append("receiptDigest", digest).append("expiresAt", run.getDate("expiresAt")));
            Document changes = new Document("receivedCount", Math.addExact(received, page.items().size()))
                    .append("pagesApplied", Math.addExact(index, 1)).append("lastUserId", previous)
                    .append("nextCursor", page.nextCursor()).append("state", last ? "VERIFYING" : "APPLYING");
            updateRun(session, run, changes);
            job.put("state", last ? "VERIFYING" : "APPLYING"); success(job, 1); saveJob(session, job);
            return null;
        });
    }

    /** At most 10 keyset pages or 30 seconds per tick. A lost process/lease restarts hashing. */
    private void verifyAndInstall(Dispatch d) {
        renew(d);
        if (verification == null) {
            Document job = job(d);
            Document run = run(d, job.getString("snapshotId"));
            check("VERIFYING".equals(run.getString("state")));
            verification = new Verification(d, run);
        }
        Verification v = verification;
        Document run = v.run;
        check(v.dispatch.equals(d) && clock.instant().isBefore(run.getDate("expiresAt").toInstant()));
        long started = System.nanoTime();
        boolean done = false;
        for (int page = 0; page < 10 && System.nanoTime() - started < Duration.ofSeconds(30).toNanos(); page++) {
            renew(d);
            Document query = scoped(d).append("snapshotId", run.getString("snapshotId"));
            if (v.after != null) query.append("userId", new Document("$gt", v.after));
            List<Document> rows = store.database().getCollection(ROWS).find(query).sort(new Document("userId", 1))
                    .limit(20).into(new ArrayList<>());
            for (Document row : rows) {
                String user = row.getString("userId");
                check(v.after == null || v.after.compareTo(user) < 0);
                byte[] bytes = canonical(new WithdrawalFact(user, Instant.parse(row.getString("withdrawnAt"))));
                v.digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); v.digest.update(bytes);
                v.after = user; v.count = Math.addExact(v.count, 1);
                check(v.count <= run.getLong("itemCount"));
            }
            if (rows.size() < 20) { done = true; break; }
        }
        if (!done) return; // Retain this exact lease and constant-size in-memory digest until next tick.
        check(v.count == run.getLong("itemCount"));
        check(HexFormat.of().formatHex(v.digest.digest()).equals(run.getString("digest")));
        final long verifiedCount = v.count;
        store.transaction(session -> {
            Document currentJob = lockedJob(session, d);
            check("VERIFYING".equals(currentJob.getString("state")));
            Document current = run(session, d, currentJob.getString("snapshotId"));
            check(current.getLong("version").equals(run.getLong("version"))
                    && current.getLong("receivedCount") == verifiedCount
                    && current.getLong("pagesApplied").equals(current.getLong("pageCount")));
            Document control = control(session);
            Document old = control.get("baseline", Document.class);
            if (old != null) {
                // Source replacement requires the separate explicit epoch installer; do not compare different streams.
                check(old.getString("streamId").equals(current.getString("streamId")));
                check(control.get("checkpoint") instanceof Long && current.getLong("highWater") >= control.getLong("checkpoint"));
            }
            Document baseline = new Document("streamId", current.getString("streamId"))
                    .append("tSeconds", current.getLong("tSeconds")).append("tIncrement", current.getLong("tIncrement"))
                    .append("highWater", current.getLong("highWater")).append("installedAt", Date.from(clock.instant()));
            store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, scope(),
                    new Document("$set", new Document("baseline", baseline).append("checkpoint", current.getLong("highWater"))));
            updateRun(session, current, new Document("state", "INSTALLED"));
            finish(session, currentJob, "COMPLETE");
            return null;
        });
        verification = null;
    }

    private void renew(Dispatch d) {
        store.transaction(session -> {
            Document job = lockedJob(session, d);
            job.put("leaseUntil", Date.from(clock.instant().plusSeconds(60))); saveJob(session, job); return null;
        });
    }
    private Document lockedJob(ClientSession session, Dispatch d) {
        fence.acquire(session, d.ticket(), PaymentRecoveryFence.Writer.RECOVERY);
        Document job = control(session).get("snapshotJob", Document.class);
        require(job != null && d.operationId().equals(job.getString("operationId")) && d.leaseId().equals(job.getString("leaseId")));
        require(clock.instant().isBefore(job.getDate("leaseUntil").toInstant()));
        require(clock.instant().isBefore(job.getDate("expiresAt").toInstant()));
        require(!terminal(job));
        return job;
    }
    private void saveJob(ClientSession session, Document job) {
        long version = job.getLong("version");
        require(version > 0 && version < Long.MAX_VALUE);
        Document match = new Document(scope()).append("snapshotJob.operationId", job.getString("operationId"))
                .append("snapshotJob.version", version);
        job.put("version", version + 1);
        require(store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, match,
                new Document("$set", new Document("snapshotJob", job))).getModifiedCount() == 1);
    }
    private void updateRun(ClientSession session, Document run, Document changes) {
        long version = run.getLong("version");
        require(version > 0 && version < Long.MAX_VALUE);
        require(store.database().getCollection(RUNS).updateOne(session,
                new Document("_id", run.get("_id")).append("version", version),
                new Document("$set", changes).append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
    }
    private void finish(ClientSession session, Document job, String state) {
        job.put("state", state); job.put("leaseId", null); job.put("leaseUntil", Date.from(clock.instant()));
        if ("COMPLETE".equals(state)) success(job, 0);
        else if (job.containsKey("runNumber")) job.put("nextAttemptAt", Date.from(clock.instant().plusSeconds(job.getLong("runNumber") == 1 ? 60 : 300)));
        saveJob(session, job);
        require(store.database().getCollection(OPERATIONS).updateOne(session,
                scope().append("operationId", job.getString("operationId")), new Document("$set", new Document("state", state)))
                .getMatchedCount() == 1);
    }
    private void markFailed(Dispatch d) {
        store.transaction(session -> { Document job = lockedJob(session, d); job.put("failureCode", "CONTRACT"); finish(session, job, "FAILED"); return null; });
    }
    private void success(Document job, long delaySeconds) {
        if (!job.containsKey("attempts")) return;
        job.put("failures", 0L); job.put("nextAttemptAt", Date.from(clock.instant().plusSeconds(delaySeconds)));
    }
    private void remoteFailure(Dispatch d, RecoveryRemoteException failure) {
        store.transaction(session -> {
            Document job = lockedJob(session, d);
            boolean transientError = failure.kind() == RecoveryRemoteException.Kind.TRANSIENT
                    || (failure.kind() == RecoveryRemoteException.Kind.APPLICATION && failure.error().code().retryable);
            if (!transientError) {
                // EXPIRED/FAILED page errors need a valid status response before any new operation.
                if (failure.kind() == RecoveryRemoteException.Kind.APPLICATION
                        && Set.of(ErrorCode.SNAPSHOT_EXPIRED, ErrorCode.SNAPSHOT_FAILED).contains(failure.error().code())
                        && job.get("snapshotId") != null) {
                    job.put("state", "POLLING");
                } else {
                    job.put("blocked", true);
                    job.put("failureCode", failure.kind() == RecoveryRemoteException.Kind.AUTH ? "AUTH" : "CONTRACT");
                }
            }
            if (failure.retryAt() != null && failure.retryAt().isAfter(job.getDate("nextAttemptAt").toInstant())) {
                long millis = failure.retryAt().toEpochMilli();
                job.put("nextAttemptAt", new Date(Math.addExact(millis, failure.retryAt().getNano() % 1_000_000 == 0 ? 0 : 1)));
            }
            saveJob(session, job); return null;
        });
    }
    private void release(Dispatch d) {
        // Only release the original lease; never borrow a new epoch after an interrupted response.
        store.transaction(session -> {
            Document c = control(session);
            if (!d.ticket().epoch().equals(c.getString("epoch"))) return null;
            Document job = c.get("snapshotJob", Document.class);
            if (job == null || !d.operationId().equals(job.getString("operationId"))
                    || !d.leaseId().equals(job.getString("leaseId"))) return null;
            fence.acquire(session, d.ticket(), PaymentRecoveryFence.Writer.RECOVERY);
            job.put("leaseId", null); job.put("leaseUntil", Date.from(clock.instant())); saveJob(session, job); return null;
        });
    }
    private Document job(Dispatch d) {
        Document c = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(scope()).first();
        require(c != null && d.ticket().epoch().equals(c.getString("epoch")));
        Document job = c.get("snapshotJob", Document.class);
        require(job != null && d.operationId().equals(job.getString("operationId")) && d.leaseId().equals(job.getString("leaseId")));
        return job;
    }
    private Document run(Dispatch d, String snapshot) {
        Document value = store.database().getCollection(RUNS).find(scoped(d).append("snapshotId", snapshot)).first();
        require(value != null); return value;
    }
    private Document run(ClientSession session, Dispatch d, String snapshot) {
        Document value = store.database().getCollection(RUNS).find(session, scoped(d).append("snapshotId", snapshot)).first();
        require(value != null); return value;
    }
    private Document control(ClientSession session) {
        Document value = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(session, scope()).first();
        require(value != null); return value;
    }
    private Document scope() { return new Document("environment", properties.getEnvironment()).append("consumer", "billing"); }
    private Document scoped(Dispatch d) { return scope().append("epoch", d.ticket().epoch()); }
    private void enabled() { require(properties.getRecovery().isSnapshotEnabled() && properties.getRecovery().isFenceRequired()); }
    private static boolean terminal(Document job) { return Set.of("COMPLETE", "FAILED", "EXPIRED").contains(job.getString("state")); }
    private byte[] canonical(WithdrawalFact fact) {
        try {
            var node = json.createObjectNode().put("userId", fact.userId()).put("withdrawnAt", fact.withdrawnAt().toString());
            return json.writeValueAsBytes(node);
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }
    private String receipt(String cursor, SnapshotPage page) {
        try {
            var node = json.createObjectNode();
            node.put("cursor", cursor); node.put("nextCursor", page.nextCursor()); node.put("done", page.done());
            var rows = node.putArray("items");
            for (var fact : page.items()) rows.add(json.readTree(canonical(fact)));
            return HexFormat.of().formatHex(sha().digest(json.writeValueAsBytes(node)));
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }
    private static MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (Exception impossible) { throw new IllegalStateException(); }
    }
    private void discardExpiredVerification() {
        if (verification != null && !clock.instant().isBefore(verification.run.getDate("expiresAt").toInstant())) verification = null;
    }
    private static void id(String value) {
        try { UUID id = UUID.fromString(value); check(id.toString().equals(value) && id.version() == 4 && id.variant() == 2); }
        catch (Exception invalid) { throw new RecoveryContractException(); }
    }
    private static void check(boolean valid) { if (!valid) throw new RecoveryContractException(); }
}
