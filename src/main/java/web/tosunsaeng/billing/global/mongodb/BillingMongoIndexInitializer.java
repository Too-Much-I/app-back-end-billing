package web.tosunsaeng.billing.global.mongodb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.config.BillingMongoProperties;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BillingMongoIndexInitializer implements ApplicationRunner {

    public static final int SCHEMA_VERSION = 1;
    public static final String INBOX_COLLECTION = "inbound_event_inbox";
    public static final String ELIGIBILITY_COLLECTION = "trial_eligibility";

    private final MongoTemplate mongoTemplate;
    private final BillingMongoProperties properties;

    public BillingMongoIndexInitializer(
            MongoTemplate mongoTemplate,
            BillingMongoProperties properties
    ) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isInitializeIndexes()) {
            return;
        }
        if (properties.getSchemaVersion() != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported Billing Mongo schema version.");
        }
        ensureCollection(INBOX_COLLECTION);
        ensureCollection(ELIGIBILITY_COLLECTION);

        Document identityRevisionFilter = new Document("producer", "identity")
                .append("consumerScopeId", new Document("$exists", true))
                .append("userId", new Document("$exists", true))
                .append("bindingRevision", new Document("$exists", true));
        ensureIndex(INBOX_COLLECTION, new ExpectedIndex(
                "ux_inbox_event_id",
                new Document("eventId", 1),
                true,
                null,
                null
        ));
        ensureIndex(INBOX_COLLECTION, new ExpectedIndex(
                "ux_inbox_identity_scope_user_revision",
                new Document("producer", 1)
                        .append("consumerScopeId", 1)
                        .append("userId", 1)
                        .append("bindingRevision", 1),
                true,
                null,
                identityRevisionFilter
        ));
        ensureIndex(INBOX_COLLECTION, new ExpectedIndex(
                "ttl_inbox_purge_at",
                new Document("purgeAt", 1),
                false,
                0L,
                null
        ));
        ensureIndex(ELIGIBILITY_COLLECTION, new ExpectedIndex(
                "ux_trial_scope_user",
                new Document("consumerScopeId", 1).append("userId", 1),
                true,
                null,
                null
        ));
        ensureIndex(ELIGIBILITY_COLLECTION, new ExpectedIndex(
                "ix_trial_key_version",
                new Document("consumerScopeId", 1).append("candidates.keyVersion", 1),
                false,
                null,
                null
        ));
    }

    private void ensureCollection(String collectionName) {
        if (!mongoTemplate.collectionExists(collectionName)) {
            mongoTemplate.createCollection(collectionName);
        }
    }

    private void ensureIndex(String collectionName, ExpectedIndex expected) {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        Document existing = null;
        for (Document index : collection.listIndexes()) {
            if (expected.name().equals(index.getString("name"))) {
                existing = index;
                break;
            }
        }
        if (existing != null) {
            validate(existing, expected);
            return;
        }

        IndexOptions options = new IndexOptions()
                .name(expected.name())
                .unique(expected.unique());
        if (expected.expireAfterSeconds() != null) {
            options.expireAfter(expected.expireAfterSeconds(), TimeUnit.SECONDS);
        }
        if (expected.partialFilterExpression() != null) {
            options.partialFilterExpression(expected.partialFilterExpression());
        }
        collection.createIndex(expected.key(), options);
    }

    private static void validate(Document actual, ExpectedIndex expected) {
        Document actualKey = actual.get("key", Document.class);
        if (actualKey == null || !orderedEntries(actualKey).equals(orderedEntries(expected.key()))) {
            throw mismatch(expected.name(), "key order");
        }
        boolean actualUnique = Boolean.TRUE.equals(actual.getBoolean("unique"));
        if (actualUnique != expected.unique()) {
            throw mismatch(expected.name(), "unique option");
        }
        Number actualExpiry = actual.get("expireAfterSeconds", Number.class);
        Long actualExpiryValue = actualExpiry == null ? null : actualExpiry.longValue();
        if (!java.util.Objects.equals(actualExpiryValue, expected.expireAfterSeconds())) {
            throw mismatch(expected.name(), "TTL option");
        }
        Document actualPartial = actual.get("partialFilterExpression", Document.class);
        if (!java.util.Objects.equals(actualPartial, expected.partialFilterExpression())) {
            throw mismatch(expected.name(), "partial filter");
        }
    }

    private static List<Map.Entry<String, Object>> orderedEntries(Document document) {
        return new ArrayList<>(document.entrySet());
    }

    private static IllegalStateException mismatch(String indexName, String option) {
        return new IllegalStateException(
                "Mongo index " + indexName + " has an unexpected " + option + "."
        );
    }

    private record ExpectedIndex(
            String name,
            Document key,
            boolean unique,
            Long expireAfterSeconds,
            Document partialFilterExpression
    ) {
    }
}
