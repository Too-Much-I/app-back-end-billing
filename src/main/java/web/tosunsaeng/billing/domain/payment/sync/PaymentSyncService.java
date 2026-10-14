package web.tosunsaeng.billing.domain.payment.sync;

import com.mongodb.client.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Stable sync contract; shared by the default-OFF provider worker and public API binding. */
public class PaymentSyncService {
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final String environment;
    private final Map<PurchaseObservation.Store, String> apps;
    private final PurchaseLookupKey lookup;
    private final Clock clock;
    public PaymentSyncService(PaymentStore store, PaymentProperties p, Clock clock,
                              PurchaseLookupKey lookup, Map<PurchaseObservation.Store, String> apps) {
        require(p.getRecovery().isFenceRequired() && Set.of("SANDBOX", "PRODUCTION").contains(p.getEnvironment()));
        this.store = store; fence = new PaymentRecoveryFence(store, p); environment = p.getEnvironment();
        this.clock = clock; this.lookup = lookup; this.apps = Map.copyOf(apps); require(!apps.isEmpty());
        PurchaseCoreSchema.verify(store.database()); PaymentSyncSchema.verify(store.database());
        for (String name : List.of("purchases", PaymentSyncSchema.COLLECTION))
            require(c(name).find(new Document("environment", environment).append("lookupKeyVersion", new Document("$ne", lookup.version()))).first() == null);
        for (String name : List.of("payment_provider_inbox", "payment_purchase_cases"))
            require(c(name).find(new Document("environment", environment).append("normalizedObservation", new Document("$ne", null))
                    .append("normalizedObservation.lookupKeyVersion", new Document("$ne", lookup.version()))).first() == null);
        for (String name : List.of("payment_provider_inbox", "payment_purchase_cases"))
            require(c(name).find(new Document("environment", environment).append("receptionDigest", new Document("$exists", true))
                    .append("receptionLookupKeyVersion", new Document("$ne", lookup.version()))).first() == null);
    }
    public PaymentSyncResult sync(String userId, Instant authorizationDeadline, PaymentSyncRequest request) {
        canonicalUser(userId);
        String app = apps.get(request.store());
        if (app == null) throw new PaymentException(422, "UNSUPPORTED_CONTRACT");
        String tx = lookup.transaction(environment, request.store().name(), app, request.transactionId());
        String digest = digest(new Document("environment", environment).append("store", request.store().name()).append("appId", app).append("transactionKey", tx));
        var ticket = fence.capture();
        return store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_INPUT);
            authorized(authorizationDeadline);
            Document life = c("payment_account_lifecycles").find(s, userKey(userId)).first();
            Document account = c("payment_accounts").find(s, userKey(userId)).first();
            if (life != null && "WITHDRAWN".equals(life.getString("state")) || account != null && "WITHDRAWN".equals(account.getString("state")))
                throw new PaymentException(403, "PAYMENT_ACCOUNT_WITHDRAWN");
            if (account == null && life == null) throw new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED");
            require(account != null && life != null && "ACTIVE".equals(life.getString("state")) && "ACTIVE".equals(account.getString("state")));
            Document ref = c("purchase_account_refs").find(s, new Document("_id", account.getString("activeRefId"))
                    .append("environment", environment).append("accountId", account.getString("_id")).append("active", true)).first();
            require(ref != null);
            Document key = userKey(userId).append("operationId", request.operationId());
            Document old = commands().find(s, key).first();
            if (old != null) {
                if (!digest.equals(old.getString("payloadDigest"))) throw new PaymentException(409, "IDEMPOTENCY_KEY_CONFLICT");
                require(account.getString("_id").equals(old.getString("accountId")) && life.getString("_id").equals(old.getString("lifecycleId")));
                authorized(authorizationDeadline);
                return result(s, old);
            }
            Document row = new Document(key).append("_id", UUID.randomUUID().toString()).append("store", request.store().name()).append("appId", app)
                    .append("transactionKey", tx).append("lookupKeyVersion", lookup.version()).append("payloadDigest", digest).append("digestVersion", 1L)
                    .append("accountId", account.getString("_id")).append("lifecycleId", life.getString("_id"))
                    .append("state", "PENDING").append("counted", true).append("version", 1L).append("attempts", 0L)
                    .append("purchaseId", null).append("errorCode", null).append("sourceObservationKey", null)
                    .append("createdAt", Date.from(clock.instant())).append("updatedAt", Date.from(clock.instant()));
            Document purchase = purchase(s, row);
            if (purchase != null && !row.getString("accountId").equals(purchase.getString("accountId")))
                throw new PaymentException(409, "PURCHASE_BINDING_CONFLICT");
            if (purchase != null && settled(s, purchase, row)) {
                row.put("state", "APPLIED"); row.put("counted", false); row.put("purchaseId", purchase.getString("_id"));
            } else {
                Document cursor = c("payment_reconcile_cursors").find(s, new Document("accountId", account.getString("_id")).append("kind", "ACCOUNT_LOOKUP")).first();
                Document work = cursor == null ? null : cursor.get("lookup", Document.class);
                String failure = work == null ? null : work.getString("failure");
                if (failure != null && Set.of("BLOCKED_AUTH", "PROVIDER_REVIEW", "PROVIDER_CONTRACT", "PAGE_LIMIT").contains(failure)) {
                    row.put("state", "BLOCKED_AUTH".equals(failure) ? "BLOCKED_AUTH" : "REVIEW_REQUIRED"); row.put("errorCode", failure);
                }
            }
            touch(s, row, row.getBoolean("counted") ? 1 : 0);
            commands().insertOne(s, row); authorized(authorizationDeadline);
            return result(s, row);
        });
    }

    /** Worker captures this once before dispatch, not again during retry/completion. */
    public PaymentRecoveryFence.Ticket capture() { return fence.capture(); }

    /** Only an already durable, verified source may be bound. No observation is created here. */
    public long bindObservation(String commandId, long expectedVersion, String eventKey, PaymentRecoveryFence.Ticket ticket) {
        return bindObservation(commandId, expectedVersion, eventKey, ticket, s -> {});
    }
    public long bindObservation(String commandId, long expectedVersion, String eventKey, PaymentRecoveryFence.Ticket ticket,
                                java.util.function.Consumer<ClientSession> guard) {
        return store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            guard.accept(s);
            Document row = command(s, commandId); require("PENDING".equals(row.getString("state")) && row.getBoolean("counted"));
            require(row.getLong("version") == expectedVersion);
            Document source = source(s, eventKey); matchSource(row, source);
            require(row.getString("sourceObservationKey") == null || eventKey.equals(row.getString("sourceObservationKey")));
            if (eventKey.equals(row.getString("sourceObservationKey"))) return expectedVersion;
            update(s, row, new Document("sourceObservationKey", eventKey)); touch(s, row, 0);
            return expectedVersion + 1;
        });
    }
    /** Completes the command only, never pays again. A withdrawal does not discard financial work. */
    public void complete(String commandId, long expectedVersion, PaymentRecoveryFence.Ticket ticket) {
        complete(commandId, expectedVersion, ticket, s -> {});
    }
    public void complete(String commandId, long expectedVersion, PaymentRecoveryFence.Ticket ticket,
                         java.util.function.Consumer<ClientSession> guard) {
        store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            guard.accept(s);
            Document row = command(s, commandId);
            if ("APPLIED".equals(row.getString("state"))) { require(!row.getBoolean("counted")); return null; }
            require(row.getLong("version") == expectedVersion && row.getBoolean("counted") && row.getString("sourceObservationKey") != null);
            Document source = source(s, row.getString("sourceObservationKey")); matchSource(row, source);
            String state = source.getString("state");
            if (Set.of("QUARANTINED", "REVIEW_REQUIRED").contains(state)) {
                if (!"REVIEW_REQUIRED".equals(row.getString("state"))) {
                    update(s, row, new Document("state", "REVIEW_REQUIRED")); touch(s, row, 0);
                }
                return null;
            }
            if (!"APPLIED".equals(state)) return null;
            Document p = purchase(s, row);
            require(p != null && row.getString("accountId").equals(p.getString("accountId")));
            if (!settled(s, p, row)) return null;
            update(s, row, new Document("state", "APPLIED").append("counted", false).append("purchaseId", p.getString("_id")));
            touch(s, row, -1); return null;
        });
    }
    private PaymentSyncResult result(ClientSession s, Document row) {
        String state = row.getString("state");
        if ("REJECTED".equals(state)) throw new PaymentException(409, "PURCHASE_BINDING_CONFLICT");
        if (!"APPLIED".equals(state)) return PaymentSyncResult.pending(row.getString("operationId"), "REVIEW_REQUIRED".equals(state));
        Document p = purchase(s, row);
        require(p != null && row.getString("purchaseId").equals(p.getString("_id")) && row.getString("accountId").equals(p.getString("accountId")));
        if (!settled(s, p, row)) return PaymentSyncResult.pending(row.getString("operationId"), true);
        return new PaymentSyncResult(row.getString("operationId"), PaymentSyncResult.Status.APPLIED, p.getString("_id"), p.getString("purchaseState"), null);
    }

    public enum LookupFailure { BLOCKED_AUTH, RETRY_EXHAUSTED, PROVIDER_REVIEW, PROVIDER_CONTRACT, PAGE_LIMIT }
    /** Durable financial evidence outlives inbox TTL. Never creates a purchase or grants entitlement. */
    public boolean completeVerifiedPurchase(String commandId, long expectedVersion, PaymentRecoveryFence.Ticket ticket,
                                           java.util.function.Consumer<ClientSession> guard) {
        return store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard.accept(s);
            Document row = command(s, commandId);
            if ("APPLIED".equals(row.getString("state"))) { require(!row.getBoolean("counted")); return true; }
            require(row.getLong("version") == expectedVersion && "PENDING".equals(row.getString("state")) && row.getBoolean("counted"));
            Document p = purchase(s, row);
            if (p == null || !Objects.equals(p.getString("accountId"), row.getString("accountId"))
                    || !"VERIFIED".equals(p.getString("purchaseState")) || !"READY".equals(p.getString("processingStatus"))) return false;
            Document account = c("payment_accounts").find(s, new Document("_id", row.getString("accountId"))
                    .append("environment", environment).append("userId", row.getString("userId"))).first();
            require(account != null);
            Document cases = new Document("environment", environment);
            for (String field : List.of("store", "appId", "transactionKey")) cases.append("normalizedObservation." + field, row.get(field));
            if (c("payment_purchase_cases").find(s, cases).limit(1).first() != null || !settled(s, p, row)) return false;
            update(s, row, new Document("state", "APPLIED").append("counted", false).append("purchaseId", p.getString("_id")));
            touch(s, row, -1); return true;
        });
    }
    public static Instant retryStartedAt(Document row) {
        Date date = row.getDate("retryStartedAt");
        return (date == null ? row.getDate("createdAt") : date).toInstant();
    }
    /** Operational handoff only: never releases pending evidence or changes a purchase. */
    public void handoffLookup(String commandId, long expectedVersion, LookupFailure reason, PaymentRecoveryFence.Ticket ticket,
                              java.util.function.Consumer<ClientSession> guard) {
        Objects.requireNonNull(reason);
        store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard.accept(s);
            Document row = command(s, commandId);
            if (!"PENDING".equals(row.getString("state"))) return null;
            require(row.getLong("version") == expectedVersion && row.getBoolean("counted"));
            update(s, row, new Document("state", reason == LookupFailure.BLOCKED_AUTH ? "BLOCKED_AUTH" : "REVIEW_REQUIRED")
                    .append("errorCode", reason.name()));
            touch(s, row, 0); return null;
        });
    }
    private boolean settled(ClientSession s, Document p, Document row) {
        require(lookup.version().equals(p.getString("lookupKeyVersion")));
        if (c("payment_purchase_cases").find(s, new Document("environment", environment)
                .append("normalizedObservation.store", row.getString("store")).append("normalizedObservation.appId", row.getString("appId"))
                .append("normalizedObservation.transactionKey", row.getString("transactionKey"))
                .append("normalizedObservation.kind", new Document("$in", List.of("REFUND", "REVOKE")))).first() != null) return false;
        if (Set.of("REFUNDED", "REVOKED").contains(p.getString("purchaseState"))) return "REVOKED".equals(p.getString("accessState"));
        if (!"VERIFIED".equals(p.getString("purchaseState")) || !"ALLOWED".equals(p.getString("accessState"))) return false;
        String id = p.getString("_id");
        require(c("subscription_entitlements").find(s, new Document("purchaseId", id).append("accountId", row.getString("accountId"))).first() != null
                && c("payment_ledger").find(s, new Document("effectKey", id + ":PURCHASE_VERIFIED")).first() != null);
        return true;
    }
    private Document source(ClientSession s, String key) {
        require(key != null);
        Document k = new Document("environment", environment).append("providerEventKey", key);
        Document source = c("payment_provider_inbox").find(s, k).first();
        if (source == null) source = c("payment_purchase_cases").find(s, k).first();
        require(source != null); return source;
    }
    private void matchSource(Document row, Document source) {
        require(Objects.equals(row.getString("accountId"), source.getString("accountId"))
                && Objects.equals(row.getString("lifecycleId"), source.getString("lifecycleId")));
        Document o = source.get("normalizedObservation", Document.class); require(o != null);
        for (String k : List.of("environment", "store", "appId", "transactionKey", "lookupKeyVersion")) require(Objects.equals(row.get(k), o.get(k)));
    }
    private Document purchase(ClientSession s, Document row) {
        return c("purchases").find(s, new Document("environment", environment).append("store", row.getString("store"))
                .append("appId", row.getString("appId")).append("transactionKey", row.getString("transactionKey"))).first();
    }
    private Document command(ClientSession s, String id) {
        Document d = commands().find(s, new Document("_id", id).append("environment", environment)).first(); require(d != null); return d;
    }
    private void update(ClientSession s, Document row, Document values) {
        long version = version(row); values.append("version", version + 1).append("updatedAt", Date.from(clock.instant()));
        require(commands().updateOne(s, new Document("_id", row.get("_id")).append("version", version), new Document("$set", values)).getModifiedCount() == 1);
    }
    private void touch(ClientSession s, Document row, long delta) {
        Document l = c("payment_account_lifecycles").find(s, new Document("_id", row.getString("lifecycleId")).append("environment", environment)
                .append("userId", row.getString("userId"))).first();
        require(l != null && l.get("pendingEvidence") instanceof Long);
        long n = l.getLong("pendingEvidence"), v = version(l);
        require(n >= 0 && (delta != 1 || n < Long.MAX_VALUE) && (delta != -1 || n > 0));
        require(c("payment_account_lifecycles").updateOne(s, new Document("_id", l.get("_id")).append("version", v).append("pendingEvidence", n),
                new Document("$set", new Document("version", v + 1).append("pendingEvidence", n + delta))).getModifiedCount() == 1);
    }
    private static long version(Document d) { require(d.get("version") instanceof Long && d.getLong("version") > 0 && d.getLong("version") < Long.MAX_VALUE); return d.getLong("version"); }
    private Document userKey(String user) { return new Document("environment", environment).append("userId", user); }
    private MongoCollection<Document> c(String name) { return store.database().getCollection(name); }
    private MongoCollection<Document> commands() { return c(PaymentSyncSchema.COLLECTION); }
    private void authorized(Instant deadline) { if (deadline == null || !clock.instant().isBefore(deadline)) throw new PaymentException(401, "UNAUTHENTICATED"); }
    private static void canonicalUser(String u) { try { require(UUID.fromString(u).toString().equals(u)); } catch (RuntimeException e) { throw new PaymentException(401, "UNAUTHENTICATED"); } }
    private static String digest(Document d) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d.toJson().getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw PaymentException.unavailable(); } }
}
