package web.tosunsaeng.billing.domain.payment.cleanup;

import com.mongodb.client.*;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CleanupLocalReaderIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    MongoClient client;
    PaymentStore store;
    final Subject subject = new Subject("SANDBOX", "user", "life", 1, "account", 1L,
            List.of("ref"), Instant.EPOCH, 0, Instant.EPOCH, "epoch");
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        store = new PaymentStore(client, new SimpleMongoClientDatabaseFactory(client, "cleanup_reader_" + UUID.randomUUID().toString().replace("-", "")));
        // Reader tests isolate raw evidence queries. Exact schema installation is covered by existing core tests.
        for (String name : List.of("purchases", "subscription_entitlements", "payment_timelines", "payment_purchase_cases",
                "payment_provider_inbox", "payment_sync_commands", "payment_reconcile_cursors")) store.database().createCollection(name);
    }
    @AfterEach void close() { if (client != null) { store.database().drop(); client.close(); } }
    Local read() { return store.transaction(s -> new CleanupLocalReader(store).read(s, subject)); }
    void insert(String collection, Document row) { store.database().getCollection(collection).insertOne(row); }
    @Test void emptyLocalDatabaseIsOnlyLocalNone() { assertThat(read()).isEqualTo(Local.NONE); }
    @ParameterizedTest @ValueSource(strings = {"VERIFIED", "REFUNDED", "REVOKED"})
    void allPurchaseStatesPreventDeletion(String state) {
        insert("purchases", new Document("accountId", "account").append("purchaseState", state));
        assertThat(read()).isEqualTo(Local.FOUND);
    }
    @ParameterizedTest @ValueSource(strings = {"subscription_entitlements", "payment_timelines"})
    void otherFinancialEvidencePreventsDeletion(String collection) {
        insert(collection, new Document("accountId", "account")); assertThat(read()).isEqualTo(Local.FOUND);
    }
    @ParameterizedTest @ValueSource(strings = {"payment_purchase_cases", "payment_provider_inbox"})
    void unassignedEvidenceBlocksRatherThanAssumingAnotherOwner(String collection) {
        insert(collection, new Document("environment", "SANDBOX").append("accountId", null).append("state", "RECEIVED"));
        assertThat(read()).isEqualTo(Local.PENDING);
    }
    @Test void inactiveReferenceEvidenceIsIncluded() {
        insert("payment_provider_inbox", new Document("environment", "SANDBOX").append("accountId", "unresolved")
                .append("state", "QUARANTINED").append("normalizedObservation", new Document("ownerRefId", "ref")));
        assertThat(read()).isEqualTo(Local.PENDING);
    }
    @Test void ignoredOtherEnvironmentAndTerminalSyncDoNotBlock() {
        insert("payment_provider_inbox", new Document("environment", "SANDBOX").append("accountId", null).append("state", "IGNORED"));
        insert("payment_purchase_cases", new Document("environment", "PRODUCTION").append("accountId", null));
        insert("payment_sync_commands", new Document("environment", "SANDBOX").append("userId", "user").append("state", "REJECTED").append("counted", false));
        assertThat(read()).isEqualTo(Local.NONE);
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "REVIEW_REQUIRED", "BLOCKED_AUTH"})
    void unresolvedSyncPreventsDeletion(String state) {
        insert("payment_sync_commands", new Document("environment", "SANDBOX").append("userId", "user").append("state", state));
        assertThat(read()).isEqualTo(Local.PENDING);
    }
    @Test void lookupLeasePreventsDeletion() {
        insert("payment_reconcile_cursors", new Document("accountId", "account").append("kind", "ACCOUNT_LOOKUP")
                .append("lookup", new Document("owner", "worker")));
        assertThat(read()).isEqualTo(Local.PENDING);
    }
}
