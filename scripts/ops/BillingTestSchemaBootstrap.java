import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import org.bson.Document;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentFoundationInitializer;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.reservation.paid.*;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;
import web.tosunsaeng.billing.domain.examdeletion.DeletionSchema;

/** One-off operator tool. No deletion, sample purchase, automatic repair, or production support. */
public final class BillingTestSchemaBootstrap {
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception e) {
            System.out.println("BILLING_SCHEMA_FAILED " + e.getClass().getSimpleName()
                    + (e instanceof com.mongodb.MongoException mongo ? " code=" + mongo.getCode() : ""));
            for (StackTraceElement frame : e.getStackTrace()) {
                if (frame.getClassName().startsWith("web.tosunsaeng.billing.")
                        || frame.getClassName().equals("BillingTestSchemaBootstrap"))
                    System.out.println("BILLING_SCHEMA_FRAME " + frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
            }
            System.exit(1);
        }
    }
    static void require(boolean ok) { if (!ok) throw new IllegalStateException("Precondition failed"); }
    static void run(String[] args) {
        require(args.length == 1 && Set.of("inspect", "install", "verify", "open").contains(args[0]));
        String database = System.getenv("BILLING_EXPECTED_DATABASE"), host = System.getenv("BILLING_EXPECTED_MONGO_HOST");
        require(database != null && database.matches("[A-Za-z0-9_-]+[-_]test") && host != null && !host.isBlank());
        var uri = new ConnectionString(System.getenv("MONGODB_URI"));
        require(database.equals(uri.getDatabase()) && uri.getHosts().equals(List.of(host)));
        try (var client = MongoClients.create(uri)) {
            MongoDatabase db = client.getDatabase(database);
            var names = db.listCollectionNames().into(new ArrayList<>()); Collections.sort(names);
            for (String name : names) System.out.println("INVENTORY " + name + " " + db.getCollection(name).countDocuments());
            if (args[0].equals("inspect")) return;
            if (args[0].equals("install")) {
                require("true".equals(System.getenv("BILLING_WRITERS_STOPPED")));
                // This bootstrap is deliberately limited to a new test DB, not a legacy migration.
                for (String name : names) require(name.equals("benefit_definitions")
                        || name.equals(PaymentRecoveryFence.COLLECTION) || db.getCollection(name).countDocuments() == 0);
                PaymentFoundationInitializer.initialize(db);
                PaymentFoundationInitializer.initializeWithdrawal(db);
                PaymentRecoverySchema.initializeEmpty(db);
                var controls = db.getCollection(PaymentRecoveryFence.COLLECTION);
                if (controls.countDocuments() == 0) controls.insertOne(new Document("_id", UUID.randomUUID().toString())
                        .append("environment", "SANDBOX").append("consumer", "billing").append("schemaVersion", 1L)
                        .append("epoch", UUID.randomUUID().toString()).append("version", 1L).append("barrier", "ALL_BLOCKED"));
                require(controls.countDocuments() == 1);
                Document control = controls.find().first();
                require("SANDBOX".equals(control.getString("environment")) && "billing".equals(control.getString("consumer"))
                        && "ALL_BLOCKED".equals(control.getString("barrier")));
                PaidExamSchema.installUnderMaintenance(db, true);
                // A completed install is verified instead of attempting to downgrade its manifest.
                boolean refunded;
                try { RefundCoreSchema.verify(db); refunded = true; } catch (IllegalStateException absent) { refunded = false; }
                if (!refunded) { PurchaseCoreSchema.installUnderMaintenance(db, true); RefundCoreSchema.upgradeUnderMaintenance(db, true); }
                RefundFanoutSchema.installUnderMaintenance(db, true);
                PaymentSyncSchema.installUnderMaintenance(db, true);
                DeletionSchema.installUnderMaintenance(db, true);
            }
            PaymentFoundationInitializer.verify(db); PaymentRecoverySchema.verify(db);
            PaidExamSchema.verify(db); RefundCoreSchema.verify(db); RefundFanoutSchema.verify(db);
            PaymentSyncSchema.verify(db); DeletionSchema.verify(db);
            if (args[0].equals("open")) {
                require("true".equals(System.getenv("BILLING_WRITERS_STOPPED")));
                // Opening is a fresh-test bootstrap only, not approval of a restored/legacy DB.
                for (String name : names) require(name.equals("benefit_definitions")
                        || name.equals(PaymentRecoveryFence.COLLECTION) || db.getCollection(name).countDocuments() == 0);
                var controls = db.getCollection(PaymentRecoveryFence.COLLECTION);
                require(controls.countDocuments() == 1);
                Document control = controls.find().first();
                require("SANDBOX".equals(control.getString("environment")) && "billing".equals(control.getString("consumer")));
                if (!"OPEN".equals(control.getString("barrier"))) {
                    require("ALL_BLOCKED".equals(control.getString("barrier")));
                    require(controls.updateOne(new Document("_id", control.get("_id")).append("version", control.get("version"))
                            .append("barrier", "ALL_BLOCKED"), new Document("$set", new Document("barrier", "OPEN"))
                            .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
                }
            }
            System.out.println("BILLING_SCHEMA_OK " + args[0]);
        }
    }
}
