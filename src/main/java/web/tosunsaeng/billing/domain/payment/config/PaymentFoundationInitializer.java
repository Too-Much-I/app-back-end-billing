package web.tosunsaeng.billing.domain.payment.config;

import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.application.PaymentCatalogService;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Additive subset only: does not advance core schema v4 or initialize the remaining paid schema. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class PaymentFoundationInitializer implements ApplicationRunner {
    private final PaymentProperties properties;
    private final PaymentStore store;
    private final PaymentCatalogService catalog;
    public PaymentFoundationInitializer(PaymentProperties properties, PaymentStore store, PaymentCatalogService catalog) {
        this.properties = properties; this.store = store; this.catalog = catalog;
    }
    @Override public void run(ApplicationArguments args) {
        if (!properties.isCatalogEnabled() && !properties.isPurchaseAccountEnabled() && !properties.isWithdrawalEnabled()) return;
        initialize(store.database());
        if (properties.isWithdrawalEnabled()) initializeWithdrawal(store.database());
        if (properties.isCatalogEnabled()) catalog.read();
    }
    public static void initialize(MongoDatabase db) {
        initialize(db, "/payment-foundation-indexes.json");
    }
    public static void initializeWithdrawal(MongoDatabase db) {
        initialize(db, "/payment-withdrawal-indexes.json");
    }
    private static void initialize(MongoDatabase db, String resource) {
        inspect(db, resource, true);
    }
    public static void verify(MongoDatabase db) {
        inspect(db, "/payment-foundation-indexes.json", false);
        inspect(db, "/payment-withdrawal-indexes.json", false);
    }
    private static void inspect(MongoDatabase db, String resource, boolean create) {
        try (var input = PaymentFoundationInitializer.class.getResourceAsStream(resource)) {
            var manifest = new ObjectMapper().readTree(Objects.requireNonNull(input));
            for (var row : manifest.get("indexes")) {
                String collection = row.get("collection").asText(), name = row.get("name").asText();
                Document keys = Document.parse(row.get("keys").toString());
                boolean unique = row.get("unique").asBoolean();
                Document partial = row.has("partialFilterExpression") ? Document.parse(row.get("partialFilterExpression").toString()) : null;
                Long ttl = row.has("expireAfterSeconds") ? row.get("expireAfterSeconds").longValue() : null;
                var metadata = db.listCollections().filter(new Document("name", collection)).first();
                if (metadata == null) { if (!create) mismatch(); db.createCollection(collection); }
                else {
                    var options = metadata.get("options", Document.class);
                    Document collation = options == null ? null : options.get("collation", Document.class);
                    if (collation != null && !"simple".equals(collation.getString("locale"))) mismatch();
                }
                var target = db.getCollection(collection);
                var existing = target.listIndexes().into(new ArrayList<>());
                Document named = existing.stream().filter(i -> name.equals(i.getString("name"))).findFirst().orElse(null);
                if (named == null) {
                    if (!create) mismatch();
                    if (existing.stream().anyMatch(i -> keys.equals(i.get("key")))) mismatch();
                    IndexOptions options = new IndexOptions().name(name).unique(unique);
                    if (partial != null) options.partialFilterExpression(partial);
                    if (ttl != null) options.expireAfter(ttl, TimeUnit.SECONDS);
                    target.createIndex(keys, options);
                    named = target.listIndexes().into(new ArrayList<>()).stream().filter(i -> name.equals(i.getString("name"))).findFirst().orElseThrow();
                }
                Document actualKeys = named.get("key", Document.class);
                Document collation = named.get("collation", Document.class);
                if (!keys.equals(actualKeys) || !new ArrayList<>(keys.keySet()).equals(new ArrayList<>(actualKeys.keySet()))
                        || unique != named.getBoolean("unique", false) || named.getBoolean("sparse", false)
                        || named.getBoolean("hidden", false) || !Objects.equals(partial, named.get("partialFilterExpression"))
                        || !Objects.equals(ttl, named.get("expireAfterSeconds") instanceof Number n ? n.longValue() : null)
                        || collation != null && !"simple".equals(collation.getString("locale"))) mismatch();
            }
        } catch (Exception error) { throw new IllegalStateException("Payment foundation index validation failed"); }
    }
    private static void mismatch() { throw new IllegalStateException("Payment index mismatch; explicit migration required"); }
}
