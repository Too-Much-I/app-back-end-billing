package web.tosunsaeng.billing.domain.payment.recovery;

import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Only recovery temporary material. Never deletes account, lifecycle, inbox or purchase data. */
public final class PaymentRecoveryRetention {
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    private final PaymentRecoveryFence fence;
    public PaymentRecoveryRetention(PaymentStore store, PaymentProperties properties, Clock clock) {
        this.store = store; this.properties = properties; this.clock = clock;
        fence = new PaymentRecoveryFence(store, properties);
    }
    public int batch() {
        require(properties.getRecovery().isFenceRequired());
        var ticket = fence.capture();
        int metadata = store.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.RECOVERY);
            var controls = store.database().getCollection(PaymentRecoveryFence.COLLECTION);
            Document c = controls.find(session, scope()).first();
            require(c != null && Long.valueOf(4).equals(c.get("schemaVersion")));
            var feed = c.get("feedJob", Document.class);
            if (feed != null && feed.get("cursorExpiresAt") != null
                    && !clock.instant().isBefore(feed.getDate("cursorExpiresAt").toInstant())) {
                require(feed.getLong("version") < Long.MAX_VALUE);
                feed.put("version", feed.getLong("version") + 1);
                feed.put("runId", UUID.randomUUID().toString()); feed.put("cursor", null); feed.put("target", null);
                feed.put("cursorExpiresAt", null); feed.put("pageIndex", 0L); feed.put("scanStart", c.getLong("checkpoint"));
                feed.put("scanStartedAt", Date.from(clock.instant())); feed.put("leaseId", null); feed.put("leaseUntil", Date.from(clock.instant()));
                controls.updateOne(session, scope(), new Document("$set", new Document("feedJob", feed)));
            }
            var operations = store.database().getCollection(WithdrawalSnapshotProcessor.OPERATIONS);
            var expired = operations.find(session, scope().append("retentionExpiresAt", new Document("$lte", Date.from(clock.instant()))))
                    .sort(new Document("retentionExpiresAt", 1).append("_id", 1)).limit(20).into(new ArrayList<>());
            var job = c.get("snapshotJob", Document.class);
            for (var operation : expired) {
                if (job != null && job.getString("operationId").equals(operation.getString("operationId"))) {
                    require(!clock.instant().isBefore(job.getDate("expiresAt").toInstant()));
                    controls.updateOne(session, scope(), new Document("$unset", new Document("snapshotJob", "")));
                }
                operations.deleteOne(session, new Document("_id", operation.get("_id")));
            }
            return expired.size();
        });
        // These cores require no client for cleanup and never invoke any HTTP method.
        int snapshots = new WithdrawalSnapshotProcessor(store, properties, clock, null).purgeExpiredBatch();
        int receipts = new WithdrawalFeedProcessor(store, properties, clock, null, new RecoveryRetryPolicy(() -> 0.5)).purgeExpiredReceipts();
        return metadata + snapshots + receipts;
    }
    private Document scope() { return new Document("environment", properties.getEnvironment()).append("consumer", "billing"); }
}
