import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentFoundationInitializer;
import web.tosunsaeng.billing.domain.payment.cleanup.CleanupSchema;
import web.tosunsaeng.billing.domain.payment.provider.PurchaseReferenceSchema;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;
import web.tosunsaeng.billing.domain.payment.worker.PaymentAccountLookupSchema;
import web.tosunsaeng.billing.domain.reservation.paid.*;
import web.tosunsaeng.billing.domain.examdeletion.DeletionSchema;

/** Explicit first-install or read-only verification. No startup hook, repair, activation, or deletion. */
public final class BillingProdSchemaBootstrap {
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception error) {
            System.out.println("BILLING_PROD_SCHEMA_FAILED " + error.getClass().getSimpleName()
                    + (error instanceof com.mongodb.MongoException mongo ? " code=" + mongo.getCode() : ""));
            for (var frame : error.getStackTrace()) {
                if (frame.getClassName().startsWith("web.tosunsaeng.billing.")
                        || frame.getClassName().equals("BillingProdSchemaBootstrap"))
                    System.out.println("SCHEMA_FRAME " + frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
            }
            System.exit(1);
        }
    }
    static void require(boolean value) {
        if (!value) throw new IllegalStateException("Explicit production precondition failed");
    }
    static String expectedBarrier(String mode, String configured) {
        String expected = configured == null ? "ALL_BLOCKED" : configured;
        require(Set.of("ALL_BLOCKED", "PURCHASE_BLOCKED", "OPEN").contains(expected));
        require("verify".equals(mode) || "ALL_BLOCKED".equals(expected));
        return expected;
    }
    static Document validateControl(List<Document> controls, String expected) {
        require(controls.size() == 1);
        Document control = controls.getFirst();
        require("PRODUCTION".equals(control.getString("environment"))
                && "billing".equals(control.getString("consumer"))
                && expected.equals(control.getString("barrier"))
                && control.get("_id") instanceof String id && !id.isBlank()
                && control.get("schemaVersion") instanceof Long schema && schema == 1L
                && control.get("version") instanceof Long version && version > 0
                && control.get("epoch") instanceof String epoch
                && epoch.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        return control;
    }
    static void run(String[] args) {
        require(args.length == 1 && Set.of("inspect", "install-empty", "verify").contains(args[0]));
        String expected = expectedBarrier(args[0], System.getenv("BILLING_EXPECTED_RECOVERY_BARRIER"));
        String database = System.getenv("BILLING_EXPECTED_DATABASE");
        String host = System.getenv("BILLING_EXPECTED_MONGO_HOST");
        require(database != null && database.endsWith("-billing-prod"));
        require(host != null && !host.isBlank());
        require("PRODUCTION".equals(System.getenv("BILLING_PAYMENT_ENVIRONMENT")));
        var uri = new ConnectionString(System.getenv("MONGODB_URI"));
        require(database.equals(uri.getDatabase()) && uri.getHosts().equals(List.of(host)));
        try (var client = MongoClients.create(uri)) {
            MongoDatabase db = client.getDatabase(database);
            var names = db.listCollectionNames().into(new ArrayList<>());
            System.out.println("COLLECTION_COUNT " + names.size());
            if (args[0].equals("inspect")) return;
            Document before = args[0].equals("verify") ? validateControl(db.getCollection(PaymentRecoveryFence.COLLECTION)
                    .find().into(new ArrayList<>()), expected) : null;
            if (args[0].equals("install-empty")) {
                require("true".equals(System.getenv("BILLING_WRITERS_STOPPED")));
                require(database.equals(System.getenv("BILLING_PROD_SCHEMA_APPROVED_DATABASE")));
                // Partial installation is not silently resumed or repaired.
                require(names.isEmpty());
                var mongoProperties = new web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties();
                mongoProperties.setInitializeIndexes(true);
                new web.tosunsaeng.billing.global.infrastructure.mongodb.BillingMongoIndexInitializer(
                        new org.springframework.data.mongodb.core.MongoTemplate(client, database), mongoProperties).run(null);
                PaymentFoundationInitializer.initialize(db);
                PaymentFoundationInitializer.initializeWithdrawal(db);
                PaymentRecoverySchema.initializeEmpty(db);
                db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", UUID.randomUUID().toString())
                        .append("environment", "PRODUCTION").append("consumer", "billing").append("schemaVersion", 1L)
                        .append("epoch", UUID.randomUUID().toString()).append("version", 1L).append("barrier", "ALL_BLOCKED"));
                PaidExamSchema.installUnderMaintenance(db, true);
                PurchaseCoreSchema.installUnderMaintenance(db, true);
                RefundCoreSchema.upgradeUnderMaintenance(db, true);
                RefundFanoutSchema.installUnderMaintenance(db, true);
                PaymentSyncSchema.installUnderMaintenance(db, true);
                DeletionSchema.installUnderMaintenance(db, true);
                PaymentSnapshotSchema.initializeEmpty(db);
                PaymentAccountLookupSchema.installUnderMaintenance(db, true);
                PurchaseReferenceSchema.installUnderMaintenance(db, true);
                CleanupSchema.installUnderMaintenance(db, true);
            }
            PaymentFoundationInitializer.verify(db); PaymentRecoverySchema.verify(db);
            PaidExamSchema.verify(db); RefundCoreSchema.verify(db); RefundFanoutSchema.verify(db);
            PaymentSyncSchema.verify(db); DeletionSchema.verify(db); PaymentSnapshotSchema.verify(db);
            PaymentAccountLookupSchema.verify(db); PurchaseReferenceSchema.verify(db); CleanupSchema.verify(db);
            var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
            Document control = validateControl(controls, expected);
            require(before == null || before.equals(control));
            System.out.println("BILLING_PROD_SCHEMA_OK " + args[0] + " barrier=" + expected);
        }
    }
}
