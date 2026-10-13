package web.tosunsaeng.billing.domain.payment.cleanup;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** No DDL at startup. Install only under separately approved writer isolation. */
public final class CleanupSchema {
    public static final String COLLECTION = "payment_account_cleanup_jobs";
    private CleanupSchema() {}
    static Document manifest() {
        return manifest("r2");
    }
    static Document manifest(String revision) {
        try (var in = CleanupSchema.class.getResourceAsStream("/payment-cleanup-schema-" + revision + ".json")) {
            return Document.parse(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception error) { throw mismatch(); }
    }
    public static void verify(MongoDatabase db) {
        try {
            Document options = options(db);
            if (!manifest().get("validator").equals(options.get("validator"))
                    || !"strict".equals(options.getString("validationLevel"))
                    || !"error".equals(options.getString("validationAction"))) throw mismatch();
            simple(options);
            verifyIndexes(db, false);
        } catch (RuntimeException error) { throw mismatch(); }
    }
    /** An explicit maintenance helper, never a bean/startup/request operation. Flag is not proof of isolation. */
    public static void installUnderMaintenance(MongoDatabase db, boolean writersIsolated) {
        if (!writersIsolated) throw mismatch();
        var controls = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (controls.isEmpty() || controls.stream().anyMatch(c -> !"ALL_BLOCKED".equals(c.getString("barrier")))) throw mismatch();
        var options = options(db);
        simple(options);
        Document existing = options.get("validator", Document.class);
        Document expected = manifest().get("validator", Document.class);
        if (existing != null && !existing.isEmpty() && !expected.equals(existing)
                && !manifest("r1").get("validator").equals(existing)) throw mismatch();
        // Existing rows must validate BEFORE installing; never bless corrupt legacy rows.
        if (db.getCollection(COLLECTION).find(new Document("$nor", List.of(expected))).limit(1).first() != null) throw mismatch();
        verifyIndexes(db, true);
        db.runCommand(new Document("collMod", COLLECTION).append("validator", expected)
                .append("validationLevel", "strict").append("validationAction", "error"));
        verify(db);
    }
    private static Document options(MongoDatabase db) {
        var meta = db.listCollections().filter(new Document("name", COLLECTION)).first();
        if (meta == null) throw mismatch();
        return meta.get("options", Document.class);
    }
    private static void verifyIndexes(MongoDatabase db, boolean addMissing) {
        var collection = db.getCollection(COLLECTION);
        var indexes = collection.listIndexes().into(new ArrayList<>());
        if (indexes.stream().anyMatch(i -> i.containsKey("expireAfterSeconds"))) throw mismatch();
        for (Document index : manifest().getList("indexes", Document.class)) {
            var actual = indexes.stream().filter(i -> index.getString("name").equals(i.getString("name"))).findFirst().orElse(null);
            Document keys = index.get("keys", Document.class);
            if (actual == null) {
                if (!addMissing || indexes.stream().anyMatch(i -> keys.equals(i.get("key")))) throw mismatch();
                collection.createIndex(keys, new IndexOptions().name(index.getString("name")).unique(index.getBoolean("unique")));
                continue;
            }
            Document actualKeys = actual.get("key", Document.class);
            if (!keys.equals(actualKeys) || !new ArrayList<>(keys.keySet()).equals(new ArrayList<>(actualKeys.keySet()))
                    || index.getBoolean("unique") != actual.getBoolean("unique", false)
                    || actual.getBoolean("hidden", false) || actual.getBoolean("sparse", false)
                    || actual.containsKey("partialFilterExpression") || actual.containsKey("expireAfterSeconds")) throw mismatch();
            simple(actual);
        }
    }
    private static void simple(Document document) {
        var collation = document.get("collation", Document.class);
        if (collation != null && !"simple".equals(collation.getString("locale"))) throw mismatch();
    }
    static IllegalStateException mismatch() { return new IllegalStateException("Cleanup schema mismatch; explicit maintenance required"); }
}
