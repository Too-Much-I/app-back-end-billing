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

/** Startup verifies only. DDL and initial epoch installation are explicit rollout steps. */
@Component
public final class PaymentRecoverySchema implements ApplicationRunner {
    public static final String INDEX = "ux_payment_recovery_control";
    private final PaymentStore store;
    private final PaymentProperties properties;
    public PaymentRecoverySchema(PaymentStore store, PaymentProperties properties) {
        this.store = store; this.properties = properties;
    }
    @Override public void run(ApplicationArguments args) {
        if (!properties.getRecovery().isFenceRequired()) return;
        verify(store.database());
        new PaymentRecoveryFence(store, properties).capture();
    }
    public static Document validator() {
        try (var in = PaymentRecoverySchema.class.getResourceAsStream("/payment-recovery-control-schema.json")) {
            return Document.parse(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception error) { throw mismatch(); }
    }
    private static Document keys() { return new Document("environment", 1).append("consumer", 1); }
    /** Explicit additive migration helper; never called by startup or a request. Does not install an epoch. */
    public static void initializeEmpty(MongoDatabase db) {
        if (db.listCollections().filter(new Document("name", PaymentRecoveryFence.COLLECTION)).first() == null) {
            db.createCollection(PaymentRecoveryFence.COLLECTION, new CreateCollectionOptions()
                    .validationOptions(new ValidationOptions().validator(validator())
                            .validationLevel(ValidationLevel.STRICT).validationAction(ValidationAction.ERROR)));
            db.getCollection(PaymentRecoveryFence.COLLECTION).createIndex(keys(),
                    new IndexOptions().name(INDEX).unique(true));
        }
        verify(db);
    }
    public static void verify(MongoDatabase db) {
        try {
            var meta = db.listCollections().filter(new Document("name", PaymentRecoveryFence.COLLECTION)).first();
            if (meta == null) throw mismatch();
            var options = meta.get("options", Document.class);
            if (options == null || !validator().equals(options.get("validator"))
                    || !"strict".equals(options.getString("validationLevel"))
                    || !"error".equals(options.getString("validationAction"))) throw mismatch();
            var collation = options.get("collation", Document.class);
            if (collation != null && !"simple".equals(collation.getString("locale"))) throw mismatch();
            var index = db.getCollection(PaymentRecoveryFence.COLLECTION).listIndexes().into(new ArrayList<>())
                    .stream().filter(i -> INDEX.equals(i.getString("name"))).findFirst().orElseThrow();
            Document actual = index.get("key", Document.class);
            if (!keys().equals(actual) || !new ArrayList<>(keys().keySet()).equals(new ArrayList<>(actual.keySet()))
                    || !index.getBoolean("unique", false) || index.getBoolean("sparse", false)
                    || index.getBoolean("hidden", false) || index.containsKey("expireAfterSeconds")
                    || index.containsKey("partialFilterExpression")) throw mismatch();
            var indexCollation = index.get("collation", Document.class);
            if (indexCollation != null && !"simple".equals(indexCollation.getString("locale"))) throw mismatch();
        } catch (Exception error) { throw mismatch(); }
    }
    private static IllegalStateException mismatch() {
        return new IllegalStateException("Recovery control schema mismatch; explicit migration required");
    }
}
