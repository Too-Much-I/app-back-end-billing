package web.tosunsaeng.billing.domain.payment.worker;

import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Bounded operational retention only. Never deletes purchases, cases, accounts or financial evidence. */
public final class PaymentRetentionWorker {
    public record Tick(int handedOff, int candidates, int deleted, int failed) {}
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final PurchaseCore core;
    private final Clock clock;
    private final String environment;
    private final boolean deleteEnabled;

    public PaymentRetentionWorker(PaymentStore store, PaymentProperties p, PurchaseCore core, Clock clock, boolean deleteEnabled) {
        require(p.getRecovery().isFenceRequired());
        this.store = store; this.fence = new PaymentRecoveryFence(store, p); this.core = core;
        this.clock = clock; this.environment = p.getEnvironment(); this.deleteEnabled = deleteEnabled;
        PurchaseCoreSchema.verify(store.database()); PaymentSyncSchema.verify(store.database());
    }

    public Tick tick(int batch) {
        require(batch >= 1 && batch <= 100);
        var ticket = fence.capture(); Instant now = clock.instant();
        int handedOff = 0, deleted = 0, failed = 0;
        var sources = store.database().getCollection("payment_provider_inbox").find(new Document("environment", environment)
                .append("state", "RECEIVED").append("receivedAt", new Document("$lte", Date.from(now.minus(Duration.ofDays(119))))))
                .sort(new Document("receivedAt", 1).append("_id", 1)).limit(batch).into(new ArrayList<>());
        for (Document source : sources) {
            try {
                if (core.handoffAged(source.getString("providerEventKey"), ticket) == PurchaseCore.Outcome.REVIEW_REQUIRED) handedOff++;
            } catch (RuntimeException unavailable) { failed++; }
        }
        var commands = store.database().getCollection(PaymentSyncSchema.COLLECTION);
        Document eligible = terminal(now);
        var candidates = commands.find(eligible).sort(new Document("updatedAt", 1).append("_id", 1)).limit(batch).into(new ArrayList<>());
        if (deleteEnabled) for (Document row : candidates) {
            try {
                boolean removed = store.transaction(s -> {
                    fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PURGE);
                    Document exact = new Document(eligible).append("_id", row.get("_id")).append("version", row.get("version"));
                    return commands.deleteOne(s, exact).getDeletedCount() == 1;
                });
                if (removed) deleted++;
            } catch (RuntimeException unavailable) { failed++; }
        }
        return new Tick(handedOff, candidates.size(), deleted, failed);
    }

    private Document terminal(Instant now) {
        return new Document("environment", environment).append("state", new Document("$in", List.of("APPLIED", "REJECTED")))
                .append("counted", false).append("updatedAt", new Document("$lte", Date.from(now.minus(Duration.ofDays(120)))));
    }
}
