package web.tosunsaeng.billing.domain.payment;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class PaymentFoundationIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    MongoClient client;
    MongoDatabase db;
    PaymentStore store;
    PaymentProperties properties;
    PaymentAccountService accounts;
    PaymentRateLimiter limiter;
    static final Instant NOW = Instant.parse("2026-10-12T00:00:45Z");
    static final String USER = "11111111-1111-4111-8111-111111111111";
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        String name = "payment_" + UUID.randomUUID().toString().replace("-", "");
        store = new PaymentStore(client, new SimpleMongoClientDatabaseFactory(client, name));
        db = store.database();
        PaymentFoundationInitializer.initialize(db);
        properties = new PaymentProperties(); properties.setEnvironment("SANDBOX");
        properties.setLimiterKey(Base64.getEncoder().encodeToString(new byte[32]));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        accounts = new PaymentAccountService(store, properties, clock);
        limiter = new PaymentRateLimiter(store, properties, clock);
    }
    @AfterEach void close() {
        if (client != null) {
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); client.close(); // Only the per-test database inside disposable Mongo.
        }
    }
    @Test void concurrentCreationAndRetryKeepAccountAndRefStable() throws Exception {
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) calls.add(() -> accounts.getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId());
        Set<String> refs = new HashSet<>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (var result : pool.invokeAll(calls)) refs.add(result.get());
        }
        assertThat(refs).hasSize(1);
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("purchase_account_refs").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("payment_reconcile_cursors").countDocuments()).isEqualTo(1);
        Document before = db.getCollection("payment_accounts").find().first();
        accounts.getOrCreate(USER, NOW.plusSeconds(60));
        assertThat(db.getCollection("payment_accounts").find().first()).isEqualTo(before);
        assertThat(db.listCollectionNames().into(new ArrayList<>())).doesNotContain("trial_claims", "entitlement_grants", "payment_ledger");
    }
    @Test void userAndEnvironmentIsolation() {
        String first = accounts.getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId();
        String other = accounts.getOrCreate(UUID.randomUUID().toString(), NOW.plusSeconds(60)).purchaseAccountRefId();
        properties.setEnvironment("PRODUCTION");
        String production = accounts.getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId();
        assertThat(Set.of(first, other, production)).hasSize(3);
    }
    @Test void withdrawnBeforeCreationOrAfterCreationCannotIssueReference() {
        var key = new Document("environment", "SANDBOX").append("userId", USER);
        db.getCollection("payment_account_lifecycles").insertOne(new Document(key).append("state", "WITHDRAWN").append("version", 1L));
        assertThatThrownBy(() -> accounts.getOrCreate(USER, NOW.plusSeconds(60)))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo("PAYMENT_ACCOUNT_WITHDRAWN"));
        assertThat(db.getCollection("payment_accounts").countDocuments()).isZero();
        String second = UUID.randomUUID().toString();
        accounts.getOrCreate(second, NOW.plusSeconds(60));
        db.getCollection("payment_account_lifecycles").updateOne(new Document("userId", second),
                new Document("$set", new Document("state", "WITHDRAWN")));
        assertThatThrownBy(() -> accounts.getOrCreate(second, NOW.plusSeconds(60))).isInstanceOf(PaymentException.class);
    }
    @Test void missingCursorRollsBackFenceAndNeverRotatesReference() {
        String ref = accounts.getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId();
        Document before = db.getCollection("payment_account_lifecycles").find().first();
        db.getCollection("payment_reconcile_cursors").deleteMany(new Document());
        assertThatThrownBy(() -> accounts.getOrCreate(USER, NOW.plusSeconds(60))).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("payment_account_lifecycles").find().first()).isEqualTo(before);
        assertThat(db.getCollection("payment_accounts").find().first().getString("activeRefId")).isEqualTo(ref);
    }
    @Test void activityDoesNotOverwriteWorkerLeaseOrBackoff() {
        accounts.getOrCreate(USER, NOW.plusSeconds(60));
        var cursors = db.getCollection("payment_reconcile_cursors");
        Date future = Date.from(NOW.plusSeconds(3600));
        cursors.updateOne(new Document(), new Document("$set", new Document("leaseOwner", "fake-worker")
                .append("notBefore", future).append("nextAttemptAt", future)));
        Document before = cursors.find().first();
        new PaymentAccountService(store, properties, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .getOrCreate(USER, NOW.plusSeconds(60));
        before.put("lastActivityAt", Date.from(NOW.plusSeconds(1)));
        assertThat(cursors.find().first()).isEqualTo(before);
    }
    @Test void expiredAuthorizationCreatesNothing() {
        assertThatThrownBy(() -> accounts.getOrCreate(USER, NOW)).isInstanceOfSatisfying(PaymentException.class,
                e -> assertThat(e.status()).isEqualTo(401));
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isZero();
    }
    @Test void inactiveReferenceIsNotReplacedWithAnotherId() {
        String ref = accounts.getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId();
        db.getCollection("purchase_account_refs").updateOne(new Document("_id", ref),
                new Document("$set", new Document("active", false)));
        assertThatThrownBy(() -> accounts.getOrCreate(USER, NOW.plusSeconds(60))).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("purchase_account_refs").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("payment_accounts").find().first().getString("activeRefId")).isEqualTo(ref);
    }
    @Test void failedInsertRollsBackAllDocuments() {
        // Test-only validation rejects the cursor, after lifecycle/account/ref insert attempts.
        db.runCommand(new Document("collMod", "payment_reconcile_cursors")
                .append("validator", new Document("impossible", new Document("$exists", true))));
        assertThatThrownBy(() -> accounts.getOrCreate(USER, NOW.plusSeconds(60))).isInstanceOf(PaymentException.class);
        for (String collection : List.of("payment_accounts", "purchase_account_refs", "payment_account_lifecycles", "payment_reconcile_cursors"))
            assertThat(db.getCollection(collection).countDocuments()).isZero();
    }
    @Test void limiterSharesAtomicLimitAndReturnsRemainingWindow() throws Exception {
        List<Callable<Long>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) calls.add(() -> limiter.acquire(true, USER));
        List<Long> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (var result : pool.invokeAll(calls)) results.add(result.get());
        }
        assertThat(results.stream().filter(v -> v == 0).count()).isEqualTo(10);
        assertThat(results.stream().filter(v -> v == 15).count()).isEqualTo(10);
        Document bucket = db.getCollection("payment_rate_limits").find().first();
        assertThat(bucket).doesNotContainKey("userId");
        assertThat(bucket.getLong("count")).isEqualTo(10);
        assertThat(limiter.acquire(false, USER)).isZero();
        var nextMinute = new PaymentRateLimiter(store, properties, Clock.fixed(NOW.plusSeconds(15), ZoneOffset.UTC));
        assertThat(nextMinute.acquire(true, USER)).isZero(); // Old TTL row still exists.
    }
    @Test void initializerIsIdempotentAndFailsClosedOnOptionsMismatch() {
        PaymentFoundationInitializer.initialize(db);
        db.getCollection("payment_accounts").dropIndex("ux_payment_account_user"); // Disposable test fixture only.
        db.getCollection("payment_accounts").createIndex(new Document("environment", 1).append("userId", 1),
                new IndexOptions().name("ux_payment_account_user").unique(false));
        assertThatThrownBy(() -> PaymentFoundationInitializer.initialize(db)).isInstanceOf(IllegalStateException.class);
    }
    @Test void catalogEmptyIsSuccessButBrokenMappingIsNotEmpty() {
        var catalog = new PaymentCatalogService(store, properties);
        assertThat(catalog.read().products()).isEmpty();
        Document row = product();
        db.getCollection("payment_products").insertOne(row);
        assertThat(catalog.read().products().getFirst().storeProducts()).hasSize(1);
        db.getCollection("payment_products").updateOne(new Document(), new Document("$unset", new Document("packageId", 1)));
        assertThatThrownBy(catalog::read).isInstanceOf(PaymentException.class);
    }
    @Test void transientTransactionAndUnknownCommitLabelsConverge() {
        for (String label : List.of("TransientTransactionError", "UnknownTransactionCommitResult")) {
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                    .append("data", new Document("failCommands", List.of(label.equals("TransientTransactionError") ? "insert" : "commitTransaction"))
                            .append("errorCode", label.equals("TransientTransactionError") ? 112 : 1)
                            .append("errorLabels", List.of(label))));
            String user = UUID.randomUUID().toString();
            String ref = accounts.getOrCreate(user, NOW.plusSeconds(60)).purchaseAccountRefId();
            assertThat(accounts.getOrCreate(user, NOW.plusSeconds(60)).purchaseAccountRefId()).isEqualTo(ref);
        }
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(2);
    }
    static Document product() {
        return new Document("environment", "SANDBOX").append("saleEnabled", true).append("offerCode", "PREMIUM_1D")
                .append("catalogVersion", 1L).append("durationSeconds", 86400L).append("referencePriceKrw", 9000L)
                .append("referenceCurrency", "KRW").append("store", "APP_STORE").append("appId", "fake-app")
                .append("storeProductId", "fake-product").append("rcProductId", "fake-rc-product").append("packageId", "fake-package");
    }
}
