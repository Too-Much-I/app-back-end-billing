package web.tosunsaeng.billing.domain.payment.cleanup;

import com.mongodb.client.ClientSession;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;

/** Read within the cleanup lifecycle transaction. Schema verification is required at startup. */
final class CleanupLocalReader implements LocalReader {
    private final PaymentStore store;
    CleanupLocalReader(PaymentStore store) { this.store = Objects.requireNonNull(store); }

    @Override public Local read(ClientSession session, Subject subject) {
        Objects.requireNonNull(session);
        if (subject.accountId() != null) {
            Document account = new Document("accountId", subject.accountId());
            // Refunded, revoked and expired purchases still require financial retention.
            if (exists(session, "purchases", account)
                    || exists(session, "subscription_entitlements", account)
                    || exists(session, "payment_timelines", account)) return Local.FOUND;
        }
        if (subject.pendingEvidence() > 0) return Local.PENDING;
        List<Document> owners = new ArrayList<>();
        owners.add(new Document("lifecycleId", subject.lifecycleId()));
        if (subject.accountId() != null) owners.add(new Document("accountId", subject.accountId()));
        if (!subject.references().isEmpty()) owners.add(new Document("normalizedObservation.ownerRefId",
                new Document("$in", subject.references())));
        // An unassigned receipt/case may belong to this account; absence cannot be inferred.
        owners.add(new Document("accountId", null));
        Document pending = new Document("environment", subject.environment()).append("$or", owners);
        if (exists(session, "payment_purchase_cases", pending)
                || exists(session, "payment_provider_inbox", new Document(pending)
                    .append("state", new Document("$nin", List.of("APPLIED", "IGNORED"))))) return Local.PENDING;
        Document sync = new Document("environment", subject.environment()).append("userId", subject.userId())
                .append("$or", List.of(new Document("counted", true),
                        new Document("state", new Document("$nin", List.of("APPLIED", "REJECTED")))));
        if (exists(session, "payment_sync_commands", sync)) return Local.PENDING;
        if (subject.accountId() != null && exists(session, "payment_reconcile_cursors",
                new Document("accountId", subject.accountId()).append("$or", List.of(
                        new Document("kind", new Document("$ne", "ACCOUNT_LOOKUP")),
                        new Document("lookup.owner", new Document("$type", "string")),
                        new Document("lease", new Document("$exists", true)),
                        new Document("leaseOwner", new Document("$exists", true)),
                        new Document("leaseUntil", new Document("$exists", true)))))) return Local.PENDING;
        return Local.NONE;
    }
    private boolean exists(ClientSession session, String collection, Document query) {
        // Timeouts abort the attempt; they are never interpreted as an empty result.
        return store.database().getCollection(collection).find(session, query).projection(new Document("_id", 1))
                .limit(1).maxTime(200, TimeUnit.MILLISECONDS).first() != null;
    }
}
