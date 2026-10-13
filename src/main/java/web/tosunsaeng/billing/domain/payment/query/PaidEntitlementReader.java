package web.tosunsaeng.billing.domain.payment.query;

import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.PaymentOffer;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;

/** MongoTemplate joins the free reader's Spring-bound snapshot session. No raw PaymentStore reads. */
public final class PaidEntitlementReader {
    private final MongoTemplate mongo;
    private final String environment;
    public PaidEntitlementReader(MongoTemplate mongo, PaymentProperties p) { this.mongo = mongo; environment = p.getEnvironment(); }
    public void prepare() {
        // Metadata cannot be queried inside a snapshot transaction. Fail closed on a removed collection.
        require(mongo.getCollectionNames().containsAll(List.of("payment_accounts", "payment_account_lifecycles", "purchase_account_refs",
                "payment_recovery_controls", "payment_sync_commands", "payment_provider_inbox", "payment_purchase_cases",
                "purchases", "subscription_entitlements", "payment_timelines", "payment_ledger")));
    }
    public PaidQueryResponse read(String userId, Instant now) {
        require(TransactionSynchronizationManager.isActualTransactionActive());
        Document control = one("payment_recovery_controls", env().append("consumer", "billing"));
        require(control != null && Set.of("OPEN", "PURCHASE_BLOCKED").contains(control.getString("barrier"))
                && control.get("version") instanceof Long && control.getLong("version") > 0
                && Long.valueOf(4L).equals(control.get("schemaVersion")) && control.getString("epoch") != null);
        Document life = one("payment_account_lifecycles", env().append("userId", userId));
        Document account = one("payment_accounts", env().append("userId", userId));
        if (life == null) require(account == null);
        else require(Set.of("ACTIVE", "WITHDRAWN").contains(life.getString("state")));
        if (account != null) require(life != null && Set.of("ACTIVE", "WITHDRAWN").contains(account.getString("state")));
        String id = account == null ? null : account.getString("_id");
        boolean pending = id != null && exists("payment_sync_commands", env().append("accountId", id)
                .append("state", new Document("$in", List.of("PENDING", "REVIEW_REQUIRED", "BLOCKED_AUTH"))));
        if (life != null && "WITHDRAWN".equals(life.getString("state"))) return PaidQueryResponse.empty(pending);
        boolean unresolved = exists("payment_purchase_cases", env().append("accountId", null))
                || exists("payment_provider_inbox", env().append("accountId", null).append("state", "RECEIVED"));
        if (account == null) return unresolved || !"OPEN".equals(control.getString("barrier")) ? PaidQueryResponse.unknown(false) : PaidQueryResponse.empty(false);
        require("ACTIVE".equals(account.getString("state")));
        require(one("purchase_account_refs", env().append("_id", account.getString("activeRefId")).append("accountId", id).append("active", true)) != null);
        unresolved |= exists("payment_purchase_cases", env().append("accountId", id))
                || exists("payment_provider_inbox", env().append("accountId", id).append("state", "RECEIVED")
                        .append("normalizedObservation.kind", new Document("$in", List.of("REFUND", "REVOKE"))));
        if (unresolved) return PaidQueryResponse.unknown(pending);
        Document timeline = one("payment_timelines", new Document("_id", id));
        List<Document> slots = find("subscription_entitlements", new Document("accountId", id).append("endsAt", new Document("$gt", Date.from(now))), 1000);
        if (slots.isEmpty()) {
            // A future timeline without its slots is missing data, not an empty entitlement.
            require(timeline == null || timeline.getDate("timelineEnd") != null && !timeline.getDate("timelineEnd").toInstant().isAfter(now));
            return PaidQueryResponse.empty(pending);
        }
        require(timeline != null && environment.equals(timeline.getString("environment")) && id.equals(timeline.getString("accountId")));
        var ids = slots.stream().map(s -> s.getString("purchaseId")).toList();
        require(new HashSet<>(ids).size() == slots.size());
        List<Document> purchases = find("purchases", env().append("accountId", id).append("_id", new Document("$in", ids)), 1000);
        require(purchases.size() == new HashSet<>(ids).size());
        Map<String, Document> byId = new HashMap<>(); purchases.forEach(p -> byId.put(p.getString("_id"), p));
        List<Document> usable = new ArrayList<>(); Set<Long> sequences = new HashSet<>();
        for (Document slot : slots) {
            require(slot.get("sequence") instanceof Long && slot.getLong("sequence") > 0 && sequences.add(slot.getLong("sequence"))
                    && slot.get("durationSeconds") instanceof Long && slot.getLong("durationSeconds") > 0
                    && slot.getDate("startsAt") != null && slot.getDate("endsAt") != null && slot.getDate("startsAt").before(slot.getDate("endsAt")));
            require(timeline.get("nextSequence") instanceof Long && timeline.getLong("nextSequence") > slot.getLong("sequence")
                    && timeline.getDate("timelineEnd") != null && !timeline.getDate("timelineEnd").before(slot.getDate("endsAt")));
            Document purchase = byId.get(slot.getString("purchaseId")); require(purchase != null);
            String state = purchase.getString("purchaseState"), access = purchase.getString("accessState");
            require(Set.of("VERIFIED", "REFUNDED", "REVOKED").contains(state) && Set.of("ALLOWED", "NOT_GRANTED", "REVOKED").contains(access));
            if (!"VERIFIED".equals(state) || "REVOKED".equals(access) || slot.getDate("revokedAt") != null) continue;
            require("ALLOWED".equals(access));
            if (!"READY".equals(purchase.getString("processingStatus"))) return PaidQueryResponse.unknown(pending);
            Document product = purchase.get("productSnapshot", Document.class); require(product != null);
            try { require(PaymentOffer.valueOf(product.getString("offerCode")).durationSeconds == slot.getLong("durationSeconds")); }
            catch (RuntimeException invalid) { throw new EntitlementQueryException(EntitlementQueryException.Failure.STORAGE); }
            usable.add(slot);
        }
        usable.sort(Comparator.comparing(s -> s.getDate("startsAt")));
        for (int i = 1; i < usable.size(); i++) require(!usable.get(i).getDate("startsAt").before(usable.get(i - 1).getDate("endsAt")));
        Document active = null; Instant next = null, continuous = null;
        for (Document slot : usable) {
            Instant start = slot.getDate("startsAt").toInstant(), end = slot.getDate("endsAt").toInstant();
            if (!start.isAfter(now) && end.isAfter(now)) { require(active == null); active = slot; continuous = end; }
            else if (start.isAfter(now)) {
                if (next == null) next = start;
                if (continuous != null && continuous.equals(start)) continuous = end;
            }
        }
        if (active == null) return new PaidQueryResponse(PaidQueryResponse.Status.READY, false, null, null, null, next, pending);
        String offer = byId.get(active.getString("purchaseId")).get("productSnapshot", Document.class).getString("offerCode");
        return new PaidQueryResponse(PaidQueryResponse.Status.READY, true, offer, active.getDate("endsAt").toInstant(), continuous, next, pending);
    }
    private Document env() { return new Document("environment", environment); }
    private boolean exists(String collection, Document query) { return !mongo.find(new BasicQuery(query).limit(1).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, collection).isEmpty(); }
    private Document one(String collection, Document query) { List<Document> rows = find(collection, query, 1); return rows.isEmpty() ? null : rows.getFirst(); }
    private List<Document> find(String collection, Document query, int cap) {
        List<Document> rows = mongo.find(new BasicQuery(query).limit(cap + 1).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, collection);
        if (rows.size() > cap) throw new EntitlementQueryException(EntitlementQueryException.Failure.LIMIT); return rows;
    }
}
