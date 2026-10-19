package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.MongoDatabase;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema.*;

/** r9: optional matching hints, separate from verified normalizedObservation. Explicit maintenance only. */
public final class PurchaseReviewResolutionSchema {
    private PurchaseReviewResolutionSchema() {}
    static Map<String, Definition> manifest() {
        var result = MixedStorePurchaseSchema.manifest();
        Document fields = new Document();
        for (String key : List.of("environment", "store", "appId", "transactionKey", "lookupKeyVersion", "ownerRefId", "storeProductId"))
            fields.put(key, new Document("bsonType", "string"));
        fields.put("purchasedAt", new Document("bsonType", "date"));
        fields.put("environment", new Document("enum", List.of("SANDBOX", "PRODUCTION")));
        fields.put("store", new Document("enum", List.of("APP_STORE", "PLAY_STORE")));
        fields.put("transactionKey", new Document("bsonType", "string").append("pattern", "^[a-f0-9]{64}(?![\\s\\S])"));
        var props = result.get("payment_purchase_cases").validator().get("$jsonSchema", Document.class).get("properties", Document.class);
        props.put("reviewCandidate", new Document("bsonType", "object").append("required", new ArrayList<>(fields.keySet()))
                .append("additionalProperties", false).append("properties", fields));
        props.put("resolvedObservationKey", new Document("bsonType", "string").append("pattern", "^[a-f0-9]{64}(?![\\s\\S])"));
        return result;
    }
    public static void verify(MongoDatabase db) { manifest().forEach((name, definition) -> verifyOne(db, name, definition)); }
    public static void upgradeUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier"))))
            throw new IllegalStateException("Review migration requires isolated writers and ALL_BLOCKED");
        var old = MixedStorePurchaseSchema.manifest(); var target = manifest();
        target.forEach((name, definition) -> {
            try { verifyOne(db, name, definition); }
            catch (IllegalStateException prior) { verifyOne(db, name, old.get(name)); }
            if (db.getCollection(name).find(new Document("$nor", List.of(definition.validator()))).limit(1).first() != null)
                throw new IllegalStateException("Review migration data mismatch");
        });
        db.runCommand(new Document("collMod", "payment_purchase_cases").append("validator", target.get("payment_purchase_cases").validator())
                .append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
}
