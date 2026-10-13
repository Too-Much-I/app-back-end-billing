package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.PaymentOffer;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Internal verified-evidence core. Deliberately NOT a Spring bean, HTTP adapter or scheduler. */
public final class PurchaseCore {
    public enum Outcome { RECEIVED, APPLIED, REVIEW_REQUIRED, PURCHASE_BLOCKED }
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final Clock clock;
    private final String environment, keyVersion;
    private final Map<PurchaseObservation.Store, String> apps;

    public PurchaseCore(PaymentStore store, PaymentProperties properties, Clock clock,
                        Map<PurchaseObservation.Store, String> apps, String keyVersion) {
        require(properties.getRecovery().isFenceRequired());
        this.store = store; this.fence = new PaymentRecoveryFence(store, properties); this.clock = clock;
        environment = properties.getEnvironment();
        require(Set.of("SANDBOX", "PRODUCTION").contains(environment));
        PurchaseObservation.label(keyVersion); this.keyVersion = keyVersion;
        this.apps = Map.copyOf(apps); require(!this.apps.isEmpty());
        this.apps.values().forEach(PurchaseObservation::text);
        PurchaseCoreSchema.verify(store.database());
        // A version change is a maintenance migration, never another uniqueness namespace.
        require(collection("purchases").find(new Document("environment", environment)
                .append("lookupKeyVersion", new Document("$ne", keyVersion))).limit(1).first() == null);
        for (String source : List.of("payment_provider_inbox", "payment_purchase_cases"))
            require(collection(source).find(new Document("environment", environment)
                    .append("normalizedObservation.lookupKeyVersion", new Document("$ne", keyVersion))).limit(1).first() == null);
    }

    public Outcome receive(PurchaseObservation observation) {
        require(environment.equals(observation.environment()) && keyVersion.equals(observation.lookupKeyVersion())
                && observation.appId().equals(apps.get(observation.store())));
        var ticket = fence.capture();
        return store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.PAYMENT_INPUT);
            Document key = sourceKey(observation.providerEventKey());
            Document previous = collection("payment_provider_inbox").find(session, key).first();
            if (previous == null) previous = collection("payment_purchase_cases").find(session, key).first();
            if (previous != null) {
                if (!observation.digest().equals(previous.getString("digest")))
                    throw new PaymentException(409, "EVENT_ID_CONFLICT");
                return outcome(previous);
            }
            Document account = resolveAccount(session, observation.ownerRefId());
            Document lifecycle = account == null ? null : lifecycle(session, account);
            if (lifecycle != null) touch(session, lifecycle.getString("_id"), 1);
            Instant now = clock.instant();
            Document row = new Document(key).append("_id", uuid()).append("digestVersion", 1L)
                    .append("digest", observation.digest()).append("normalizedObservation", observation.body())
                    .append("accountId", account == null ? null : account.getString("_id"))
                    .append("lifecycleId", lifecycle == null ? null : lifecycle.getString("_id"))
                    .append("counted", lifecycle != null).append("state", "RECEIVED").append("version", 1L)
                    .append("attempts", 0L).append("receivedAt", Date.from(now))
                    .append("retentionDeadline", Date.from(now.plus(Duration.ofDays(120))));
            collection("payment_provider_inbox").insertOne(session, row);
            return Outcome.RECEIVED;
        });
    }

    public Outcome apply(String eventKey) {
        PurchaseObservation.hex(eventKey);
        var ticket = fence.capture();
        return store.transaction(session -> {
            var barrier = fence.acquire(session, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            Document row = collection("payment_provider_inbox").find(session, sourceKey(eventKey)).first();
            if (row == null) {
                require(collection("payment_purchase_cases").find(session, sourceKey(eventKey)).first() != null);
                return Outcome.REVIEW_REQUIRED;
            }
            if (!"RECEIVED".equals(row.getString("state"))) return outcome(row);
            PurchaseObservation o = PurchaseObservation.read(eventKey, row.get("normalizedObservation", Document.class));
            require(o.digest().equals(row.getString("digest")) && keyVersion.equals(o.lookupKeyVersion())
                    && environment.equals(o.environment()) && o.appId().equals(apps.get(o.store())));
            Document account = resolveAccount(session, o.ownerRefId());
            // Frozen attribution cannot be repaired using a new account or a guessed user ID.
            if (row.getString("accountId") == null) return review(session, row, "OWNER_UNRESOLVED");
            require(account != null && row.getString("accountId").equals(account.getString("_id")));
            Document life = lifecycle(session, account);
            require(life.getString("_id").equals(row.getString("lifecycleId")) && row.getBoolean("counted"));
            Document purchase = collection("purchases").find(session, transactionKey(o)).first();
            if (purchase != null && (!Objects.equals(purchase.getString("accountId"), account.getString("_id"))
                    || !Objects.equals(purchase.getString("ownerRefId"), o.ownerRefId())))
                return review(session, row, "OWNER_CONFLICT");

            if (o.kind() != PurchaseObservation.Kind.PURCHASE) {
                // First-seen reversal fences later purchase delivery. Existing grants need PLAN-012, not a partial revoke here.
                if (purchase == null) {
                    purchase = observedPurchase(o, account, null, o.kind() == PurchaseObservation.Kind.REFUND ? "REFUNDED" : "REVOKED");
                    purchase.put("accessState", "REVOKED");
                    purchase.put("processingStatus", "REVIEW_REQUIRED");
                    collection("purchases").insertOne(session, purchase);
                    ledger(session, purchase, "PURCHASE_OBSERVED", null, o, new Document("kind", o.kind().name()));
                }
                return review(session, row, "REVERSAL_REQUIRES_PLAN_012");
            }
            if (purchase != null && !"VERIFIED".equals(purchase.getString("purchaseState")))
                return review(session, row, "TRANSACTION_ALREADY_REVERSED");
            if (collection("payment_purchase_cases").find(session, new Document("environment", environment)
                    .append("normalizedObservation.store", o.store().name()).append("normalizedObservation.appId", o.appId())
                    .append("normalizedObservation.transactionKey", o.transactionKey())
                    .append("normalizedObservation.kind", new Document("$in", List.of("REFUND", "REVOKE")))).first() != null)
                return review(session, row, "TRANSACTION_REVERSAL_UNRESOLVED");
            if (purchase != null && !samePurchase(purchase, o)) return review(session, row, "IMMUTABLE_FACT_CONFLICT");
            if (purchase != null && "ALLOWED".equals(purchase.getString("accessState"))) {
                require(collection("subscription_entitlements").find(session, new Document("purchaseId", purchase.getString("_id"))).first() != null);
                return complete(session, row);
            }
            Document product = purchase == null ? product(session, o) : purchase.get("productSnapshot", Document.class);
            if (o.purchasedAt() == null || o.purchasedAt().isAfter(clock.instant()) || product == null)
                return review(session, row, "PURCHASE_FACTS_INCOMPLETE");
            if (purchase == null) {
                purchase = observedPurchase(o, account, product, "VERIFIED");
                collection("purchases").insertOne(session, purchase);
                ledger(session, purchase, "PURCHASE_OBSERVED", null, o, new Document("money", o.body().get("money")));
            }
            boolean active = "ACTIVE".equals(life.getString("state")) && "ACTIVE".equals(account.getString("state"))
                    && o.ownerRefId().equals(account.getString("activeRefId")) && activeReference(session, o.ownerRefId());
            if (!active || "REVIEW_REQUIRED".equals(purchase.getString("processingStatus"))) {
                updatePurchase(session, purchase, new Document("processingStatus", "REVIEW_REQUIRED"));
                return review(session, row, "OWNER_NOT_ACTIVE");
            }
            if (barrier == PaymentRecoveryFence.Barrier.PURCHASE_BLOCKED) {
                touch(session, row.getString("lifecycleId"), 0);
                return Outcome.PURCHASE_BLOCKED;
            }
            append(session, purchase, product, o);
            return complete(session, row);
        });
    }

    /** Explicit pre-TTL handoff. Scheduling and case resolution are separate, still disabled. */
    public Outcome handoffAged(String eventKey) {
        PurchaseObservation.hex(eventKey);
        var ticket = fence.capture();
        return store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            Document row = collection("payment_provider_inbox").find(session, sourceKey(eventKey)).first();
            if (row == null) {
                require(collection("payment_purchase_cases").find(session, sourceKey(eventKey)).first() != null);
                return Outcome.REVIEW_REQUIRED;
            }
            if (!"RECEIVED".equals(row.getString("state"))) return outcome(row);
            require(!clock.instant().isBefore(row.getDate("receivedAt").toInstant().plus(Duration.ofDays(119))));
            return review(session, row, "RETENTION_HANDOFF");
        });
    }

    private void append(ClientSession session, Document purchase, Document product, PurchaseObservation o) {
        String accountId = purchase.getString("accountId");
        var timelines = collection("payment_timelines");
        Document timeline = timelines.find(session, new Document("_id", accountId)).first();
        long sequence = timeline == null ? 1L : positive(timeline, "nextSequence");
        require(sequence < Long.MAX_VALUE);
        Instant start = o.purchasedAt();
        if (timeline != null) {
            require(environment.equals(timeline.getString("environment")) && accountId.equals(timeline.getString("accountId")));
            Date end = timeline.getDate("timelineEnd");
            if (end != null && end.toInstant().isAfter(start)) start = end.toInstant();
        }
        Instant end;
        try { end = start.plusSeconds(positive(product, "durationSeconds")); Date.from(end); }
        catch (RuntimeException invalid) { throw PaymentException.unavailable(); }
        String entitlementId = uuid();
        Document entitlement = new Document("_id", entitlementId).append("purchaseId", purchase.getString("_id"))
                .append("accountId", accountId).append("sequence", sequence).append("durationSeconds", product.getLong("durationSeconds"))
                .append("originalStartsAt", Date.from(start)).append("originalEndsAt", Date.from(end))
                .append("startsAt", Date.from(start)).append("endsAt", Date.from(end)).append("revokedAt", null).append("version", 1L);
        collection("subscription_entitlements").insertOne(session, entitlement);
        Document next = new Document("_id", accountId).append("accountId", accountId).append("environment", environment)
                .append("nextSequence", sequence + 1).append("timelineEnd", Date.from(end))
                .append("version", timeline == null ? 1L : increment(timeline, "version"));
        if (timeline == null) timelines.insertOne(session, next);
        else require(timelines.replaceOne(session, versionKey(timeline), next).getModifiedCount() == 1);
        updatePurchase(session, purchase, new Document("accessState", "ALLOWED").append("processingStatus", "READY")
                .append("retentionExpiresAt", Date.from((end.isAfter(clock.instant()) ? end : clock.instant())
                        .atOffset(ZoneOffset.UTC).plusYears(5).toInstant())));
        ledger(session, purchase, "PURCHASE_VERIFIED", entitlementId, o,
                new Document("sequence", sequence).append("startsAt", Date.from(start)).append("endsAt", Date.from(end))
                        .append("productSnapshot", product));
    }

    private Document observedPurchase(PurchaseObservation o, Document account, Document product, String state) {
        Date now = Date.from(clock.instant());
        return new Document(transactionKey(o)).append("_id", uuid()).append("lookupKeyVersion", keyVersion)
                .append("accountId", account.getString("_id")).append("ownerRefId", o.ownerRefId())
                .append("storeProductId", o.storeProductId()).append("purchasedAt", o.body().get("purchasedAt"))
                .append("productSnapshot", product).append("money", o.body().get("money"))
                .append("purchaseState", state).append("accessState", "NOT_GRANTED").append("processingStatus", "NEEDS_LOOKUP")
                .append("version", 1L).append("firstVerifiedAt", now).append("lastVerifiedAt", now).append("retentionExpiresAt", null);
    }
    private static boolean samePurchase(Document purchase, PurchaseObservation o) {
        Document incoming = o.body();
        return Objects.equals(purchase.get("purchasedAt"), incoming.get("purchasedAt"))
                && Objects.equals(purchase.get("storeProductId"), o.storeProductId())
                && Objects.equals(purchase.get("money"), incoming.get("money"));
    }
    private Document product(ClientSession session, PurchaseObservation o) {
        if (o.storeProductId() == null) return null;
        var rows = collection("payment_products").find(session, new Document("environment", environment)
                .append("store", o.store().name()).append("appId", o.appId()).append("storeProductId", o.storeProductId()))
                .limit(2).into(new ArrayList<>());
        if (rows.size() != 1) return null;
        Document p = rows.getFirst();
        try {
            PaymentOffer offer = PaymentOffer.valueOf(p.getString("offerCode"));
            Object duration = p.get("durationSeconds");
            require((duration instanceof Long || duration instanceof Integer) && ((Number) duration).longValue() == offer.durationSeconds);
            Object catalog = p.get("catalogVersion");
            require((catalog instanceof Long || catalog instanceof Integer) && ((Number) catalog).longValue() > 0);
            PurchaseObservation.text(p.getString("rcProductId"));
            return new Document("offerCode", offer.name()).append("durationSeconds", offer.durationSeconds)
                    .append("catalogVersion", ((Number) catalog).longValue()).append("storeProductId", o.storeProductId())
                    .append("rcProductId", p.getString("rcProductId"));
        } catch (RuntimeException invalid) { return null; }
    }
    private void ledger(ClientSession session, Document purchase, String type, String entitlementId,
                        PurchaseObservation o, Document detail) {
        collection("payment_ledger").insertOne(session, new Document("_id", uuid())
                .append("effectKey", purchase.getString("_id") + ":" + type).append("type", type)
                .append("purchaseId", purchase.getString("_id")).append("entitlementId", entitlementId)
                .append("occurredAt", Date.from(clock.instant())).append("sourceKind", "VERIFIED_OBSERVATION")
                .append("sourceObservationKey", o.providerEventKey()).append("detail", detail));
    }
    private Outcome complete(ClientSession session, Document row) {
        require(row.getBoolean("counted"));
        touch(session, row.getString("lifecycleId"), -1);
        finishSource(session, row, "APPLIED");
        return Outcome.APPLIED;
    }
    private Outcome review(ClientSession session, Document row, String reason) {
        Document review = new Document(row);
        review.remove("retentionDeadline"); review.remove("attempts");
        review.put("_id", uuid()); review.put("state", "REVIEW_REQUIRED"); review.put("version", 1L);
        review.put("reason", reason); review.put("createdAt", Date.from(clock.instant()));
        collection("payment_purchase_cases").insertOne(session, review);
        if (row.getBoolean("counted")) touch(session, row.getString("lifecycleId"), 0);
        finishSource(session, row, "QUARANTINED");
        return Outcome.REVIEW_REQUIRED;
    }
    private void finishSource(ClientSession session, Document row, String state) {
        require(collection("payment_provider_inbox").updateOne(session, versionKey(row), new Document("$set",
                new Document("state", state).append("counted", false).append("version", increment(row, "version"))
                        .append("purgeAt", row.getDate("retentionDeadline")))).getModifiedCount() == 1);
    }
    private void updatePurchase(ClientSession session, Document purchase, Document changes) {
        changes.append("version", increment(purchase, "version")).append("lastVerifiedAt", Date.from(clock.instant()));
        require(collection("purchases").updateOne(session, versionKey(purchase), new Document("$set", changes)).getModifiedCount() == 1);
    }
    private Document resolveAccount(ClientSession session, String reference) {
        if (reference == null) return null;
        Document ref = collection("purchase_account_refs").find(session, new Document("_id", reference).append("environment", environment)).first();
        if (ref == null) return null;
        Document account = collection("payment_accounts").find(session, new Document("_id", ref.getString("accountId"))
                .append("environment", environment)).first();
        require(account != null);
        return account;
    }
    private boolean activeReference(ClientSession session, String reference) {
        Document ref = collection("purchase_account_refs").find(session, new Document("_id", reference).append("environment", environment)).first();
        return ref != null && Boolean.TRUE.equals(ref.getBoolean("active"));
    }
    private Document lifecycle(ClientSession session, Document account) {
        Document life = collection("payment_account_lifecycles").find(session, new Document("environment", environment)
                .append("userId", account.getString("userId"))).first();
        require(life != null && Set.of("ACTIVE", "WITHDRAWN").contains(life.getString("state")));
        return life;
    }
    private void touch(ClientSession session, String lifecycleId, long delta) {
        require(lifecycleId != null);
        var lifecycles = collection("payment_account_lifecycles");
        Document life = lifecycles.find(session, new Document("_id", lifecycleId).append("environment", environment)).first();
        require(life != null && life.get("pendingEvidence") instanceof Long);
        long pending = life.getLong("pendingEvidence");
        require(pending >= 0 && (delta != -1 || pending > 0) && (delta != 1 || pending < Long.MAX_VALUE));
        require(lifecycles.updateOne(session, versionKey(life).append("pendingEvidence", pending), new Document("$set",
                new Document("version", increment(life, "version")).append("pendingEvidence", pending + delta))).getModifiedCount() == 1);
    }
    private Document sourceKey(String event) { return new Document("environment", environment).append("providerEventKey", event); }
    private static Document transactionKey(PurchaseObservation o) {
        return new Document("environment", o.environment()).append("store", o.store().name())
                .append("appId", o.appId()).append("transactionKey", o.transactionKey());
    }
    private static Outcome outcome(Document row) {
        return switch (row.getString("state")) {
            case "RECEIVED" -> Outcome.RECEIVED;
            case "APPLIED" -> Outcome.APPLIED;
            case "QUARANTINED", "REVIEW_REQUIRED" -> Outcome.REVIEW_REQUIRED;
            default -> throw PaymentException.unavailable();
        };
    }
    private static long positive(Document d, String field) { require(d.get(field) instanceof Long && d.getLong(field) > 0); return d.getLong(field); }
    private static long increment(Document d, String field) { long n = positive(d, field); require(n < Long.MAX_VALUE); return n + 1; }
    private static Document versionKey(Document row) { return new Document("_id", row.get("_id")).append("version", positive(row, "version")); }
    private MongoCollection<Document> collection(String name) { return store.database().getCollection(name); }
    private static String uuid() { return UUID.randomUUID().toString(); }
}
