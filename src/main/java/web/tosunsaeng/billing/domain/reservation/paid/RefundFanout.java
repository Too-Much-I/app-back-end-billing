package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

/** Bounded, database-owned fan-out. Explicit construction only until the provider/rollout gates are met. */
public final class RefundFanout {
    public static final String OUTBOX = "payment_access_outbox";
    private final PaidExamTransactions tx;
    private final MongoTemplate mongo;
    private final PaidReservationService reservations;
    private final PaymentRecoveryFence fence;
    private final PaymentProperties properties;
    private final String environment, scope;
    private final Clock clock;
    private record Lease(String purchaseId, String owner, long version, long generation) {}
    public RefundFanout(PaidExamTransactions tx, MongoTemplate mongo, PaidReservationService reservations,
            PaymentStore store, PaymentProperties properties, String environment, String scope, Clock clock) {
        this.tx = tx; this.mongo = mongo; this.reservations = reservations; this.properties = properties;
        this.environment = Objects.requireNonNull(environment); this.scope = Objects.requireNonNull(scope); this.clock = clock;
        if (scope.isBlank() || scope.length() > 128 || !Set.of("test", "staging", "production").contains(environment))
            throw new IllegalArgumentException("Invalid refund environment or scope");
        fence = new PaymentRecoveryFence(store, properties);
        RefundFanoutSchema.verify(store.database());
    }
    /** Scheduler contract: fixed delay five seconds; no parallel work inside one instance. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public synchronized int tick() {
        if (!properties.getRefund().isProcessingEnabled()) return 0;
        int completed = 0;
        for (int n = 0; n < 20; n++) {
            var query = due(clock.instant());
            Document purchase = mongo.findOne(new BasicQuery(query).with(Sort.by("refundWork.nextAttemptAt", "_id")), Document.class, "purchases");
            if (purchase == null) break;
            if (advanceOne(purchase.getString("_id"))) completed++;
        }
        return completed;
    }
    public boolean advanceOne(String purchaseId) {
        if (!properties.getRefund().isProcessingEnabled()) return false;
        Lease lease = claim(purchaseId, false);
        if (lease == null) return false;
        try {
            var ticket = fence.capture();
            return tx.execute(c -> {
                fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                Document purchase = held(c, lease); Document work = purchase.get("refundWork", Document.class);
                Instant now = clock.instant();
                if (!now.isBefore(work.getDate("retryStartedAt").toInstant().plus(Duration.ofHours(24)))) {
                    finish(c, lease, new Update().set("refundWork.state", "REVIEW_REQUIRED").set("refundWork.failure", "REFUND_RETRY_EXHAUSTED"));
                    return false;
                }
                String phase = work.getString("phase");
                Document source = new Document("sourceSnapshot.purchaseId", purchaseId);
                if (work.getString("afterId") != null) source.append("_id", new Document("$gt", work.getString("afterId")));
                Update progress = new Update().set("refundWork.failure", null).set("refundWork.nextAttemptAt", Date.from(now));
                if ("RESERVATIONS".equals(phase)) {
                    source.append("status", "RESERVED");
                    Reservation r = c.operations().findOne(new BasicQuery(source).with(Sort.by("_id")), Reservation.class);
                    if (r != null) {
                        reservations.cancelRefunded(c, r, purchase, now);
                        progress.set("refundWork.afterId", r.getReservationId());
                    } else {
                        // Cursor cannot hide a RESERVED record inserted by an invalid old writer.
                        require(!c.operations().exists(new BasicQuery(new Document("sourceSnapshot.purchaseId", purchaseId).append("status", "RESERVED")), "reservations"));
                        progress.set("refundWork.phase", "GROUPS").set("refundWork.afterId", null);
                    }
                } else if ("GROUPS".equals(phase)) {
                    Document group = c.operations().findOne(new BasicQuery(source).with(Sort.by("_id")), Document.class, "attempt_groups");
                    if (group == null) progress.set("refundWork.phase", "DONE").set("refundWork.afterId", null).set("refundWork.nextAttemptAt", null);
                    else {
                        group(c, purchase, group, now);
                        progress.set("refundWork.afterId", group.getString("_id"));
                    }
                } else throw new IllegalStateException("Invalid refund phase");
                finish(c, lease, progress);
                return true;
            });
        } catch (RuntimeException unavailable) {
            failed(lease);
            return false;
        }
    }
    /** Rechecks existing evidence only. No caller-supplied protected ID, cursor rewind, or invented event. */
    public boolean recheckGroup(String purchaseId, String groupId) {
        if (!properties.getRefund().isProcessingEnabled()) return false;
        Lease lease = claim(purchaseId, true);
        if (lease == null) return false;
        try {
            var ticket = fence.capture();
            return tx.execute(c -> {
                fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                Document purchase = held(c, lease);
                Document group = c.operations().findById(groupId, Document.class, "attempt_groups");
                require(group != null && group.get("refundReview", Document.class) != null
                        && purchase.get("refund", Document.class).getString("effectId").equals(group.get("refundReview", Document.class).getString("effectId")));
                group(c, purchase, group, clock.instant());
                finish(c, lease, new Update());
                return true;
            });
        } catch (RuntimeException unavailable) { failed(lease); return false; }
    }
    private Lease claim(String id, boolean recheck) {
        var ticket = fence.capture();
        return tx.execute(c -> {
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            Instant now = clock.instant();
            Document query = due(now).append("_id", id);
            if (recheck) { query.remove("refundWork.phase"); query.remove("refundWork.nextAttemptAt"); }
            Document row = c.operations().findOne(new BasicQuery(query), Document.class, "purchases");
            if (row == null) return null;
            Document w = row.get("refundWork", Document.class);
            long version = increment(w.getLong("leaseVersion")), generation = w.getLong("generation"), attempts = increment(w.getLong("attempts"));
            require(generation >= 1);
            Lease lease = new Lease(id, UUID.randomUUID().toString(), version, generation);
            Update update = new Update().set("refundWork.owner", lease.owner()).set("refundWork.until", Date.from(now.plusSeconds(30)))
                    .set("refundWork.leaseVersion", version).set("refundWork.attempts", attempts);
            if (attempts == 1) update.set("refundWork.retryStartedAt", Date.from(now));
            require(c.operations().updateFirst(new BasicQuery(query.append("refundWork.leaseVersion", version - 1)), update, "purchases").getModifiedCount() == 1);
            return lease;
        });
    }
    private Document due(Instant now) {
        return new Document("environment", properties.getEnvironment()).append("accessState", "REVOKED")
                .append("refundWork.state", "READY").append("refundWork.phase", new Document("$ne", "DONE"))
                .append("refundWork.nextAttemptAt", new Document("$lte", Date.from(now)))
                .append("$or", List.of(new Document("refundWork.until", null), new Document("refundWork.until", new Document("$lte", Date.from(now)))));
    }
    private Document held(PaidExamTransactions.Context c, Lease lease) {
        Document row = c.operations().findOne(new BasicQuery(heldQuery(lease)), Document.class, "purchases");
        require(row != null && row.get("refund", Document.class) != null);
        return row;
    }
    private Document heldQuery(Lease lease) {
        return new Document("_id", lease.purchaseId()).append("environment", properties.getEnvironment()).append("accessState", "REVOKED")
                .append("refundWork.state", "READY").append("refundWork.owner", lease.owner()).append("refundWork.leaseVersion", lease.version())
                .append("refundWork.generation", lease.generation()).append("refundWork.until", new Document("$gt", Date.from(clock.instant())));
    }
    private void finish(PaidExamTransactions.Context c, Lease lease, Update progress) {
        progress.set("refundWork.owner", null).set("refundWork.until", null);
        require(c.operations().updateFirst(new BasicQuery(heldQuery(lease)), progress, "purchases").getModifiedCount() == 1);
    }
    private void failed(Lease lease) {
        var ticket = fence.capture();
        tx.execute(c -> {
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            Document p = c.operations().findOne(new BasicQuery(heldQuery(lease)), Document.class, "purchases");
            if (p == null) return null; // Expired/stolen worker cannot write over a newer lease.
            Document w = p.get("refundWork", Document.class); Instant now = clock.instant();
            boolean exhausted = !now.isBefore(w.getDate("retryStartedAt").toInstant().plus(Duration.ofHours(24)));
            long base = Math.min(3600, 5L << Math.min(10, w.getLong("attempts") - 1));
            long delay = Math.min(3600, (long) (base * (1 + java.util.concurrent.ThreadLocalRandom.current().nextDouble())));
            finish(c, lease, new Update().set("refundWork.state", exhausted ? "REVIEW_REQUIRED" : "READY")
                    .set("refundWork.failure", exhausted ? "REFUND_RETRY_EXHAUSTED" : "REFUND_STORAGE_UNAVAILABLE")
                    .set("refundWork.nextAttemptAt", exhausted || "DONE".equals(w.getString("phase")) ? null : Date.from(now.plusSeconds(delay))));
            return null;
        });
    }
    private void group(PaidExamTransactions.Context c, Document purchase, Document group, Instant now) {
        require(group.getLong("version") > 0 && group.getLong("version") < Long.MAX_VALUE);
        String id = group.getString("_id"), purchaseId = purchase.getString("_id");
        Document refund = purchase.get("refund", Document.class), source = group.get("sourceSnapshot", Document.class);
        require(source != null && "SUBSCRIPTION".equals(source.getString("type")) && purchaseId.equals(source.getString("purchaseId"))
                && purchase.getString("accountId").equals(source.getString("accountId")));
        Document key = new Document("environment", environment).append("producer", "billing").append("consumerScopeId", scope).append("attemptGroupId", id);
        Document existing = c.operations().findOne(new BasicQuery(key), Document.class, OUTBOX);
        Update update = new Update().set("replacementBlocked", true).inc("version", 1);
        if (existing != null) {
            require(purchaseId.equals(existing.getString("purchaseId")) && refund.getString("effectId").equals(existing.getString("refundEffectId")));
        } else {
            String sessionId = group.getString("lastConfirmedSessionId");
            Document session = sessionId == null ? null : c.operations().findById(sessionId, Document.class, "attempt_sessions");
            Document account = c.operations().findById(purchase.getString("accountId"), Document.class, "payment_accounts");
            boolean evidence = session != null && session.getDate("confirmedAt") != null && id.equals(session.getString("attemptGroupId"))
                    && Objects.equals(group.getString("subjectRefId"), session.getString("subjectRefId"))
                    && source.equals(session.get("sourceSnapshot", Document.class)) && account != null
                    && properties.getEnvironment().equals(account.getString("environment"));
            if (!evidence) {
                if (group.get("refundReview") == null) update.set("refundReview", new Document("effectId", refund.getString("effectId"))
                        .append("state", "OPEN").append("reason", "REFUND_EVIDENCE_MISSING").append("createdAt", Date.from(now)).append("resolvedAt", null));
            } else {
                Document fact = c.operations().findOne(new BasicQuery(new Document("effectKey", "refund-access:" + purchaseId).append("type", "ACCESS_REVOKED")), Document.class, "payment_ledger");
                require(fact != null && refund.getString("effectId").equals(fact.get("detail", Document.class).getString("effectId")));
                String reason = fact.get("detail", Document.class).getString("reason");
                require("REFUND".equals(reason) || "REVOKE".equals(reason));
                String eventId = UUID.randomUUID().toString();
                Document body = new Document("eventId", eventId).append("eventType", "AttemptGroupAccessRevoked").append("schemaVersion", 1)
                        .append("producer", "billing").append("consumerScopeId", scope)
                        .append("occurredAt", DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(refund.getDate("appliedAt").toInstant()))
                        .append("userId", account.getString("userId")).append("attemptGroupId", id)
                        .append("reason", "REFUND".equals(reason) ? "PURCHASE_REFUNDED" : "PURCHASE_REVOKED").append("confirmedSessionId", sessionId);
                String json = canonical(body);
                require(json.getBytes(StandardCharsets.UTF_8).length <= 16384);
                c.operations().insert(new Document(key).append("_id", eventId).append("eventId", eventId).append("purchaseId", purchaseId)
                        .append("refundEffectId", refund.getString("effectId")).append("body", json).append("digest", digest(json))
                        .append("createdAt", Date.from(now)).append("deliveredAt", null)
                        .append("work", new Document("state", "READY").append("owner", null).append("until", null).append("leaseVersion", 1L)
                                .append("generation", 1L).append("nextAttemptAt", Date.from(now)).append("retryStartedAt", Date.from(now))
                                .append("attempts", 0L).append("failure", null)), OUTBOX);
                if (group.get("refundReview") != null) update.set("refundReview.state", "RESOLVED").set("refundReview.resolvedAt", Date.from(now));
            }
        }
        require(c.operations().updateFirst(new BasicQuery(new Document("_id", id).append("version", group.getLong("version"))), update, "attempt_groups").getModifiedCount() == 1);
    }
    static String canonical(Document body) {
        try { return new ObjectMapper().writeValueAsString(body); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid revoke body"); }
    }
    static String digest(String body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static long increment(long value) { require(value >= 0 && value < Long.MAX_VALUE); return value + 1; }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException("Refund fan-out precondition failed"); }
}
