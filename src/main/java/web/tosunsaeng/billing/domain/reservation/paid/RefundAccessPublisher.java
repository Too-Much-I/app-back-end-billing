package web.tosunsaeng.billing.domain.reservation.paid;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtCodec;

/** Immutable body, fresh JWT per send, no I/O inside a Mongo transaction. Explicit OFF-gated construction. */
public final class RefundAccessPublisher {
    public static final String ROUTE = "/internal/v1/entitlements/access/events";
    public record Reply(int status, Map<String, List<String>> headers, byte[] body) {}
    public interface Transport { Reply post(URI uri, String bearer, byte[] body) throws Exception; }
    public static final class ContractFailure extends RuntimeException { public ContractFailure() { super("Invalid LC response"); } }
    private record Lease(String id, String owner, long version, long generation) {}
    record Decision(String state, String failure, Instant next) {}
    private final PaidExamTransactions tx;
    private final MongoTemplate mongo;
    private final PaymentProperties properties;
    private final PaymentRecoveryFence fence;
    private final String environment, audience;
    private final Clock clock;
    private final URI endpoint;
    private final WorkloadJwtCodec.Issuer issuer;
    private final Transport transport;
    public RefundAccessPublisher(PaidExamTransactions tx, MongoTemplate mongo, PaymentStore store, PaymentProperties properties,
            String environment, URI origin, String privateJwk, Clock clock, Transport transport) {
        if (!Set.of("test", "staging", "production").contains(environment) || origin == null || !"https".equals(origin.getScheme())
                || origin.getHost() == null || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                || !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))) throw new IllegalArgumentException("Invalid LC origin");
        if (properties.getRefund().isPublisherEnabled() && !properties.getRefund().isProcessingEnabled()) throw new IllegalStateException("Publisher requires processing");
        this.tx = tx; this.mongo = mongo; this.properties = properties; this.environment = environment; this.clock = clock; this.transport = transport;
        this.endpoint = origin.resolve(ROUTE); fence = new PaymentRecoveryFence(store, properties);
        issuer = new WorkloadJwtCodec.Issuer(privateJwk, "urn:tosunsaeng:workload:billing:" + environment, clock);
        audience = "tosunsaeng-learning-core:entitlement-access-events:" + environment;
        RefundFanoutSchema.verify(store.database());
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public synchronized int tick() {
        if (!enabled()) return 0;
        int count = 0;
        for (int n = 0; n < 20; n++) {
            Document row = mongo.findOne(new BasicQuery(due()).with(Sort.by("work.nextAttemptAt", "_id")), Document.class, RefundFanout.OUTBOX);
            if (row == null) break;
            if (sendOne(row.getString("_id"))) count++;
        }
        return count;
    }
    public boolean sendOne(String id) {
        if (!enabled()) return false;
        var ticket = fence.capture();
        Lease lease = tx.execute(c -> {
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            Document row = c.operations().findOne(new BasicQuery(due().append("_id", id)), Document.class, RefundFanout.OUTBOX);
            if (row == null) return null;
            Document work = row.get("work", Document.class);
            long v = increment(work.getLong("leaseVersion")), attempt = increment(work.getLong("attempts"));
            Lease result = new Lease(id, UUID.randomUUID().toString(), v, work.getLong("generation"));
            Update update = new Update().set("work.owner", result.owner()).set("work.until", Date.from(clock.instant().plusSeconds(30)))
                    .set("work.leaseVersion", v).set("work.attempts", attempt);
            if (attempt == 1) update.set("work.retryStartedAt", Date.from(clock.instant()));
            require(c.operations().updateFirst(new BasicQuery(due().append("_id", id).append("work.leaseVersion", v - 1)), update, RefundFanout.OUTBOX).getModifiedCount() == 1);
            return result;
        });
        if (lease == null) return false;
        // Recheck flags, lease and recovery epoch immediately before dispatch.
        Document ready = tx.execute(c -> {
            fence.check(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            return enabled() ? c.operations().findOne(new BasicQuery(held(lease)), Document.class, RefundFanout.OUTBOX) : null;
        });
        if (ready == null) return false;
        Document work = ready.get("work", Document.class);
        Instant deadline = work.getDate("retryStartedAt").toInstant().plus(Duration.ofHours(24));
        Decision decision;
        if (!clock.instant().isBefore(deadline)) decision = new Decision("REVIEW_REQUIRED", "REFUND_RETRY_EXHAUSTED", null);
        else if (ready.getString("body").getBytes(StandardCharsets.UTF_8).length > 16384
                || !RefundFanout.digest(ready.getString("body")).equals(ready.getString("digest")))
            decision = new Decision("REVIEW_REQUIRED", "LC_CONTRACT_ERROR", null);
        else {
            try {
                Reply reply = transport.post(endpoint, issuer.issue(audience), ready.getString("body").getBytes(StandardCharsets.UTF_8));
                decision = classify(reply, clock.instant(), deadline, work.getLong("attempts"));
            } catch (ContractFailure invalid) {
                decision = new Decision("REVIEW_REQUIRED", "LC_CONTRACT_ERROR", null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); decision = retry(clock.instant(), deadline, work.getLong("attempts"), null);
            } catch (Exception unavailable) { decision = retry(clock.instant(), deadline, work.getLong("attempts"), null); }
        }
        Decision result = decision;
        return tx.execute(c -> {
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            Update update = new Update().set("work.state", result.state()).set("work.failure", result.failure())
                    .set("work.nextAttemptAt", result.next() == null ? null : Date.from(result.next())).set("work.owner", null).set("work.until", null);
            if ("DELIVERED".equals(result.state())) update.set("deliveredAt", Date.from(clock.instant()));
            // Expired/old generation workers leave immutable body and newer delivery state untouched.
            return c.operations().updateFirst(new BasicQuery(held(lease)), update, RefundFanout.OUTBOX).getModifiedCount() == 1;
        });
    }
    private boolean enabled() { return properties.getRefund().isProcessingEnabled() && properties.getRefund().isPublisherEnabled(); }
    private Document due() {
        return new Document("environment", environment).append("work.state", "READY").append("work.nextAttemptAt", new Document("$lte", Date.from(clock.instant())))
                .append("$or", List.of(new Document("work.until", null), new Document("work.until", new Document("$lte", Date.from(clock.instant())))));
    }
    private Document held(Lease lease) {
        return new Document("_id", lease.id()).append("environment", environment).append("work.state", "READY")
                .append("work.owner", lease.owner()).append("work.leaseVersion", lease.version()).append("work.generation", lease.generation())
                .append("work.until", new Document("$gt", Date.from(clock.instant())));
    }
    static Decision classify(Reply reply, Instant now, Instant deadline, long attempt) {
        if (reply.body() == null || reply.body().length > 16384) return new Decision("REVIEW_REQUIRED", "LC_CONTRACT_ERROR", null);
        int status = reply.status();
        if (status == 204 && reply.body().length == 0) return new Decision("DELIVERED", null, null);
        if (status == 401 || status == 403) return new Decision("BLOCKED_AUTH", "LC_AUTHENTICATION_FAILED", null);
        if (status == 408 || status == 429 || status >= 500 && status <= 599)
            return retry(now, deadline, attempt, status == 429 || status == 503 ? retryAfter(reply.headers(), now) : null);
        if (status == 409) {
            String code = null;
            try { code = Document.parse(new String(reply.body(), StandardCharsets.UTF_8)).getString("code"); } catch (RuntimeException ignored) { }
            return new Decision("REVIEW_REQUIRED", Set.of("EVENT_ID_CONFLICT", "GROUP_REVOKE_CONFLICT").contains(code == null ? "" : code) ? code : "LC_CONTRACT_ERROR", null);
        }
        return new Decision("REVIEW_REQUIRED", "LC_CONTRACT_ERROR", null);
    }
    private static Decision retry(Instant now, Instant deadline, long attempt, Instant header) {
        long base = Math.min(3600, 5L << Math.min(10, Math.max(0, attempt - 1)));
        long delay = Math.min(3600, (long) (base * (1 + java.util.concurrent.ThreadLocalRandom.current().nextDouble())));
        Instant next = now.plusSeconds(delay);
        if (header != null && header.isAfter(next)) next = header;
        return !now.isBefore(deadline) || !next.isBefore(deadline)
                ? new Decision("REVIEW_REQUIRED", "REFUND_RETRY_EXHAUSTED", header)
                : new Decision("READY", header == null ? "LC_RETRY" : "LC_RETRY_AFTER", next);
    }
    static Instant retryAfter(Map<String, List<String>> headers, Instant now) {
        try {
            List<String> values = new ArrayList<>();
            headers.forEach((k, v) -> { if (k.equalsIgnoreCase("Retry-After")) values.addAll(v); });
            if (values.size() != 1) return null;
            String value = values.getFirst();
            if (value.matches("[0-9]+")) return now.plusSeconds(Long.parseLong(value));
            Instant parsed = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return parsed.isBefore(now) ? now : parsed;
        } catch (RuntimeException invalid) { return null; }
    }
    private static long increment(long value) { require(value >= 0 && value < Long.MAX_VALUE); return value + 1; }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException("Refund delivery precondition failed"); }
}
