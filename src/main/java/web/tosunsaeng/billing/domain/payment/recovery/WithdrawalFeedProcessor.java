package web.tosunsaeng.billing.domain.payment.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.ClientSession;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalApplier;
import web.tosunsaeng.billing.domain.payment.api.PaymentWithdrawalDecoder;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;
import static web.tosunsaeng.billing.domain.payment.recovery.RecoveryPayloadDecoder.*;

/** Bounded feed executor. A committed dispatch also counts a crash as a failed attempt. */
public final class WithdrawalFeedProcessor {
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    private final IdentityWithdrawalRecoveryClient client;
    private final PaymentRecoveryFence fence;
    private final PaymentWithdrawalApplier applier;
    private final RecoveryRetryPolicy retry;
    private final ObjectMapper json = new ObjectMapper();
    private record Dispatch(PaymentRecoveryFence.Ticket ticket, String runId, String leaseId,
                            String stream, long after, Long target, String cursor, long pageIndex) {
        @Override public String toString() { return "FeedDispatch[REDACTED]"; }
    }
    public WithdrawalFeedProcessor(PaymentStore store, PaymentProperties properties, Clock clock,
                                   IdentityWithdrawalRecoveryClient client, RecoveryRetryPolicy retry) {
        this.store = store; this.properties = properties; this.clock = clock; this.client = client; this.retry = retry;
        fence = new PaymentRecoveryFence(store, properties);
        applier = new PaymentWithdrawalApplier(store, properties, clock);
    }

    /** At most ten committed pages or thirty seconds, followed by a durable one-second yield. */
    public int tick() {
        require(properties.getRecovery().isFeedEnabled() && properties.getRecovery().isFenceRequired());
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int pages = 0;
        while (pages < 10 && System.nanoTime() < deadline) {
            Dispatch d = claim();
            if (d == null) break;
            try {
                FeedPage page = client.feedPage(d.stream(), d.after(), d.cursor(), d.target());
                validate(d, page);
                apply(d, page);
                pages++;
                if (page.done()) break;
            } catch (RecoveryRemoteException remote) {
                remoteFailure(d, remote); break;
            } catch (RecoveryContractException | web.tosunsaeng.billing.global.exception.InternalApiException invalid) {
                transition(d, "BLOCKED_CONTRACT", null, false); break;
            } catch (RuntimeException localFailure) {
                // Unknown local/transport exceptions are not evidence of source loss.
                // The durable dispatch retains the failure count and notBefore even if DB is unavailable.
                transition(d, null, null, false); break;
            }
        }
        if (pages == 10 || (pages > 0 && System.nanoTime() >= deadline)) yieldUntilNextTick();
        return pages;
    }

    private Dispatch claim() {
        var ticket = fence.capture();
        return store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            Document c = control(session);
            require(Set.of(3L, 4L).contains(c.get("schemaVersion")));
            if (!snapshotComplete(c)) return null;
            Document baseline = c.get("baseline", Document.class);
            require(baseline != null && c.get("checkpoint") instanceof Long);
            long after = c.getLong("checkpoint");
            require(after >= baseline.getLong("highWater"));
            Instant now = clock.instant();
            Document job = c.get("feedJob", Document.class);
            if (job == null) {
                job = new Document("epoch", ticket.epoch()).append("streamId", baseline.getString("streamId"))
                        .append("version", 1L).append("failures", 0L).append("attempts", 0L)
                        .append("state", "READY").append("nextAttemptAt", Date.from(now))
                        .append("leaseId", null).append("leaseUntil", Date.from(now)).append("lastSuccessAt", null);
                newScan(job, after, now);
            }
            require(ticket.epoch().equals(job.getString("epoch")) && baseline.getString("streamId").equals(job.getString("streamId")));
            if (blocked(job) || now.isBefore(job.getDate("nextAttemptAt").toInstant())
                    || (job.get("leaseId") != null && now.isBefore(job.getDate("leaseUntil").toInstant()))) return null;
            if (job.get("cursorExpiresAt") != null && !now.isBefore(job.getDate("cursorExpiresAt").toInstant()))
                newScan(job, after, now);
            if ("CAUGHT_UP".equals(job.getString("state"))) newScan(job, after, now);
            long failures = Math.min(20, job.getLong("failures") + 1);
            require(job.getLong("attempts") < Long.MAX_VALUE);
            job.put("attempts", job.getLong("attempts") + 1);
            job.put("failures", failures);
            job.put("state", failures >= 20 ? "CIRCUIT" : "RETRY");
            job.put("nextAttemptAt", Date.from(retry.failed(now, failures)));
            String lease = UUID.randomUUID().toString();
            job.put("leaseId", lease); job.put("leaseUntil", Date.from(now.plusSeconds(60)));
            // First scan deadline starts before HTTP, conservatively earlier than the producer's cursor.
            if (job.get("cursorExpiresAt") == null) job.put("cursorExpiresAt", Date.from(now.plusSeconds(3600)));
            save(session, job);
            return new Dispatch(ticket, job.getString("runId"), lease, job.getString("streamId"), after,
                    job.getLong("target"), job.getString("cursor"), job.getLong("pageIndex"));
        });
    }

    private void validate(Dispatch d, FeedPage page) {
        check(page != null && page.availability() != null);
        long target = page.targetThroughSequence();
        check(target >= d.after() && (d.target() == null || target == d.target()));
        long end = d.after() + Math.min(20, target - d.after());
        check(page.nextAfterSequence() == end && page.scannedThrough() == end && page.done() == (end == target));
        check(page.scanCursor() != null && page.scanCursor().matches("[!-~]{1,2048}"));
        var a = page.availability();
        check(d.stream().equals(a.streamId()) && a.replayFloor() >= 0
                && a.observedThroughSequence() >= target && a.replayFloor() <= a.observedThroughSequence()
                && a.checkedFromExclusive() == d.after() && a.checkedThroughSequence() == end
                && (end == d.after() || d.after() >= a.replayFloor()));
        check(page.items().size() == end - d.after());
        Set<String> ids = new HashSet<>();
        long sequence = d.after();
        var decoder = new PaymentWithdrawalDecoder(clock);
        for (var item : page.items()) {
            check(item != null && item.sequence() == ++sequence && item.event() != null);
            var event = item.event();
            // Validate fake/in-process adapters too; never trust a caller-supplied digest.
            try {
                var payload = json.createObjectNode().put("eventId", event.eventId()).put("schemaVersion", 1)
                        .put("userId", event.userId()).put("withdrawnAt", event.withdrawnAt().toString());
                check(decoder.decode(json.writeValueAsBytes(payload)).equals(event));
            } catch (Exception invalid) { throw new RecoveryContractException(); }
            check(ids.add(event.eventId()));
        }
    }

    private void apply(Dispatch d, FeedPage page) {
        store.transaction(session -> {
            Document job = locked(session, d);
            require(clock.instant().isBefore(job.getDate("cursorExpiresAt").toInstant()));
            for (var item : page.items()) applier.applyEvent(session, item.event());
            var receipt = scope().append("_id", UUID.randomUUID().toString()).append("epoch", d.ticket().epoch())
                    .append("kind", "FEED").append("runId", d.runId()).append("pageIndex", d.pageIndex())
                    .append("receiptDigest", digest(d, page)).append("expiresAt", Date.from(clock.instant().plus(Duration.ofDays(7))));
            store.database().getCollection(WithdrawalSnapshotProcessor.PAGES).insertOne(session, receipt);
            require(store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session,
                    scope().append("checkpoint", d.after()), new Document("$set", new Document("checkpoint", page.nextAfterSequence())))
                    .getMatchedCount() == 1);
            require(d.pageIndex() < Long.MAX_VALUE);
            job.put("pageIndex", d.pageIndex() + 1); job.put("target", page.targetThroughSequence());
            job.put("cursor", page.done() ? null : page.scanCursor());
            job.put("failures", 0L); job.put("lastSuccessAt", Date.from(clock.instant()));
            job.put("state", page.done() ? "CAUGHT_UP" : "READY");
            job.put("nextAttemptAt", Date.from(page.done()
                    ? retry.poll(clock.instant(), job.getLong("scanStart") == page.targetThroughSequence() ? 30 : 5)
                    : clock.instant()));
            if (page.done()) job.put("cursorExpiresAt", null);
            release(job); save(session, job);
            return null;
        });
    }

    private void remoteFailure(Dispatch d, RecoveryRemoteException failure) {
        if (failure.kind() == RecoveryRemoteException.Kind.AUTH) { transition(d, "BLOCKED_AUTH", null, false); return; }
        if (failure.kind() == RecoveryRemoteException.Kind.CONTRACT) { transition(d, "BLOCKED_CONTRACT", null, false); return; }
        if (failure.kind() == RecoveryRemoteException.Kind.TRANSIENT) { transition(d, null, failure.retryAt(), false); return; }
        var error = failure.error();
        switch (error.code()) {
            case COVERAGE_GAP, REPLAY_UNAVAILABLE -> {
                // Source loss is actionable only when it is bound to the current requested range.
                var a = error.details();
                long bound = d.target() == null && a != null ? a.observedThroughSequence() : d.target() == null ? -1 : d.target();
                boolean valid = a != null && d.stream().equals(a.streamId()) && bound >= d.after()
                        && a.observedThroughSequence() >= bound && a.replayFloor() >= 0
                        && a.replayFloor() <= a.observedThroughSequence()
                        && a.missingSequence() > d.after() && a.missingSequence() <= d.after() + Math.min(20, bound - d.after());
                if (error.code() == ErrorCode.COVERAGE_GAP)
                    valid &= a != null && a.fromSequence() == null && a.toSequence() == null && a.missingSequence() > a.replayFloor();
                else valid &= a != null && a.fromSequence() != null && a.toSequence() != null
                        && a.fromSequence() > 0 && a.fromSequence() <= a.missingSequence()
                        && a.missingSequence() <= a.toSequence() && a.toSequence() <= a.observedThroughSequence();
                transition(d, valid ? "SNAPSHOT_REQUIRED" : "BLOCKED_CONTRACT", null, false);
            }
            case CURSOR_EXPIRED -> transition(d, "READY", null, true);
            case STREAM_REPLACED -> transition(d, "STREAM_REPLACED", null, false);
            default -> transition(d, error.code().retryable ? null : "BLOCKED_CONTRACT", failure.retryAt(), false);
        }
    }
    private void transition(Dispatch d, String state, Instant notBefore, boolean resetScan) {
        store.transaction(session -> {
            Document job = locked(session, d);
            String nextState = state;
            if (resetScan) {
                newScan(job, d.after(), clock.instant());
                // Reopening a cursor must not evade the durable failure/probe budget.
                if (job.getLong("failures") < 20) job.put("nextAttemptAt", Date.from(clock.instant().plusSeconds(1)));
                else nextState = "CIRCUIT";
            }
            if (nextState != null) job.put("state", nextState);
            if (notBefore != null && notBefore.isAfter(job.getDate("nextAttemptAt").toInstant()))
                job.put("nextAttemptAt", ceilingMillis(notBefore));
            if (blocked(job)) { job.put("cursor", null); job.put("cursorExpiresAt", null); }
            if ("SNAPSHOT_REQUIRED".equals(state))
                store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, scope(),
                        new Document("$set", new Document("historicalUncertainty", true)));
            release(job); save(session, job); return null;
        });
    }

    private void yieldUntilNextTick() {
        var ticket = fence.capture();
        store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            Document job = control(session).get("feedJob", Document.class);
            if (job != null && "READY".equals(job.getString("state")) && job.get("leaseId") == null) {
                job.put("nextAttemptAt", Date.from(clock.instant().plusSeconds(1))); save(session, job);
            }
            return null;
        });
    }
    /** Expired feed receipts contain no raw cursor or user list. Separate from account deletion. */
    public int purgeExpiredReceipts() {
        var ticket = fence.capture();
        return store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            var pages = store.database().getCollection(WithdrawalSnapshotProcessor.PAGES);
            List<String> ids = pages.find(session, scope().append("kind", "FEED")
                            .append("expiresAt", new Document("$lte", Date.from(clock.instant()))))
                    .sort(new Document("expiresAt", 1).append("_id", 1)).limit(20).into(new ArrayList<>())
                    .stream().map(row -> row.getString("_id")).toList();
            return ids.isEmpty() ? 0 : Math.toIntExact(pages.deleteMany(session, new Document("_id", new Document("$in", ids))).getDeletedCount());
        });
    }
    private Document locked(ClientSession session, Dispatch d) {
        fence.acquire(session, d.ticket(), PaymentRecoveryFence.Writer.RECOVERY);
        Document c = control(session), job = c.get("feedJob", Document.class);
        require(snapshotComplete(c) && job != null && d.runId().equals(job.getString("runId"))
                && d.leaseId().equals(job.getString("leaseId")) && c.getLong("checkpoint") == d.after()
                && clock.instant().isBefore(job.getDate("leaseUntil").toInstant()));
        return job;
    }
    private void save(ClientSession session, Document job) {
        require(job.getLong("version") < Long.MAX_VALUE);
        job.put("version", job.getLong("version") + 1);
        store.database().getCollection(PaymentRecoveryFence.COLLECTION).updateOne(session, scope(),
                new Document("$set", new Document("feedJob", job)));
    }
    private void newScan(Document job, long after, Instant now) {
        job.put("runId", UUID.randomUUID().toString()); job.put("scanStart", after); job.put("pageIndex", 0L);
        job.put("target", null); job.put("cursor", null); job.put("cursorExpiresAt", null); job.put("scanStartedAt", Date.from(now));
    }
    private void release(Document job) { job.put("leaseId", null); job.put("leaseUntil", Date.from(clock.instant())); }
    private static boolean blocked(Document job) {
        return Set.of("BLOCKED_AUTH", "BLOCKED_CONTRACT", "SNAPSHOT_REQUIRED", "STREAM_REPLACED").contains(job.getString("state"));
    }
    private static boolean snapshotComplete(Document control) {
        var job = control.get("snapshotJob", Document.class);
        return job == null || "COMPLETE".equals(job.getString("state"));
    }
    private Document control(ClientSession s) {
        Document c = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(s, scope()).first();
        require(c != null); return c;
    }
    private Document scope() { return new Document("environment", properties.getEnvironment()).append("consumer", "billing"); }
    private String digest(Dispatch d, FeedPage page) {
        try {
            var root = json.createObjectNode().put("stream", d.stream()).put("after", d.after()).put("target", page.targetThroughSequence())
                    .put("end", page.nextAfterSequence());
            var entries = root.putArray("events");
            for (var item : page.items()) entries.addObject().put("sequence", item.sequence()).put("digest", item.event().digest());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(root)));
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }
    private static Date ceilingMillis(Instant value) {
        return new Date(Math.addExact(value.toEpochMilli(), value.getNano() % 1_000_000 == 0 ? 0 : 1));
    }
    private static void check(boolean value) { if (!value) throw new RecoveryContractException(); }
}
