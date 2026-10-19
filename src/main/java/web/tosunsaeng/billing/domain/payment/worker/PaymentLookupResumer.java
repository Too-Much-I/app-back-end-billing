package web.tosunsaeng.billing.domain.payment.worker;

import java.time.*;
import java.util.*;
import com.mongodb.client.ClientSession;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.*;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Explicit maintenance primitive, NOT an HTTP endpoint or scheduled automatic retry. */
public final class PaymentLookupResumer {
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final Clock clock;
    private final String environment;
    public PaymentLookupResumer(PaymentStore store, PaymentProperties p, Clock clock) {
        require(p.getRecovery().isFenceRequired());
        this.store = store; this.clock = clock; this.environment = p.getEnvironment(); this.fence = new PaymentRecoveryFence(store, p);
        PaymentSyncSchema.verify(store.database()); PaymentAccountLookupSchema.verify(store.database());
    }
    /** Caller must authenticate/authorize the operator and isolate account writers before invocation. */
    public int resumeUnderMaintenance(String accountId, long expectedCursorVersion, long expectedGeneration,
                                      PaymentSyncService.LookupFailure reason, Map<String, Long> selected, boolean writersIsolated) {
        require(writersIsolated && reason != null && selected != null && selected.size() <= 100);
        Map<String, Long> commands = Map.copyOf(selected);
        var ticket = fence.capture();
        return store.transaction(s -> resumeInTransaction(s, ticket, accountId, expectedCursorVersion, expectedGeneration, reason, commands, true));
    }
    /** Operator receipt and mutation share the caller's transaction. Preview performs reads only. */
    public int resumeInTransaction(ClientSession s, PaymentRecoveryFence.Ticket ticket, String accountId,
                                    long expectedCursorVersion, long expectedGeneration,
                                    PaymentSyncService.LookupFailure reason, Map<String, Long> commands, boolean apply) {
            require(s != null && s.hasActiveTransaction() && reason != null && commands != null && commands.size() <= 100);
            if (apply) fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            else fence.check(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            Instant now = clock.instant();
            var accounts = store.database().getCollection("payment_accounts");
            Document account = accounts.find(s, new Document("_id", accountId).append("environment", environment)).first(); require(account != null);
            var rows = store.database().getCollection(PaymentAccountLookupSchema.COLLECTION);
            Document row = rows.find(s, new Document("accountId", accountId).append("kind", "ACCOUNT_LOOKUP")).first();
            require(row != null && row.getLong("version") == expectedCursorVersion);
            Document work = row.get("lookup", Document.class);
            require(work != null && work.getLong("generation") == expectedGeneration);
            if (commands.isEmpty()) require(reason.name().equals(work.getString("failure")));
            String marker = work.getString("failure");
            if (marker != null && Set.of("BLOCKED_AUTH", "PROVIDER_REVIEW", "PROVIDER_CONTRACT", "PAGE_LIMIT").contains(marker))
                require(reason.name().equals(marker));
            require(work.getString("owner") == null || !work.getDate("until").toInstant().isAfter(now));
            var sync = store.database().getCollection(PaymentSyncSchema.COLLECTION);
            Document accountKey = new Document("environment", environment).append("accountId", accountId);
            require(sync.find(s, new Document(accountKey).append("state", "PENDING")).limit(1).first() == null);
            String lifecycleId = null;
            for (var item : commands.entrySet()) {
                Document command = sync.find(s, new Document(accountKey).append("_id", item.getKey())).first();
                require(command != null && item.getValue() != null && command.getLong("version").equals(item.getValue())
                        && command.getBoolean("counted") && Objects.equals(command.getString("errorCode"), reason.name())
                        && command.getString("sourceObservationKey") == null && Objects.equals(command.getString("userId"), account.getString("userId"))
                        && (reason == PaymentSyncService.LookupFailure.BLOCKED_AUTH ? "BLOCKED_AUTH" : "REVIEW_REQUIRED").equals(command.getString("state")));
                require(!now.isBefore(command.getDate("updatedAt").toInstant()) && !now.isBefore(command.getDate("createdAt").toInstant()));
                if (lifecycleId == null) lifecycleId = command.getString("lifecycleId");
                require(Objects.equals(lifecycleId, command.getString("lifecycleId")));
                Document cases = new Document("environment", environment);
                for (String field : List.of("store", "appId")) cases.append("normalizedObservation." + field, command.get(field));
                cases.append("normalizedObservation.transactionKey", web.tosunsaeng.billing.domain.payment.sync.SyncTransactionKeys.query(command));
                require(store.database().getCollection("payment_purchase_cases").find(s, cases).limit(1).first() == null);
                long version = next(command, "version", 1), attempts = next(command, "attempts", 0);
                if (apply) require(sync.updateOne(s, new Document("_id", command.get("_id")).append("version", item.getValue()), new Document("$set",
                        new Document("state", "PENDING").append("errorCode", null).append("retryStartedAt", Date.from(now))
                                .append("updatedAt", Date.from(now)).append("version", version).append("attempts", attempts))).getModifiedCount() == 1);
            }
            var lifecycles = store.database().getCollection("payment_account_lifecycles");
            Document lifeKey = new Document("environment", environment).append("userId", account.getString("userId"));
            if (lifecycleId != null) lifeKey.append("_id", lifecycleId);
            Document life = lifecycles.find(s, lifeKey).first();
            require(life != null && life.getLong("pendingEvidence") >= commands.size());
            long lifeVersion = next(life, "version", 1);
            if (apply) require(lifecycles.updateOne(s, new Document("_id", life.get("_id")).append("version", life.get("version")),
                    new Document("$set", new Document("version", lifeVersion))).getModifiedCount() == 1);
            Document reset = new Document(work).append("owner", null).append("until", null).append("cursor", null).append("pages", 0L)
                    .append("generation", next(work, "generation", 1)).append("leaseVersion", next(work, "leaseVersion", 1))
                    .append("attempts", 1L).append("fenceVersion", 0L).append("failure", "RESUMED_" + reason.name()).append("reference", account.getString("activeRefId"));
            long cursorVersion = next(row, "version", 1);
            if (apply) require(rows.updateOne(s, new Document("_id", row.get("_id")).append("version", expectedCursorVersion), new Document("$set",
                    new Document("lookup", reset).append("nextAttemptAt", Date.from(now)).append("reconcileAt", Date.from(now))
                            .append("reconcileStartedAt", null).append("version", cursorVersion))).getModifiedCount() == 1);
            return commands.size();
    }
    private static long next(Document row, String key, long minimum) {
        require(row.get(key) instanceof Long); long value = row.getLong(key); require(value >= minimum && value < Long.MAX_VALUE); return value + 1;
    }
}
