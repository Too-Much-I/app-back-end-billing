package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.ClientSession;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.provider.PurchaseReferenceSchema;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Explicit, bounded maintenance. Financial rows and account identity connections are never deleted here. */
public final class PaymentReferenceRetentionWorker {
    public record Tick(int inspected, int eligible, int deleted, int leasesReleased, int failed) {}
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final Clock clock;
    private final String environment;
    private final boolean enabled;
    private String afterReference, afterCursor;

    public PaymentReferenceRetentionWorker(PaymentStore store, PaymentProperties p, Clock clock, boolean enabled) {
        require(p.getRecovery().isFenceRequired());
        this.store = store; this.fence = new PaymentRecoveryFence(store, p); this.clock = clock;
        this.environment = p.getEnvironment(); this.enabled = enabled;
        PurchaseCoreSchema.verify(store.database()); PurchaseReferenceSchema.verify(store.database());
        PaymentAccountLookupSchema.verify(store.database()); PaymentSyncSchema.verify(store.database());
    }

    public synchronized Tick tick(int batch) {
        require(batch >= 1 && batch <= 100);
        var ticket = fence.capture(); Instant now = clock.instant();
        var references = store.database().getCollection(PurchaseReferenceSchema.COLLECTION);
        Document due = new Document("environment", environment).append("retentionExpiresAt", new Document("$lte", Date.from(now)));
        if (afterReference != null) due.append("_id", new Document("$gt", afterReference));
        var rows = references.find(due).sort(new Document("_id", 1)).limit(batch).into(new ArrayList<>());
        int eligible = 0, deleted = 0, released = 0, failed = 0;
        for (Document row : rows) {
            try {
                boolean ready = store.transaction(s -> {
                    if (enabled) fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PURGE);
                    else fence.check(s, ticket, PaymentRecoveryFence.Writer.PURGE);
                    Document current = references.find(s, new Document("_id", row.get("_id")).append("version", row.get("version"))).first();
                    if (current == null || !canPurge(s, current, now)) return false;
                    if (enabled) require(references.deleteOne(s, new Document("_id", current.get("_id"))
                            .append("version", current.get("version"))).getDeletedCount() == 1);
                    return true;
                });
                if (ready) { eligible++; if (enabled) deleted++; }
            } catch (RuntimeException unavailable) { failed++; }
        }
        // Sweep past held rows too; one unresolved item must not starve later candidates.
        afterReference = rows.size() < batch ? null : rows.getLast().getString("_id");
        var cursors = store.database().getCollection(PaymentAccountLookupSchema.COLLECTION);
        Document expired = new Document("kind", "ACCOUNT_LOOKUP").append("lookup.owner", new Document("$type", "string"))
                .append("lookup.until", new Document("$type", "date").append("$lte", Date.from(now)));
        if (afterCursor != null) expired.append("_id", new Document("$gt", afterCursor));
        var leases = cursors.find(expired).sort(new Document("_id", 1)).limit(batch).into(new ArrayList<>());
        for (Document row : leases) {
            try {
                boolean changed = store.transaction(s -> {
                    if (enabled) fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PURGE);
                    else fence.check(s, ticket, PaymentRecoveryFence.Writer.PURGE);
                    Document account = store.database().getCollection("payment_accounts").find(s,
                            new Document("_id", row.get("accountId")).append("environment", environment)).first();
                    if (account == null) return false;
                    Document exact = new Document("_id", row.get("_id")).append("version", row.get("version"))
                            .append("lookup.owner", row.get("lookup", Document.class).get("owner"))
                            .append("lookup.until", new Document("$lte", Date.from(now)));
                    Document current = cursors.find(s, exact).first(); if (current == null) return false;
                    require(current.getLong("version") < Long.MAX_VALUE && current.get("lookup", Document.class).getLong("leaseVersion") < Long.MAX_VALUE);
                    if (enabled) require(cursors.updateOne(s, exact, new Document("$set", new Document("lookup.owner", null).append("lookup.until", null))
                            .append("$inc", new Document("version", 1L).append("lookup.leaseVersion", 1L))).getModifiedCount() == 1);
                    return true;
                });
                if (changed && enabled) released++;
            } catch (RuntimeException unavailable) { failed++; }
        }
        afterCursor = leases.size() < batch ? null : leases.getLast().getString("_id");
        return new Tick(rows.size(), eligible, deleted, released, failed);
    }

    private boolean canPurge(ClientSession s, Document reference, Instant now) {
        Document purchase = store.database().getCollection("purchases").find(s, new Document("_id", reference.get("_id"))
                .append("environment", environment)).first();
        if (purchase == null || purchase.get("money") == null || !"READY".equals(purchase.getString("processingStatus"))
                || !expired(purchase.getDate("retentionExpiresAt"), now)) return false;
        for (String field : List.of("accountId", "store", "appId", "transactionKey", "lookupKeyVersion"))
            if (!Objects.equals(purchase.get(field), reference.get(field))) return false;
        String accountId = purchase.getString("accountId");
        Document account = store.database().getCollection("payment_accounts").find(s, new Document("_id", accountId).append("environment", environment)).first();
        if (account == null) return false;
        Document life = store.database().getCollection("payment_account_lifecycles").find(s,
                new Document("environment", environment).append("userId", account.get("userId"))).first();
        if (life == null || !Set.of("ACTIVE", "WITHDRAWN").contains(life.getString("state")) || !Long.valueOf(0).equals(life.get("pendingEvidence"))) return false;
        Document key = new Document("environment", environment).append("accountId", accountId);
        Document related = new Document("environment", environment).append("$or", List.of(new Document("accountId", accountId),
                new Document("normalizedObservation.transactionKey", purchase.get("transactionKey"))));
        if (exists(s, "payment_purchase_cases", related) || exists(s, PaymentSyncSchema.COLLECTION, new Document(key)
                .append("state", new Document("$nin", List.of("APPLIED", "REJECTED"))))
                || exists(s, "payment_provider_inbox", new Document(related).append("state", "RECEIVED"))) return false;
        Document cursor = store.database().getCollection(PaymentAccountLookupSchema.COLLECTION).find(s, new Document("accountId", accountId)).first();
        if (cursor == null) return false;
        Document work = cursor.get("lookup", Document.class);
        if (work != null && (work.getString("owner") != null || work.get("cursor") != null
                || work.getString("failure") != null && !"IDLE".equals(work.getString("failure")))) return false;
        // Check current rights and ledger independently of a stale cached deadline. Unknown future entry types hold.
        Document purchaseKey = new Document("purchaseId", purchase.get("_id"));
        Document right = store.database().getCollection("subscription_entitlements").find(s, purchaseKey).first();
        if (right == null || !fiveYearsElapsed(right.getDate("endsAt"), now)
                || right.getDate("revokedAt") != null && !fiveYearsElapsed(right.getDate("revokedAt"), now)) return false;
        if (exists(s, "payment_ledger", new Document(purchaseKey).append("type",
                new Document("$nin", List.of("PURCHASE_OBSERVED", "PURCHASE_VERIFIED", "MONEY_CONFIRMED"))))) return false;
        Document latest = store.database().getCollection("payment_ledger").find(s, purchaseKey).sort(new Document("occurredAt", -1)).first();
        if (latest == null || !fiveYearsElapsed(latest.getDate("occurredAt"), now)) return false;
        return true;
    }
    private boolean exists(ClientSession s, String collection, Document key) { return store.database().getCollection(collection).find(s, key).limit(1).first() != null; }
    private boolean expired(Date date, Instant now) { return date != null && !date.toInstant().isAfter(now); }
    private boolean fiveYearsElapsed(Date date, Instant now) { return date != null && !date.toInstant().atZone(ZoneOffset.UTC).plusYears(5).toInstant().isAfter(now); }
}
