package web.tosunsaeng.billing.domain.payment.recovery;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bson.Document;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Additive DDL is explicit; application startup only verifies. */
@Component
public final class PaymentSnapshotSchema implements ApplicationRunner {
    private final PaymentStore store;
    private final PaymentProperties properties;
    public PaymentSnapshotSchema(PaymentStore store, PaymentProperties properties) {
        this.store = store; this.properties = properties;
    }
    @Override public void run(ApplicationArguments args) {
        if (!properties.getRecovery().isSnapshotEnabled()) return;
        if (!properties.getRecovery().isFenceRequired()) throw mismatch();
        PaymentRecoverySchema.verify(store.database());
        verify(store.database());
        Document control = store.database().getCollection(PaymentRecoveryFence.COLLECTION)
                .find(new Document("environment", properties.getEnvironment()).append("consumer", "billing")).first();
        if (control == null || !Long.valueOf(2).equals(control.get("schemaVersion"))) throw mismatch();
    }
    static Document manifest() {
        try (var in = PaymentSnapshotSchema.class.getResourceAsStream("/payment-recovery-snapshot-schema.json")) {
            return Document.parse(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception error) { throw mismatch(); }
    }
    public static void initializeEmpty(MongoDatabase db) {
        var manifest = manifest();
        var validators = manifest.get("validators", Document.class);
        for (var entry : validators.entrySet()) {
            if (db.listCollections().filter(new Document("name", entry.getKey())).first() == null)
                db.createCollection(entry.getKey(), new CreateCollectionOptions().validationOptions(new ValidationOptions()
                        .validator((Document) entry.getValue()).validationLevel(ValidationLevel.STRICT)
                        .validationAction(ValidationAction.ERROR)));
        }
        for (Document index : manifest.getList("indexes", Document.class)) {
            var collection = db.getCollection(index.getString("collection"));
            var existing = collection.listIndexes().into(new ArrayList<>());
            if (existing.stream().noneMatch(i -> index.getString("name").equals(i.getString("name")))) {
                if (existing.stream().anyMatch(i -> index.get("keys").equals(i.get("key")))) throw mismatch();
                collection.createIndex(index.get("keys", Document.class), new IndexOptions()
                        .name(index.getString("name")).unique(index.getBoolean("unique")));
            }
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try {
            var manifest = manifest();
            for (var entry : manifest.get("validators", Document.class).entrySet()) {
                var meta = db.listCollections().filter(new Document("name", entry.getKey())).first();
                if (meta == null) throw mismatch();
                var options = meta.get("options", Document.class);
                if (options == null || !entry.getValue().equals(options.get("validator"))
                        || !"strict".equals(options.getString("validationLevel"))
                        || !"error".equals(options.getString("validationAction"))) throw mismatch();
                simple(options);
            }
            for (Document index : manifest.getList("indexes", Document.class)) {
                var actual = db.getCollection(index.getString("collection")).listIndexes().into(new ArrayList<>())
                        .stream().filter(i -> index.getString("name").equals(i.getString("name"))).findFirst().orElseThrow();
                var keys = index.get("keys", Document.class);
                var actualKeys = actual.get("key", Document.class);
                if (!keys.equals(actualKeys) || !new ArrayList<>(keys.keySet()).equals(new ArrayList<>(actualKeys.keySet()))
                        || index.getBoolean("unique") != actual.getBoolean("unique", false)
                        || actual.getBoolean("hidden", false) || actual.getBoolean("sparse", false)
                        || actual.containsKey("partialFilterExpression") || actual.containsKey("expireAfterSeconds")) throw mismatch();
                simple(actual);
            }
        } catch (Exception error) { throw mismatch(); }
    }
    private static void simple(Document options) {
        var collation = options.get("collation", Document.class);
        if (collation != null && !"simple".equals(collation.getString("locale"))) throw mismatch();
    }
    private static IllegalStateException mismatch() {
        return new IllegalStateException("Recovery snapshot schema mismatch; explicit migration required");
    }
}
