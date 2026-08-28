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

    public static final int SCHEMA_VERSION = 2;
    public static final String INBOX_COLLECTION = "inbound_event_inbox";
    public static final String ELIGIBILITY_COLLECTION = "trial_eligibility";
    public static final String CLAIM_COLLECTION = "trial_claims";
    public static final String ALIAS_COLLECTION = "trial_candidate_aliases";
    public static final String SUBJECT_LINK_COLLECTION = "billing_subject_links";
    public static final String GRANT_COLLECTION = "entitlement_grants";
    public static final String LEDGER_COLLECTION = "entitlement_ledger";
    public static final String RESERVATION_COLLECTION = "reservations";
    public static final String ALLOCATION_COLLECTION = "reservation_allocations";
    public static final String COMMAND_COLLECTION = "idempotency_commands";
    public static final String ATTEMPT_GROUP_COLLECTION = "attempt_groups";
    public static final String ATTEMPT_SESSION_COLLECTION = "attempt_sessions";

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
        ensureCollection(CLAIM_COLLECTION);
        ensureCollection(ALIAS_COLLECTION);
        ensureCollection(SUBJECT_LINK_COLLECTION);
        ensureCollection(GRANT_COLLECTION);
        ensureCollection(LEDGER_COLLECTION);
        ensureCollection(RESERVATION_COLLECTION);
        ensureCollection(ALLOCATION_COLLECTION);
        ensureCollection(COMMAND_COLLECTION);
        ensureCollection(ATTEMPT_GROUP_COLLECTION);
        ensureCollection(ATTEMPT_SESSION_COLLECTION);

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
        ensureIndex(CLAIM_COLLECTION, new ExpectedIndex(
                "ix_claim_retention_state",
                new Document("retentionExpiresAt", 1).append("state", 1),
                false,
                null,
                null
        ));
        ensureIndex(ALIAS_COLLECTION, new ExpectedIndex(
                "ux_active_trial_candidate",
                new Document("benefitType", 1)
                        .append("keyVersion", 1)
                        .append("candidate", 1),
                true,
                null,
                new Document("active", true)
        ));
        ensureIndex(ALIAS_COLLECTION, new ExpectedIndex(
                "ix_alias_active_expiry",
                new Document("active", 1).append("retentionExpiresAt", 1),
                false,
                null,
                null
        ));
        ensureIndex(ALIAS_COLLECTION, new ExpectedIndex(
                "ix_alias_claim",
                new Document("trialClaimId", 1),
                false,
                null,
                null
        ));
        ensureIndex(SUBJECT_LINK_COLLECTION, new ExpectedIndex(
                "ux_subject_link_claim",
                new Document("trialClaimId", 1),
                true,
                null,
                null
        ));
        ensureIndex(SUBJECT_LINK_COLLECTION, new ExpectedIndex(
                "ix_subject_link_user_active",
                new Document("userId", 1).append("active", 1),
                false,
                null,
                null
        ));
        ensureIndex(SUBJECT_LINK_COLLECTION, new ExpectedIndex(
                "ix_subject_link_expiry",
                new Document("active", 1).append("retentionExpiresAt", 1),
                false,
                null,
                null
        ));
        ensureIndex(GRANT_COLLECTION, new ExpectedIndex(
                "ux_grant_source_type",
                new Document("sourceType", 1).append("sourceId", 1).append("grantType", 1),
                true,
                null,
                null
        ));
        ensureIndex(LEDGER_COLLECTION, new ExpectedIndex(
                "ux_ledger_dedupe",
                new Document("dedupeKey", 1),
                true,
                null,
                null
        ));
        ensureIndex(LEDGER_COLLECTION, new ExpectedIndex(
                "ux_ledger_aggregate_sequence",
                new Document("aggregateType", 1).append("aggregateId", 1).append("sequence", 1),
                true,
                null,
                null
        ));
        ensureIndex(RESERVATION_COLLECTION, new ExpectedIndex(
                "ux_reservation_subject_operation",
                new Document("subjectRefId", 1).append("operationId", 1),
                true,
                null,
                null
        ));
        ensureIndex(RESERVATION_COLLECTION, new ExpectedIndex(
                "ux_active_reservation_subject",
                new Document("subjectRefId", 1),
                true,
                null,
                new Document("activeGuard", true)
        ));
        ensureIndex(RESERVATION_COLLECTION, new ExpectedIndex(
                "ix_reservation_status_expiry",
                new Document("status", 1).append("expiresAt", 1),
                false,
                null,
                null
        ));
        ensureIndex(ALLOCATION_COLLECTION, new ExpectedIndex(
                "ux_allocation_reservation_grant",
                new Document("reservationId", 1).append("grantId", 1),
                true,
                null,
                null
        ));
        ensureIndex(COMMAND_COLLECTION, new ExpectedIndex(
                "ux_command_scope_operation_type",
                new Document("callerService", 1)
                        .append("userId", 1)
                        .append("operationId", 1)
                        .append("commandType", 1),
                true,
                null,
                null
        ));
        ensureIndex(COMMAND_COLLECTION, new ExpectedIndex(
                "ux_active_create_command_user",
                new Document("callerService", 1).append("userId", 1),
                true,
                null,
                new Document("active", true).append("commandType", "RESERVE")
        ));
        ensureIndex(COMMAND_COLLECTION, new ExpectedIndex(
                "ttl_terminal_command_purge_at",
                new Document("purgeAt", 1),
                false,
                0L,
                null
        ));
        ensureIndex(ATTEMPT_GROUP_COLLECTION, new ExpectedIndex(
                "ux_open_group_subject",
                new Document("subjectRefId", 1),
                true,
                null,
                new Document("openGuard", true)
        ));
        ensureIndex(ATTEMPT_GROUP_COLLECTION, new ExpectedIndex(
                "ix_group_claim_created",
                new Document("trialClaimId", 1).append("createdAt", -1),
                false,
                null,
                null
        ));
        ensureIndex(ATTEMPT_SESSION_COLLECTION, new ExpectedIndex(
                "ux_attempt_session_id",
                new Document("sessionId", 1),
                true,
                null,
                null
        ));
        ensureIndex(ATTEMPT_SESSION_COLLECTION, new ExpectedIndex(
                "ux_session_group_operation",
                new Document("attemptGroupId", 1).append("operationId", 1),
                true,
                null,
                null
        ));
        ensureIndex(ATTEMPT_SESSION_COLLECTION, new ExpectedIndex(
                "ux_active_session_subject",
                new Document("subjectRefId", 1),
                true,
                null,
                new Document("activeGuard", true)
        ));
        ensureIndex(ATTEMPT_SESSION_COLLECTION, new ExpectedIndex(
                "ix_session_group_proposed",
                new Document("attemptGroupId", 1).append("proposedAt", -1),
                false,
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
