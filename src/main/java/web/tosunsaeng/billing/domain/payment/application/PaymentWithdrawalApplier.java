package web.tosunsaeng.billing.domain.payment.application;

import java.time.*;
import java.util.*;
import com.mongodb.client.ClientSession;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.PaymentWithdrawal;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalService.Outcome;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentWithdrawalException.*;

/**
 * Applies inside the caller's transaction; never opens or commits a nested transaction.
 * Recovery callers must acquire their local fence before calling, including duplicates/no-ops.
 */
public final class PaymentWithdrawalApplier {
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    public PaymentWithdrawalApplier(PaymentStore store, PaymentProperties properties, Clock clock) {
        this.store = store; this.properties = properties; this.clock = clock;
    }
    public Outcome applyEvent(ClientSession session, PaymentWithdrawal event) {
        require(session != null && session.hasActiveTransaction());
        var inbox = store.database().getCollection("payment_withdrawal_inbox");
        Document previous = inbox.find(session, new Document("eventId", event.eventId())).first();
        if (previous != null) {
            if (!event.digest().equals(previous.getString("digest"))) throw conflict("EVENT_ID_CONFLICT");
            return Outcome.DUPLICATE;
        }
        Outcome outcome = applyFact(session, new WithdrawalFact(event.userId(), event.withdrawnAt()));
        Instant now = clock.instant();
        inbox.insertOne(session, new Document("eventId", event.eventId()).append("digest", event.digest())
                .append("disposition", outcome.name()).append("receivedAt", Date.from(now))
                .append("purgeAt", Date.from(now.plus(Duration.ofDays(120)))));
        return outcome;
    }
    /** Snapshot fact has no event ID: never invent an event or inbox entry. */
    public Outcome applyFact(ClientSession session, WithdrawalFact event) {
        require(session != null && session.hasActiveTransaction());
        var db = store.database();
        var key = new Document("environment", properties.getEnvironment()).append("userId", event.userId());
        var lifecycles = db.getCollection("payment_account_lifecycles");
        var accounts = db.getCollection("payment_accounts");
        Document lifecycle = lifecycles.find(session, key).first();
        Document account = accounts.find(session, key).first();
        Date now = Date.from(clock.instant());
        String exactTime = event.withdrawnAt().toString();
        Outcome outcome = Outcome.APPLIED;
        if (lifecycle != null && "WITHDRAWN".equals(lifecycle.getString("state"))) {
            // Canonical text preserves legacy sub-millisecond evidence that BSON Date cannot represent.
            require(lifecycle.get("withdrawnAtCanonical") instanceof String);
            if (!exactTime.equals(lifecycle.getString("withdrawnAtCanonical"))) throw conflict("WITHDRAWAL_STATE_CONFLICT");
            outcome = Outcome.NOOP;
        } else {
            Document changes = new Document("state", "WITHDRAWN").append("withdrawnAt", Date.from(event.withdrawnAt()))
                    .append("withdrawnAtCanonical", exactTime).append("updatedAt", now);
            if (lifecycle == null) {
                require(account == null); // Legacy missing evidence needs an explicit migration.
                lifecycle = new Document(key).append("_id", UUID.randomUUID().toString())
                        .append("version", 1L).append("pendingEvidence", 0L);
                lifecycle.putAll(changes);
                lifecycles.insertOne(session, lifecycle);
            } else {
                require("ACTIVE".equals(lifecycle.getString("state")));
                long version = version(lifecycle);
                require(lifecycles.updateOne(session, new Document("_id", lifecycle.get("_id"))
                        .append("state", "ACTIVE").append("version", version),
                        new Document("$set", changes).append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            }
            if (account != null) {
                require("ACTIVE".equals(account.getString("state")));
                long version = version(account);
                require(accounts.updateOne(session, new Document("_id", account.get("_id")).append("version", version),
                        new Document("$set", changes).append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
                db.getCollection("purchase_account_refs").updateMany(session,
                        new Document("environment", properties.getEnvironment()).append("accountId", account.get("_id")).append("active", true),
                        new Document("$set", new Document("active", false).append("retiredAt", now)));
            }
            // Candidate only: no worker, no provider check, no TTL/business deletion.
            Instant eligible = event.withdrawnAt().plus(Duration.ofDays(15));
            Date eligibleAt = ceilingMillis(eligible);
            var job = new Document(key).append("_id", UUID.randomUUID().toString())
                    .append("lifecycleId", lifecycle.get("_id")).append("accountId", account == null ? null : account.get("_id"))
                    .append("eligibleAt", eligibleAt).append("state", "WAITING").append("version", 1L)
                    .append("nextAttemptAt", eligibleAt).append("createdAt", now);
            db.getCollection("payment_account_cleanup_jobs").updateOne(session, key,
                    new Document("$setOnInsert", job), new UpdateOptions().upsert(true));
        }

        return outcome;
    }
    private static long version(Document document) {
        require(document.get("version") instanceof Long);
        long value = document.getLong("version");
        require(value > 0 && value < Long.MAX_VALUE);
        return value;
    }
    private static Date ceilingMillis(Instant instant) {
        long millis = instant.toEpochMilli();
        return new Date(instant.getNano() % 1_000_000 == 0 ? millis : Math.addExact(millis, 1));
    }
}
