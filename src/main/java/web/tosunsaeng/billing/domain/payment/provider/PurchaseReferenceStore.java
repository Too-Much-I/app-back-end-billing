package web.tosunsaeng.billing.domain.payment.provider;

import com.mongodb.client.ClientSession;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Minimal encrypted RC resource identity, not a grant source or a no-purchase verifier. */
public final class PurchaseReferenceStore {
    private final PaymentStore store;
    private final PaymentRecoveryFence fence;
    private final String environment, project;
    private final Clock clock;
    private final PurchaseReferenceCipher cipher;
    public PurchaseReferenceStore(PaymentStore store, PaymentProperties p, Clock clock, String project, PurchaseReferenceCipher cipher) {
        require(p.getRecovery().isFenceRequired()); RevenueCatPurchaseDecoder.text(project);
        this.store = store; this.fence = new PaymentRecoveryFence(store, p); this.environment = p.getEnvironment();
        this.clock = clock; this.project = project; this.cipher = cipher; PurchaseReferenceSchema.verify(store.database());
    }
    public void remember(PurchaseObservation observation, String reference, String accountId, PaymentRecoveryFence.Ticket ticket, Consumer<ClientSession> guard) {
        require(environment.equals(observation.environment())); RevenueCatPurchaseDecoder.text(reference);
        store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY); guard.accept(s);
            Document key = new Document("environment", environment).append("store", observation.store().name()).append("appId", observation.appId())
                    .append("transactionKey", observation.transactionKey()).append("lookupKeyVersion", observation.lookupKeyVersion())
                    .append("accountId", accountId).append("ownerRefId", observation.ownerRefId());
            Document p = store.database().getCollection("purchases").find(s, key).first();
            require(p != null && "VERIFIED".equals(p.getString("purchaseState")) && "ALLOWED".equals(p.getString("accessState"))
                    && "READY".equals(p.getString("processingStatus")) && p.getDate("retentionExpiresAt") != null);
            if (!clock.instant().isBefore(p.getDate("retentionExpiresAt").toInstant())) return null;
            var rows = store.database().getCollection(PurchaseReferenceSchema.COLLECTION);
            Document old = rows.find(s, new Document("_id", p.get("_id"))).first();
            if (old != null) {
                match(old, p, accountId);
                if (!reference.equals(decode(old))) throw RevenueCatFailure.contract();
                long version = old.getLong("version"); require(version > 0 && version < Long.MAX_VALUE);
                if (!old.getDate("retentionExpiresAt").equals(p.getDate("retentionExpiresAt")))
                    require(rows.updateOne(s, new Document("_id", p.get("_id")).append("version", version), new Document("$set",
                            new Document("retentionExpiresAt", p.getDate("retentionExpiresAt"))).append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
            } else {
                var e = cipher.encrypt(context(p), reference);
                Document row = new Document("_id", p.get("_id")).append("environment", environment).append("project", project)
                        .append("accountId", accountId).append("version", 1L).append("retentionExpiresAt", p.getDate("retentionExpiresAt"))
                        .append("envelope", new Document("revision", (long) e.revision()).append("keyId", e.keyId()).append("nonce", e.nonce()).append("ciphertext", e.ciphertext()));
                for (String f : List.of("store", "appId", "transactionKey", "lookupKeyVersion")) row.append(f, p.get(f));
                rows.insertOne(s, row);
            }
            return null;
        });
    }
    /** Internal use only. The plaintext must never be logged or returned by a public API. */
    public String read(String purchaseId, String accountId) {
        var ticket = fence.capture();
        return store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PAYMENT_APPLY);
            Document p = store.database().getCollection("purchases").find(s, new Document("_id", purchaseId).append("environment", environment).append("accountId", accountId)).first();
            require(p != null);
            if (p.getDate("retentionExpiresAt") == null || !clock.instant().isBefore(p.getDate("retentionExpiresAt").toInstant())) return null;
            Document row = store.database().getCollection(PurchaseReferenceSchema.COLLECTION).find(s, new Document("_id", purchaseId)).first();
            if (row == null) return null; match(row, p, accountId); return decode(row);
        });
    }
    private void match(Document row, Document purchase, String accountId) {
        require(environment.equals(row.getString("environment")) && project.equals(row.getString("project")) && accountId.equals(row.getString("accountId")));
        for (String f : List.of("_id", "store", "appId", "transactionKey", "lookupKeyVersion")) require(Objects.equals(row.get(f), purchase.get(f)));
    }
    private PurchaseReferenceCipher.Context context(Document row) { return new PurchaseReferenceCipher.Context(environment, project, row.getString("appId"), row.getString("transactionKey")); }
    private String decode(Document row) {
        Document e = row.get("envelope", Document.class);
        return cipher.decrypt(context(row), new PurchaseReferenceCipher.Envelope(Math.toIntExact(e.getLong("revision")), e.getString("keyId"), e.getString("nonce"), e.getString("ciphertext")));
    }
    @Override public String toString() { return "PurchaseReferenceStore[REDACTED]"; }
}
