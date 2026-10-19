package web.tosunsaeng.billing.domain.reservation.paid;

import com.mongodb.client.ClientSession;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Restricted operator output, never a public endpoint. Reads only; DONE is not delivery completion. */
public final class RefundOperationsReader {
    private static final Set<String> REFUND_REASONS = Set.of("REFUND_AMOUNT_UNKNOWN", "REFUND_AMOUNT_CONFLICT",
            "REFUND_BINDING_UNRESOLVED", "REFUND_FINALITY_UNRESOLVED", "REFUND_REVERSED",
            "REFUND_EVIDENCE_MISSING", "REFUND_RETRY_EXHAUSTED", "REVERSAL_REQUIRES_PLAN_012");
    private static final Document CASE_FIELDS = new Document("_id", 1).append("purchaseId", 1)
            .append("reviewState", 1).append("reason", 1).append("version", 1)
            .append("createdAt", 1).append("resolvedAt", 1);
    private final PaymentStore store;
    private final String environment, paymentEnvironment;
    private final Clock clock;
    public RefundOperationsReader(PaymentStore store, String environment, Clock clock) {
        if (!Set.of("test", "staging", "production").contains(environment)) throw new IllegalArgumentException("Invalid environment");
        this.store = store; this.environment = environment; this.clock = clock;
        paymentEnvironment = environment.equals("production") ? "PRODUCTION" : "SANDBOX";
        RefundFanoutSchema.verify(store.database());
    }
    public Document purchase(String id) {
        uuid(id);
        return store.transaction(s -> {
            Document p = find(s, "purchases", new Document("_id", id).append("environment", paymentEnvironment));
            if (p == null) return new Document("status", "NOT_FOUND");
            Document work = p.get("refundWork", Document.class);
            if (work == null) return new Document("status", "NO_REFUND_WORK");
            var groups = store.database().getCollection("attempt_groups").find(s,
                    new Document("sourceSnapshot.purchaseId", id).append("refundReview.state", "OPEN"))
                    .projection(new Document("_id", 1)).limit(101).maxTime(5, TimeUnit.SECONDS).into(new ArrayList<>());
            var deliveries = store.database().getCollection(RefundFanout.OUTBOX).find(s,
                    new Document("purchaseId", id).append("environment", environment).append("work.state", new Document("$ne", "DELIVERED")))
                    .projection(new Document("_id", 1)).limit(101).maxTime(5, TimeUnit.SECONDS).into(new ArrayList<>());
            boolean done = "DONE".equals(work.getString("phase"));
            return new Document("status", "FOUND").append("asOf", clock.instant().toString())
                    .append("work", workView(work)).append("enumerationDone", done)
                    .append("recoveryEpoch", epoch(s))
                    .append("reviewGroupIds", groups.stream().limit(100).map(d -> d.getString("_id")).toList())
                    .append("undeliveredEventIds", deliveries.stream().limit(100).map(d -> d.getString("_id")).toList())
                    .append("openReviewCount", Math.min(100, groups.size())).append("openReviewTruncated", groups.size() > 100)
                    .append("undeliveredCount", Math.min(100, deliveries.size())).append("undeliveredTruncated", deliveries.size() > 100)
                    .append("cleanupComplete", done && "READY".equals(work.getString("state")) && groups.isEmpty() && deliveries.isEmpty());
        });
    }
    public Document outbox(String id) {
        uuid(id);
        return store.transaction(s -> {
            Document row = find(s, RefundFanout.OUTBOX, new Document("_id", id).append("environment", environment));
            if (row == null) return new Document("status", "NOT_FOUND");
            return new Document("status", "FOUND").append("asOf", clock.instant().toString())
                    .append("recoveryEpoch", epoch(s))
                    .append("work", workView(row.get("work", Document.class)))
                    .append("deliveredAt", row.getDate("deliveredAt"));
        });
    }
    /** A live, bounded listing, not a durable snapshot/export or a total count. */
    public Document cases(String after) {
        if (after != null) uuid(after);
        return store.transaction(s -> {
            Document filter = caseFilter().append("state", "REVIEW_REQUIRED")
                    .append("$and", List.of(new Document("$or", List.of(new Document("reviewState", "OPEN"),
                            new Document("reviewState", new Document("$exists", false))))));
            if (after != null) filter.append("_id", new Document("$gt", after));
            var rows = store.database().getCollection("payment_purchase_cases").find(s, filter)
                    .projection(CASE_FIELDS).sort(new Document("_id", 1)).limit(101)
                    .maxTime(5, TimeUnit.SECONDS).into(new ArrayList<>());
            boolean more = rows.size() > 100;
            return new Document("asOf", clock.instant().toString())
                    .append("items", rows.stream().limit(100).map(this::caseView).toList())
                    .append("hasMore", more).append("nextAfter", more ? rows.get(99).getString("_id") : null);
        });
    }
    public Document reviewCase(String id) {
        uuid(id);
        return store.transaction(s -> {
            Document row = store.database().getCollection("payment_purchase_cases")
                    .find(s, caseFilter().append("_id", id)).projection(CASE_FIELDS)
                    .maxTime(5, TimeUnit.SECONDS).first();
            return row == null ? new Document("status", "NOT_FOUND") : new Document("status", "FOUND")
                    .append("asOf", clock.instant().toString()).append("review", caseView(row));
        });
    }
    private Document caseFilter() {
        return new Document("environment", paymentEnvironment).append("$or", List.of(
                new Document("reason", new Document("$in", new ArrayList<>(REFUND_REASONS))),
                new Document("normalizedObservation.kind", new Document("$in", List.of("REFUND", "REVOKE")))));
    }
    private Document caseView(Document row) {
        return new Document("caseId", row.getString("_id")).append("purchaseId", row.getString("purchaseId"))
                .append("reviewState", row.containsKey("reviewState")
                        ? safe(row.getString("reviewState"), Set.of("OPEN", "RESOLVED")) : "OPEN")
                .append("reason", safe(row.getString("reason"), REFUND_REASONS))
                .append("version", row.getLong("version")).append("createdAt", row.getDate("createdAt"))
                .append("resolvedAt", row.getDate("resolvedAt"));
    }
    private String epoch(ClientSession s) {
        Document control = find(s, web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence.COLLECTION,
                new Document("environment", paymentEnvironment).append("consumer", "billing"));
        if (control == null) throw new IllegalStateException("Missing recovery control");
        return control.getString("epoch");
    }
    private Document find(ClientSession s, String collection, Document filter) {
        return store.database().getCollection(collection).find(s, filter).maxTime(5, TimeUnit.SECONDS).first();
    }
    private Document workView(Document w) {
        var result = new Document();
        // Only fixed state values/numeric counters/times: no body, source, user, account, cursor or token.
        result.append("state", safe(w.getString("state"), Set.of("READY", "BLOCKED_AUTH", "REVIEW_REQUIRED", "DELIVERED")));
        if (w.containsKey("phase")) result.append("phase", safe(w.getString("phase"), Set.of("RESERVATIONS", "GROUPS", "DONE")));
        for (String key : List.of("leaseVersion", "generation", "attempts")) result.append(key, w.getLong(key));
        for (String key : List.of("nextAttemptAt", "retryStartedAt")) result.append(key, w.getDate(key));
        result.append("activeLease", w.getString("owner") != null && w.getDate("until") != null && w.getDate("until").toInstant().isAfter(clock.instant()));
        result.append("failure", safe(w.getString("failure"), Set.of("REFUND_RETRY_EXHAUSTED", "REFUND_STORAGE_UNAVAILABLE", "LC_CONTRACT_ERROR", "LC_AUTHENTICATION_FAILED", "EVENT_ID_CONFLICT", "GROUP_REVOKE_CONFLICT")));
        return result;
    }
    private static String safe(String value, Set<String> allowed) { return value == null ? null : allowed.contains(value) ? value : "OTHER"; }
    static void uuid(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid target");
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value) || id.version() != 4 || id.variant() != 2) throw new IllegalArgumentException("Invalid target");
    }
}
