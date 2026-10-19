import java.util.*;
import org.bson.Document;

/** Offline precondition tests; no connection, credential or external calls. */
public final class BillingProdSchemaBootstrapTest {
    private static int cases;
    private static Document control() {
        return new Document("_id", "synthetic-control").append("environment", "PRODUCTION")
                .append("consumer", "billing").append("schemaVersion", 1L).append("version", 2L)
                .append("epoch", "11111111-1111-4111-8111-111111111111").append("barrier", "PURCHASE_BLOCKED");
    }
    private static void rejected(Runnable operation) {
        try { operation.run(); } catch (IllegalStateException expected) { cases++; return; }
        throw new AssertionError("Unsafe precondition accepted");
    }
    public static void main(String[] args) {
        for (String barrier : List.of("ALL_BLOCKED", "PURCHASE_BLOCKED", "OPEN")) {
            if (!barrier.equals(BillingProdSchemaBootstrap.expectedBarrier("verify", barrier))) throw new AssertionError();
            Document d = control().append("barrier", barrier);
            if (BillingProdSchemaBootstrap.validateControl(List.of(d), barrier) != d) throw new AssertionError();
            cases++;
        }
        if (!"ALL_BLOCKED".equals(BillingProdSchemaBootstrap.expectedBarrier("verify", null))) throw new AssertionError();
        cases++;
        for (String value : List.of("", "open", "ANY", " OPEN"))
            rejected(() -> BillingProdSchemaBootstrap.expectedBarrier("verify", value));
        for (String mode : List.of("inspect", "install-empty"))
            rejected(() -> BillingProdSchemaBootstrap.expectedBarrier(mode, "PURCHASE_BLOCKED"));
        rejected(() -> BillingProdSchemaBootstrap.validateControl(List.of(), "PURCHASE_BLOCKED"));
        rejected(() -> BillingProdSchemaBootstrap.validateControl(List.of(control(), control()), "PURCHASE_BLOCKED"));
        rejected(() -> BillingProdSchemaBootstrap.validateControl(List.of(control()), "ALL_BLOCKED"));
        for (var invalid : List.of(new Document("environment", "SANDBOX"), new Document("consumer", "other"),
                new Document("schemaVersion", 1), new Document("schemaVersion", 2L), new Document("version", 0L),
                new Document("version", 2), new Document("epoch", "bad"), new Document("_id", ""))) {
            Document d = control(); d.putAll(invalid);
            rejected(() -> BillingProdSchemaBootstrap.validateControl(List.of(d), "PURCHASE_BLOCKED"));
        }
        System.out.println("PROD_SCHEMA_PRECONDITIONS_OK cases=" + cases);
    }
}
