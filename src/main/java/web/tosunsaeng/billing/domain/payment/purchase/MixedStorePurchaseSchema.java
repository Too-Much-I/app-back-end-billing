package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.MongoDatabase;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import static web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema.*;

/** r8: account environment stays unchanged; optional verified Store environment on a purchase. */
public final class MixedStorePurchaseSchema {
    private MixedStorePurchaseSchema() {}
    static Map<String, Definition> manifest() {
        var result = RefundCoreSchema.manifest();
        result.get("purchases").validator().get("$jsonSchema", Document.class).get("properties", Document.class)
                .put("storeEnvironment", new Document("enum", List.of("SANDBOX", "PRODUCTION")));
        return result;
    }
    public static void verify(MongoDatabase db) {
        try { manifest().forEach((name, definition) -> verifyOne(db, name, definition)); }
        catch (IllegalStateException next) { PurchaseReviewResolutionSchema.verify(db); }
    }
    /** Exact r7/r8 only, isolated writers, no backfill/index recreation or startup DDL. */
    public static void upgradeUnderMaintenance(MongoDatabase db, boolean isolated) {
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (!isolated || controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier"))))
            throw new IllegalStateException("Mixed Store migration requires isolated writers and ALL_BLOCKED");
        var old = RefundCoreSchema.manifest(); var target = manifest();
        target.forEach((name, definition) -> {
            try { verifyOne(db, name, definition); }
            catch (IllegalStateException prior) { verifyOne(db, name, old.get(name)); }
            if (db.getCollection(name).find(new Document("$nor", List.of(definition.validator()))).limit(1).first() != null)
                throw new IllegalStateException("Mixed Store migration data mismatch");
        });
        db.runCommand(new Document("collMod", "purchases").append("validator", target.get("purchases").validator())
                .append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
}
