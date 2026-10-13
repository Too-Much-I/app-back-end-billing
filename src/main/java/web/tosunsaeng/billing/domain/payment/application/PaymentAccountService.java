package web.tosunsaeng.billing.domain.payment.application;

import java.time.*;
import java.util.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.dto.response.PurchaseAccountResponse;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

@Service
public class PaymentAccountService {
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    public PaymentAccountService(PaymentStore store, PaymentProperties properties, Clock clock) {
        this.store = store; this.properties = properties; this.clock = clock;
    }
    public PurchaseAccountResponse getOrCreate(String userId, Instant authorizationDeadline) {
        require(UUID.fromString(userId).toString().equals(userId));
        var fence = new web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence(store, properties);
        var ticket = fence.capture();
        return store.transaction(session -> {
            fence.acquire(session, ticket, web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence.Writer.ACCOUNT);
            if (!clock.instant().isBefore(authorizationDeadline)) throw new PaymentException(401, "UNAUTHENTICATED");
            var db = store.database();
            String environment = properties.getEnvironment();
            var key = new Document("environment", environment).append("userId", userId);
            var lifecycles = db.getCollection("payment_account_lifecycles");
            var accounts = db.getCollection("payment_accounts");
            Document lifecycle = lifecycles.find(session, key).first();
            Document account = accounts.find(session, key).first();
            Date now = Date.from(clock.instant());
            if (lifecycle == null) {
                require(account == null); // Missing legacy evidence requires an explicit migration, never repair-upsert.
                lifecycle = new Document("_id", uuid()).append("environment", environment).append("userId", userId)
                        .append("state", "ACTIVE").append("version", 1L).append("pendingEvidence", 0L);
                lifecycles.insertOne(session, lifecycle);
            } else {
                if ("WITHDRAWN".equals(lifecycle.getString("state"))) throw new PaymentException(403, "PAYMENT_ACCOUNT_WITHDRAWN");
                require("ACTIVE".equals(lifecycle.getString("state")) && lifecycle.get("version") instanceof Long);
                long version = lifecycle.getLong("version");
                require(version > 0 && version < Long.MAX_VALUE);
                require(lifecycles.updateOne(session, new Document("_id", lifecycle.get("_id")).append("version", version).append("state", "ACTIVE"),
                        new Document("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            }
            var refs = db.getCollection("purchase_account_refs");
            var cursors = db.getCollection("payment_reconcile_cursors");
            if (account == null) {
                String accountId = uuid(), reference = uuid();
                account = new Document("_id", accountId).append("environment", environment).append("userId", userId)
                        .append("paidSubjectRefId", uuid()).append("activeRefId", reference).append("state", "ACTIVE")
                        .append("version", 1L).append("createdAt", now).append("updatedAt", now);
                accounts.insertOne(session, account);
                refs.insertOne(session, new Document("_id", reference).append("accountId", accountId).append("environment", environment)
                        .append("active", true).append("createdAt", now).append("retiredAt", null));
                cursors.insertOne(session, new Document("_id", uuid()).append("accountId", accountId).append("kind", "ACCOUNT_LOOKUP")
                        .append("version", 1L).append("nextAttemptAt", now).append("lastActivityAt", now));
            } else {
                require("ACTIVE".equals(account.getString("state")) && account.get("version") instanceof Long
                        && account.getLong("version") > 0);
                canonicalId(account.getString("_id"));
                canonicalId(account.getString("paidSubjectRefId"));
                String reference = account.getString("activeRefId");
                canonicalId(reference);
                var active = refs.find(session, new Document("environment", environment).append("accountId", account.get("_id"))
                        .append("active", true)).limit(2).into(new ArrayList<>());
                require(active.size() == 1 && reference.equals(active.getFirst().getString("_id")));
                var cursorKey = new Document("accountId", account.get("_id")).append("kind", "ACCOUNT_LOOKUP");
                require(cursors.updateOne(session, cursorKey, new Document("$max", new Document("lastActivityAt", now))).getMatchedCount() == 1);
            }
            if (!clock.instant().isBefore(authorizationDeadline)) throw new PaymentException(401, "UNAUTHENTICATED");
            return new PurchaseAccountResponse(account.getString("activeRefId"));
        });
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static void canonicalId(String value) {
        require(value != null && UUID.fromString(value).version() == 4 && UUID.fromString(value).toString().equals(value));
    }
}
