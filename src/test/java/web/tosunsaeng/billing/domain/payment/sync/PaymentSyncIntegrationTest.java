package web.tosunsaeng.billing.domain.payment.sync;

import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.mongodb.core.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.domain.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.worker.*;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class PaymentSyncIntegrationTest {
    // Re-scope this disposable, purchase-empty fixture only; production migration never rewrites account scopes.
    void mixedProduction() {
        barrier("ALL_BLOCKED");
        RefundCoreSchema.upgradeUnderMaintenance(db, true);
        assertThatThrownBy(() -> MixedStorePurchaseSchema.upgradeUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        MixedStorePurchaseSchema.upgradeUnderMaintenance(db, true);
        PaymentSyncSchema.upgradeMixedUnderMaintenance(db, true);
        MixedStorePurchaseSchema.upgradeUnderMaintenance(db, true);
        PaymentSyncSchema.upgradeMixedUnderMaintenance(db, true);
        for (String collection : List.of(PaymentRecoveryFence.COLLECTION, "payment_accounts", "payment_account_lifecycles",
                "purchase_account_refs", "payment_products", "payment_reconcile_cursors"))
            db.getCollection(collection).updateMany(new Document("environment", "SANDBOX"), new Document("$set", new Document("environment", "PRODUCTION")));
        props.setEnvironment("PRODUCTION"); props.setSandboxPurchasesEnabled(true);
        props.getRefund().setProcessingEnabled(true);
        barrier("OPEN");
        core = new PurchaseCore(store, props, Clock.fixed(NOW, ZoneOffset.UTC), Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
        sync = new PaymentSyncService(store, props, Clock.fixed(NOW, ZoneOffset.UTC), lookup, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"));
        var factory = new SimpleMongoClientDatabaseFactory(client, db.getName());
        reader = new PaidEntitlementReader(new MongoTemplate(factory), props); snapshots = new EntitlementSnapshotExecutor(factory);
    }
    PurchaseObservation mixedObservation(String env, String event, String tx, boolean refund) {
        return new PurchaseObservation(env, PurchaseObservation.Store.PLAY_STORE, "fake.app",
                lookup.transaction(env, "PLAY_STORE", "fake.app", tx), lookup.version(), lookup.event(env, "fake-project", event),
                refund ? PurchaseObservation.Kind.REFUND : PurchaseObservation.Kind.PURCHASE, ref, PaymentOffer.PREMIUM_1D.name(), NOW,
                new PurchaseObservation.Money(java.math.BigDecimal.valueOf(100), "KRW"),
                refund ? new RefundEvidence(RefundEvidence.Source.REVENUECAT_API, "a".repeat(64), NOW, java.math.BigDecimal.valueOf(100), "KRW") : null);
    }
    void receiveApply(PurchaseObservation observation) {
        core.receive(observation); assertThat(core.apply(observation.providerEventKey())).isEqualTo(PurchaseCore.Outcome.APPLIED);
    }
    @Test void mixedStorePurchasesShareTimelineButRefundAndDuplicateStayPurchaseScoped() {
        mixedProduction();
        var real = mixedObservation("PRODUCTION", "same-event", "same-id", false);
        var sandbox = mixedObservation("SANDBOX", "same-event", "same-id", false);
        receiveApply(real); receiveApply(sandbox); receiveApply(sandbox);
        assertThat(db.getCollection("purchases").countDocuments()).isEqualTo(2);
        assertThat(db.getCollection("payment_timelines").countDocuments()).isEqualTo(1);
        assertThat(first("payment_timelines").getDate("timelineEnd").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(2)));
        assertThat(read(NOW).continuousAccessEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(2)));
        var testPurchase = db.getCollection("purchases").find(new Document("storeEnvironment", "SANDBOX")).first();
        assertThat(testPurchase.getString("environment")).isEqualTo("PRODUCTION");
        receiveApply(mixedObservation("SANDBOX", "refund-test", "same-id", true));
        assertThat(db.getCollection("purchases").find(new Document("transactionKey", real.transactionKey())).first().getString("accessState")).isEqualTo("ALLOWED");
        assertThat(db.getCollection("purchases").find(new Document("transactionKey", sandbox.transactionKey())).first().getString("accessState")).isEqualTo("REVOKED");
        assertThat(first("payment_timelines").getDate("timelineEnd").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(read(NOW).active()).isTrue();
        assertThat(read(NOW).continuousAccessEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }
    @Test void mixedSandboxSourceCompletesProductionSyncWithoutChangingPublicHint() {
        mixedProduction(); var request = request("sandbox-order");
        assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.PENDING);
        var observation = mixedObservation("SANDBOX", "sandbox-source", "sandbox-order", false);
        core.receive(observation);
        var worker = new PaymentSourceWorker(store, props, core, sync, Clock.fixed(NOW, ZoneOffset.UTC), () -> 0.0);
        assertThat(worker.tick(10).failed()).isZero();
        assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(pending()).isZero();
        assertThat(db.getCollection("purchases").countDocuments()).isEqualTo(1);
    }
    @Test void mixedSyncDoesNotGuessWhenSameStoreIdExistsInBothEnvironments() {
        mixedProduction();
        receiveApply(mixedObservation("PRODUCTION", "real", "collision", false));
        receiveApply(mixedObservation("SANDBOX", "test", "collision", false));
        assertThatThrownBy(() -> submit(request("collision"))).isInstanceOfSatisfying(PaymentException.class,
                e -> assertThat(e.code()).isEqualTo("PURCHASE_BINDING_CONFLICT"));
        assertThat(db.getCollection("purchases").countDocuments()).isEqualTo(2);
        assertThat(pending()).isZero();
    }
    @Test void revokedHistoricalSlotDoesNotBreakRemainingEntitlementReaderInSingleEnvironment() {
        barrier("ALL_BLOCKED"); RefundCoreSchema.upgradeUnderMaintenance(db, true); barrier("OPEN");
        props.getRefund().setProcessingEnabled(true);
        core = new PurchaseCore(store, props, Clock.fixed(NOW, ZoneOffset.UTC), Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
        receiveApply(mixedObservation("SANDBOX", "first", "first", false));
        receiveApply(mixedObservation("SANDBOX", "second", "second", false));
        receiveApply(mixedObservation("SANDBOX", "refund", "second", true));
        assertThat(read(NOW).active()).isTrue();
        assertThat(read(NOW).continuousAccessEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }
    @Test void mixedActivationReplayEnrichesOnlyUnboundHintAndKeepsOriginalRequest() {
        mixedProduction(); props.setSandboxPurchasesEnabled(false);
        sync = new PaymentSyncService(store, props, Clock.fixed(NOW, ZoneOffset.UTC), lookup, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"));
        var request = request("legacy-pending"); submit(request); var original = first(PaymentSyncSchema.COLLECTION);
        assertThat(original).doesNotContainKey("sandboxTransactionKey");
        props.setSandboxPurchasesEnabled(true);
        sync = new PaymentSyncService(store, props, Clock.fixed(NOW, ZoneOffset.UTC), lookup, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"));
        submit(request); var current = first(PaymentSyncSchema.COLLECTION);
        assertThat(current.get("payloadDigest")).isEqualTo(original.get("payloadDigest"));
        assertThat(current.get("createdAt")).isEqualTo(original.get("createdAt"));
        assertThat(current.get("attempts")).isEqualTo(original.get("attempts"));
        assertThat(pending()).isEqualTo(1);
        core.receive(mixedObservation("SANDBOX", "legacy-verified", "legacy-pending", false));
        assertThat(worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(10).failed()).isZero();
        assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
    }
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all");
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    static final String USER = "11111111-1111-4111-8111-111111111111";
    final PurchaseLookupKey lookup = new PurchaseLookupKey(new byte[32], "fake-v1");
    MongoClient client; MongoDatabase db; PaymentStore store; PaymentProperties props;
    PaymentSyncService sync; PurchaseCore core; String ref; PaidEntitlementReader reader; EntitlementSnapshotExecutor snapshots;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "sync_" + UUID.randomUUID().toString().replace("-", ""));
        store = new PaymentStore(client, factory); db = store.database();
        PaymentFoundationInitializer.initialize(db); PaymentFoundationInitializer.initializeWithdrawal(db); PaymentRecoverySchema.initializeEmpty(db);
        props = new PaymentProperties(); props.setEnvironment("SANDBOX"); props.getRecovery().setFenceRequired(true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", "control").append("environment", "SANDBOX")
                .append("consumer", "billing").append("schemaVersion", 4L).append("epoch", UUID.randomUUID().toString())
                .append("version", 1L).append("barrier", "ALL_BLOCKED"));
        PurchaseCoreSchema.installUnderMaintenance(db, true); PaymentSyncSchema.installUnderMaintenance(db, true);
        PurchaseReferenceSchema.installUnderMaintenance(db, true);
        PaymentAccountLookupSchema.installUnderMaintenance(db, true); RevenueCatBudgetSchema.installUnderMaintenance(db, true); barrier("OPEN");
        ref = new PaymentAccountService(store, props, Clock.fixed(NOW, ZoneOffset.UTC)).getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId();
        for (PaymentOffer offer : PaymentOffer.values()) db.getCollection("payment_products").insertOne(new Document("environment", "SANDBOX")
                .append("store", "PLAY_STORE").append("appId", "fake.app").append("storeProductId", offer.name())
                .append("rcProductId", "fake-rc-" + offer.name()).append("offerCode", offer.name()).append("catalogVersion", 1L)
                .append("durationSeconds", offer.durationSeconds).append("saleEnabled", false));
        core = new PurchaseCore(store, props, Clock.fixed(NOW, ZoneOffset.UTC), Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
        sync = new PaymentSyncService(store, props, Clock.fixed(NOW, ZoneOffset.UTC), lookup, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"));
        reader = new PaidEntitlementReader(new MongoTemplate(factory), props); snapshots = new EntitlementSnapshotExecutor(factory);
    }
    @AfterEach void close() { if (client != null) { db.drop(); client.close(); } } // Isolated test database only.
    void barrier(String b) { db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", b))); }
    Document first(String c) { return db.getCollection(c).find().first(); }
    long pending() { return first("payment_account_lifecycles").getLong("pendingEvidence"); }
    PaymentSyncRequest request(String tx) { return new PaymentSyncRequest(UUID.randomUUID().toString(), PurchaseObservation.Store.PLAY_STORE, tx); }
    PaymentSyncResult submit(PaymentSyncRequest r) { return sync.sync(USER, NOW.plusSeconds(60), r); }
    PurchaseObservation observation(String event, String tx, PaymentOffer offer, PurchaseObservation.Kind kind) {
        return new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app", lookup.transaction("SANDBOX", "PLAY_STORE", "fake.app", tx),
                lookup.version(), lookup.event("SANDBOX", "fake-project", event), kind, ref, offer.name(), NOW.minusSeconds(100), null);
    }
    PurchaseObservation purchase(String event, String tx) { return observation(event, tx, PaymentOffer.PREMIUM_1D, PurchaseObservation.Kind.PURCHASE); }
    void apply(PurchaseObservation o) { core.receive(o); core.apply(o.providerEventKey()); }
    PaidQueryResponse read(Instant at) { reader.prepare(); return snapshots.execute(() -> reader.read(USER, at)); }
    void withdraw() {
        var fence = new PaymentRecoveryFence(store, props); var ticket = fence.capture();
        store.transaction(s -> { fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PUSH);
            return new PaymentWithdrawalApplier(store, props, Clock.fixed(NOW, ZoneOffset.UTC)).applyFact(s, new WithdrawalFact(USER, NOW)); });
    }
    static class WorkerClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    PaymentSourceWorker worker(Clock clock) { return new PaymentSourceWorker(store, props, core, sync, clock, () -> 0.5); }
    PaymentAccountLookupWork lookupWork(Clock clock) { return new PaymentAccountLookupWork(store, props, clock, "fake-project", lookup, new PurchaseReferenceCipher("test-key", new byte[32])); }
    PaymentAccountLookupWorker lookupWorker(Clock clock, RevenueCatReadClient.Transport transport) {
        var decoder = new RevenueCatPurchaseDecoder("fake-project", props.getEnvironment(), lookup, Map.of("rc-day",
                new RevenueCatPurchaseDecoder.Product("fake.app", PurchaseObservation.Store.PLAY_STORE, "PREMIUM_1D")), clock, props.isSandboxPurchasesEnabled());
        var reader = new RevenueCatReadClient("fake-project", props.getEnvironment(), "fake-secret",
                new RevenueCatBudgetedTransport(new RevenueCatProjectBudget(store, "fake-project", clock), transport), decoder, clock);
        return new PaymentAccountLookupWorker(store, props, clock, "fake-project", lookup, new PurchaseReferenceCipher("test-key", new byte[32]), core, sync, reader, () -> 0);
    }
    Document apiPurchase(String tx) {
        return new Document("object", "purchase").append("id", "rc-" + tx).append("customer_id", ref).append("original_customer_id", ref)
                .append("product_id", "rc-day").append("store_purchase_identifier", tx).append("environment", "sandbox").append("store", "play_store")
                .append("ownership", "purchased").append("status", "owned").append("quantity", 1).append("purchased_at", NOW.minusSeconds(100).toEpochMilli());
    }
    RevenueCatReadClient.Response page(List<Document> items, String next) {
        String url = next == null ? null : "/v2/projects/fake-project/customers/" + ref + "/purchases?starting_after=" + next;
        return new RevenueCatReadClient.Response(200, Map.of("Content-Type", List.of("application/json")),
                new Document("object", "list").append("items", items).append("next_page", url).toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void accountLookupCoalescesHintsAndCompletesUsingRealDecoderAndBudget() {
        var clock = new WorkerClock(); submit(request("tx")); submit(request("tx")); submit(request("other"));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var worker = lookupWorker(clock, (uri, key) -> { calls.incrementAndGet(); return page(List.of(apiPurchase("tx"), apiPurchase("other")), null); });
        assertThat(worker.tick(100)).isEqualTo(new PaymentAccountLookupWorker.Tick(1, 1, 0, 0));
        assertThat(calls.get()).isEqualTo(1); assertThat(pending()).isZero();
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(2);
        assertThat(worker.tick(100).claimed()).isZero(); assertThat(calls.get()).isEqualTo(1);
    }
    @Test void mixedLookupUsesBothStorePagesAndRemembersSandboxReference() {
        mixedProduction(); var request = request("test-order"); submit(request);
        var clock = new WorkerClock(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        var worker = lookupWorker(clock, (uri, key) -> {
            calls.incrementAndGet();
            return page(uri.getQuery().contains("environment=sandbox") ? List.of(apiPurchase("test-order")) : List.of(), null);
        });
        worker.tick(10); clock.now = NOW.plusSeconds(2); worker.tick(10);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(pending()).isZero();
        assertThat(db.getCollection("payment_purchase_references").countDocuments()).isEqualTo(1);
    }
    PaymentRetentionWorker retention(Clock clock, boolean delete) {
        var agedCore = new PurchaseCore(store, props, clock, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
        return new PaymentRetentionWorker(store, props, agedCore, clock, delete);
    }
    org.springframework.boot.test.context.runner.ApplicationContextRunner publicPipeline(String budgetDb) {
        var mongo = new web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties();
        mongo.setRequireTransactions(true); mongo.setInitializeIndexes(true);
        props.setSyncEnabled(true); props.setLimiterKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(PaymentProviderWorkerConfiguration.class)
                .withBean(PaymentStore.class, () -> store).withBean(MongoClient.class, () -> client,
                        definition -> ((org.springframework.beans.factory.support.AbstractBeanDefinition) definition).setDestroyMethodName(""))
                .withBean(PaymentProperties.class, () -> props).withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withBean(web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties.class, () -> mongo)
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .withPropertyValues("billing.payment.sync-enabled=true", "billing.payment.provider.worker.enabled=true",
                        "billing.payment.provider.project=fake-project", "billing.payment.provider.lookup-key=" + Base64.getEncoder().encodeToString(new byte[32]),
                        "billing.payment.provider.lookup-key-version=fake-v1", "billing.payment.provider.worker.reference-key=" + Base64.getEncoder().encodeToString(new byte[32]),
                        "billing.payment.provider.worker.reference-key-id=test-key", "billing.payment.provider.worker.api-key=fake-api-key",
                        "billing.payment.provider.worker.budget-database=" + budgetDb,
                        "billing.payment.provider.apps[fake.app].store=PLAY_STORE",
                        "billing.payment.provider.apps[fake.app].products=PREMIUM_1D,PREMIUM_3D,PREMIUM_7D,PREMIUM_14D,PREMIUM_28D");
    }
    @Test void publicSyncUsesValidatedRuntimeServiceAndRealDatabaseThroughMvc() {
        String budgetDb = "budget_" + UUID.randomUUID().toString().replace("-", "");
        RevenueCatBudgetSchema.installUnderMaintenance(client.getDatabase(budgetDb), true);
        try {
            publicPipeline(budgetDb).run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(PaymentSyncService.class);
                sync = c.getBean(PaymentSyncService.class);
                assertThat(sync).isSameAs(c.getBean(PaymentProviderWorkerRuntime.class).syncService());
                // ApplicationContextRunner emits no ApplicationReady event. Real HTTP transport is never dispatched.
                var services = new org.springframework.beans.factory.support.StaticListableBeanFactory(); services.addBean("sync", sync);
                var query = new web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties();
                var writer = new web.tosunsaeng.billing.global.config.security.PublicApiWriter(
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(), new web.tosunsaeng.billing.global.observability.TraceCorrelation(services.getBeanProvider(io.micrometer.tracing.Tracer.class)));
                var controller = new web.tosunsaeng.billing.domain.payment.api.PaymentSyncController(services.getBeanProvider(PaymentSyncService.class),
                        new PaymentSyncDecoder(), new PaymentRateLimiter(store, props, Clock.fixed(NOW, ZoneOffset.UTC)), writer, query,
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), services.getBeanProvider(io.micrometer.tracing.Tracer.class));
                var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
                var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fake").header("alg", "RS256").subject(USER)
                        .claim("account_type", "MEMBER").claim("scope", "billing:purchase").issuedAt(NOW).expiresAt(NOW.plusSeconds(60)).build();
                var security = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                security.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token));
                org.springframework.security.core.context.SecurityContextHolder.setContext(security);
                try {
                    String operation = UUID.randomUUID().toString();
                    var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/payments/sync")
                            .header("Idempotency-Key", operation).contentType("application/json")
                            .content("{\"store\":\"PLAY_STORE\",\"transactionId\":\"mvc-transaction\"}");
                    mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
                    mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
                    assertThat(pending()).isEqualTo(1); assertThat(first("purchases")).isNull();
                    var clock = new WorkerClock();
                    lookupWorker(clock, (uri, key) -> page(List.of(apiPurchase("mvc-transaction")), null)).tick(100);
                    mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("result.status").value("APPLIED"));
                    assertThat(pending()).isZero(); assertThat(db.getCollection("purchases").countDocuments()).isEqualTo(1);
                    assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
                    withdraw();
                    mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
                } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
        } finally { client.getDatabase(budgetDb).drop(); } // Only this test's freshly created random database.
    }
    @Test void publicSyncCannotStartWithoutSharedBudgetSchema() {
        String budgetDb = "missing_" + UUID.randomUUID().toString().replace("-", "");
        publicPipeline(budgetDb).run(c -> assertThat(c).hasFailed());
    }
    @Test void publicSyncCannotStartWithDriftedCursorSchema() {
        String budgetDb = "budget_" + UUID.randomUUID().toString().replace("-", "");
        RevenueCatBudgetSchema.installUnderMaintenance(client.getDatabase(budgetDb), true);
        try {
            db.runCommand(new Document("collMod", PaymentAccountLookupSchema.COLLECTION).append("validator", new Document()));
            publicPipeline(budgetDb).run(c -> assertThat(c).hasFailed());
        } finally { client.getDatabase(budgetDb).drop(); }
    }
    @Test void publicSyncOffDoesNotExposeServiceWhileWorkerCanBePrepared() {
        String budgetDb = "budget_" + UUID.randomUUID().toString().replace("-", "");
        RevenueCatBudgetSchema.installUnderMaintenance(client.getDatabase(budgetDb), true);
        try {
            var runner = publicPipeline(budgetDb); props.setSyncEnabled(false);
            runner.withPropertyValues("billing.payment.sync-enabled=false").run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(PaymentProviderWorkerRuntime.class).doesNotHaveBean(PaymentSyncService.class);
                assertThat(c.getBean(PaymentProviderWorkerRuntime.class).syncService()).isNotNull();
            });
        } finally { client.getDatabase(budgetDb).drop(); }
    }
    @Test void publicSyncRejectsBadKeyAndCatalogMapping() {
        String budgetDb = "budget_" + UUID.randomUUID().toString().replace("-", "");
        RevenueCatBudgetSchema.installUnderMaintenance(client.getDatabase(budgetDb), true);
        try {
            for (String invalid : List.of("billing.payment.provider.lookup-key=not-base64!",
                    "billing.payment.provider.worker.api-key=", "billing.payment.provider.apps[fake.app].products=unknown-product"))
                publicPipeline(budgetDb).withPropertyValues(invalid).run(c -> assertThat(c).hasFailed());
        } finally { client.getDatabase(budgetDb).drop(); }
    }
    PaymentReferenceRetentionWorker referenceRetention(Clock clock, boolean enabled) {
        return new PaymentReferenceRetentionWorker(store, props, clock, enabled);
    }
    WorkerClock agedReference() {
        var clock = new WorkerClock();
        lookupWorker(clock, (uri, key) -> page(List.of(apiPurchase("retained")), null)).tickPeriodic(100);
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isNotNull();
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("money",
                new Document("amount", new org.bson.types.Decimal128(new java.math.BigDecimal("9000")))
                        .append("currency", "KRW").append("basis", "STORE_PAID"))));
        Instant expiry = first("purchases").getDate("retentionExpiresAt").toInstant();
        clock.now = expiry.plus(Duration.ofDays(2));
        return clock;
    }
    @Test void referenceRetentionIsReadOnlyByDefaultAndPreservesFinancialAndUserLinks() {
        var clock = agedReference();
        var purchase = first("purchases"); var account = first("payment_accounts");
        var reference = first(PurchaseReferenceSchema.COLLECTION); var control = first(PaymentRecoveryFence.COLLECTION);
        var preview = referenceRetention(clock, false).tick(100);
        assertThat(preview.eligible()).isEqualTo(1); assertThat(preview.deleted()).isZero();
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isEqualTo(reference);
        assertThat(first(PaymentRecoveryFence.COLLECTION)).isEqualTo(control);
        var result = referenceRetention(clock, true).tick(100);
        assertThat(result.deleted()).isEqualTo(1); assertThat(result.failed()).isZero();
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isNull();
        assertThat(first("purchases")).isEqualTo(purchase); assertThat(first("payment_accounts")).isEqualTo(account);
        assertThat(db.getCollection("payment_ledger").countDocuments()).isPositive();
        assertThat(referenceRetention(clock, true).tick(100).deleted()).isZero();
    }
    @Test void referenceRetentionHonorsCurrentPurchaseDeadlineInsteadOfCachedReference() {
        var clock = agedReference();
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("retentionExpiresAt", Date.from(clock.now.plusSeconds(1)))));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
        clock.now = clock.now.plusSeconds(1);
        assertThat(referenceRetention(clock, true).tick(100).deleted()).isEqualTo(1);
    }
    @Test void referenceRetentionProtectsRecentLedgerAndExtendedRightEvenWithOldDeadline() {
        var clock = agedReference();
        Document right = first("subscription_entitlements");
        db.getCollection("subscription_entitlements").updateOne(new Document(), new Document("$set", new Document("endsAt", Date.from(clock.now))));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
        db.getCollection("subscription_entitlements").replaceOne(new Document("_id", right.get("_id")), right);
        db.getCollection("payment_ledger").updateOne(new Document(), new Document("$set", new Document("occurredAt", Date.from(clock.now))));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
    }
    @Test void referenceRetentionPreservesUnresolvedAndUnknownPurchase() {
        var clock = agedReference();
        core.receive(purchase("pending", "unresolved"));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isNotNull();
    }
    @Test void referenceRetentionKeepsUnknownMoneyAndReviewState() {
        var clock = agedReference(); Document original = first("purchases");
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("money", null)));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
        db.getCollection("purchases").replaceOne(new Document("_id", original.get("_id")), original);
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("processingStatus", "REVIEW_REQUIRED")));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
    }
    @Test void renewedLeaseAndBlockedRecoveryAreNotReleased() {
        var clock = new WorkerClock(); submit(request("unfinished")); var work = lookupWork(clock);
        var lease = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id"));
        clock.now = clock.now.plusSeconds(20); work.renew(lease); clock.now = clock.now.plusSeconds(10);
        assertThat(referenceRetention(clock, true).tick(100).leasesReleased()).isZero();
        clock.now = clock.now.plusSeconds(20); barrier("ALL_BLOCKED");
        assertThat(referenceRetention(clock, true).tick(100).failed()).isEqualTo(1);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).get("lookup", Document.class).getString("owner")).isEqualTo(lease.owner());
    }
    @Test void referenceRetentionRejectsMissingPurchaseAndWrongEnvironment() {
        var clock = agedReference();
        Document purchase = first("purchases");
        db.getCollection("purchases").deleteOne(new Document("_id", purchase.get("_id")));
        assertThat(referenceRetention(clock, true).tick(100).eligible()).isZero();
        db.getCollection(PurchaseReferenceSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("environment", "PRODUCTION")));
        assertThat(referenceRetention(clock, true).tick(100).inspected()).isZero();
    }
    @Test void referenceRetentionBarrierPreventsDeletion() {
        var clock = agedReference(); barrier("PURCHASE_BLOCKED");
        assertThat(referenceRetention(clock, true).tick(100).failed()).isEqualTo(1);
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isNotNull();
    }
    @Test void expiredLookupLeaseIsReleasedWithoutResettingRetryOrCursor() {
        var clock = new WorkerClock(); submit(request("unfinished")); var work = lookupWork(clock);
        var lease = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id"));
        Document before = first(PaymentAccountLookupSchema.COLLECTION);
        assertThat(referenceRetention(clock, true).tick(100).leasesReleased()).isZero();
        clock.now = clock.now.plusSeconds(30);
        assertThat(referenceRetention(clock, false).tick(100).leasesReleased()).isZero();
        assertThat(first(PaymentAccountLookupSchema.COLLECTION)).isEqualTo(before);
        assertThat(referenceRetention(clock, true).tick(100).leasesReleased()).isEqualTo(1);
        Document after = first(PaymentAccountLookupSchema.COLLECTION);
        Document old = before.get("lookup", Document.class), current = after.get("lookup", Document.class);
        for (String field : List.of("attempts", "generation", "pages", "cursor", "failure", "reference")) assertThat(current.get(field)).isEqualTo(old.get(field));
        assertThat(after.get("nextAttemptAt")).isEqualTo(before.get("nextAttemptAt"));
        assertThat(current.get("owner")).isNull(); assertThat(current.getLong("leaseVersion")).isEqualTo(old.getLong("leaseVersion") + 1);
        assertThatThrownBy(() -> work.renew(lease)).isInstanceOf(PaymentException.class);
        assertThat(work.claim(after.getString("_id"))).isNotNull(); assertThat(pending()).isEqualTo(1);
    }
    @Test void concurrentReferencePurgeDeletesOnlyOnce() throws Exception {
        var clock = agedReference(); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> referenceRetention(clock, true).tick(100));
            var b = pool.submit(() -> referenceRetention(clock, true).tick(100));
            assertThat(a.get().deleted() + b.get().deleted()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    int resumeLookup(Clock clock, PaymentSyncService.LookupFailure reason, Map<String, Long> selected) {
        var cursor = first(PaymentAccountLookupSchema.COLLECTION);
        return new PaymentLookupResumer(store, props, clock).resumeUnderMaintenance(cursor.getString("accountId"), cursor.getLong("version"),
                cursor.get("lookup", Document.class).getLong("generation"), reason, selected, true);
    }
    Map<String, Long> commandVersions() {
        var selected = new LinkedHashMap<String, Long>();
        db.getCollection(PaymentSyncSchema.COLLECTION).find().forEach(d -> selected.put(d.getString("_id"), d.getLong("version")));
        return selected;
    }
    PaymentReviewOperator.Command operatorCommand(String operation) {
        var row = first(PaymentAccountLookupSchema.COLLECTION);
        return new PaymentReviewOperator.Command(operation, row.getString("accountId"), row.getLong("version"),
                row.get("lookup", Document.class).getLong("generation"), first(PaymentRecoveryFence.COLLECTION).getString("epoch"),
                PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, PaymentReviewOperator.Reason.PROVIDER_RECOVERED, commandVersions());
    }
    PaymentReviewOperator operator() {
        PaymentOperatorSchema.installUnderMaintenance(db, true);
        return new PaymentReviewOperator(store, Clock.fixed(NOW.plus(Duration.ofDays(2)), ZoneOffset.UTC), PaymentReviewOperator.Action.ACCOUNT, "SANDBOX");
    }
    void parked() {
        submit(request("operator-test")); var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(2));
        lookupWorker(clock, (u,k) -> { throw new AssertionError("no HTTP"); }).tick(100);
    }
    Map<String, List<Document>> operatorSnapshot() {
        var result = new LinkedHashMap<String, List<Document>>();
        for (String collection : List.of(PaymentSyncSchema.COLLECTION, PaymentAccountLookupSchema.COLLECTION,
                PaymentRecoveryFence.COLLECTION, "payment_account_lifecycles", PaymentOperatorSchema.COLLECTION))
            result.put(collection, db.getCollection(collection).find().into(new ArrayList<>()));
        return result;
    }
    @Test void operatorDryRunAndInspectAreReadOnlyThenApplyIsAtomicAndReplaySafe() {
        parked(); var operator = operator(); var command = operatorCommand(UUID.randomUUID().toString());
        String actor = "a".repeat(64); var before = operatorSnapshot();
        assertThat(operator.inspect(command.target()).getList("commands", Document.class)).hasSize(1);
        assertThat(operator.execute(command, actor, false, false).status()).isEqualTo("DRY_RUN");
        assertThat(operatorSnapshot()).isEqualTo(before);
        var done = operator.execute(command, actor, true, true);
        assertThat(done.status()).isEqualTo("APPLIED"); assertThat(done.count()).isEqualTo(1);
        var after = operatorSnapshot();
        assertThat(operator.execute(command, actor, true, true).status()).isEqualTo("ALREADY_APPLIED");
        assertThat(operator.result(command.operationId())).isEqualTo(done);
        assertThat(operatorSnapshot()).isEqualTo(after); assertThat(pending()).isEqualTo(1);
        String receipt = first(PaymentOperatorSchema.COLLECTION).toJson();
        assertThat(receipt).doesNotContain(USER, ref, command.target(), "operator-test");
        assertThatThrownBy(() -> operator.execute(command, "b".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(operatorSnapshot()).isEqualTo(after);
    }
    @Test void operatorReceiptFailureRollsBackAllResumeWrites() {
        parked(); var operator = operator(); var command = operatorCommand(UUID.randomUUID().toString()); var before = operatorSnapshot();
        db.runCommand(new Document("collMod", PaymentOperatorSchema.COLLECTION).append("validator", new Document("count", -1L)));
        assertThatThrownBy(() -> operator.execute(command, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(operatorSnapshot()).isEqualTo(before);
    }
    @Test void operatorRejectsStaleEpochIsolationAndBlockedBarrierInBothModes() {
        parked(); var operator = operator(); var command = operatorCommand(UUID.randomUUID().toString()); var before = operatorSnapshot();
        assertThatThrownBy(() -> operator.execute(command, "a".repeat(64), true, false)).isInstanceOf(RuntimeException.class);
        assertThat(operatorSnapshot()).isEqualTo(before);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> operator.execute(command, "a".repeat(64), false, false)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> operator.execute(command, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        barrier("OPEN"); db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> operator.execute(command, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(PaymentOperatorSchema.COLLECTION).countDocuments()).isZero();
    }
    @Test void concurrentOperatorSameOperationConvergesToOneReceipt() throws Exception {
        parked(); var operator = operator(); var command = operatorCommand(UUID.randomUUID().toString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var latch = new CountDownLatch(1);
            Callable<PaymentReviewOperator.Result> action = () -> { latch.await(); return operator.execute(command, "a".repeat(64), true, true); };
            var a = pool.submit(action); var b = pool.submit(action); latch.countDown();
            assertThat(List.of(a.get().status(), b.get().status())).containsExactlyInAnyOrder("APPLIED", "ALREADY_APPLIED");
        }
        assertThat(db.getCollection(PaymentOperatorSchema.COLLECTION).countDocuments()).isEqualTo(1); assertThat(pending()).isEqualTo(1);
    }
    @Test void operatorCannotReuseIdForDifferentReasonOrStaleSnapshotForNewOperation() {
        parked(); var operator = operator(); var c = operatorCommand(UUID.randomUUID().toString());
        operator.execute(c, "a".repeat(64), true, true);
        var differentReason = new PaymentReviewOperator.Command(c.operationId(), c.target(), c.version(), c.generation(), c.epoch(), c.failure(),
                PaymentReviewOperator.Reason.MANUAL_REVIEW_COMPLETED, c.selected());
        assertThatThrownBy(() -> operator.execute(differentReason, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        var newOperation = new PaymentReviewOperator.Command(UUID.randomUUID().toString(), c.target(), c.version(), c.generation(), c.epoch(), c.failure(), c.reason(), c.selected());
        assertThatThrownBy(() -> operator.execute(newOperation, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(operator.result(newOperation.operationId()).status()).isEqualTo("NOT_FOUND");
        assertThat(db.getCollection(PaymentOperatorSchema.COLLECTION).countDocuments()).isEqualTo(1);
    }
    @Test void authReceiptFailureRollsBackUnblock() {
        PaymentOperatorSchema.installUnderMaintenance(db, true);
        var clock = new WorkerClock(); var budget = new RevenueCatProjectBudget(store, "fake-project", clock);
        var permit = budget.acquire(); budget.blockAuth(permit); budget.release(permit);
        var operator = new PaymentReviewOperator(store, clock, PaymentReviewOperator.Action.AUTH, "SHARED");
        var c = new PaymentReviewOperator.Command(UUID.randomUUID().toString(), "fake-project", first(RevenueCatBudgetSchema.COLLECTION).getLong("version"),
                0, null, null, PaymentReviewOperator.Reason.CREDENTIALS_FIXED, Map.of());
        var before = first(RevenueCatBudgetSchema.COLLECTION);
        db.runCommand(new Document("collMod", PaymentOperatorSchema.COLLECTION).append("validator", new Document("count", -1L)));
        assertThatThrownBy(() -> operator.execute(c, "a".repeat(64), true, true)).isInstanceOf(RuntimeException.class);
        assertThat(first(RevenueCatBudgetSchema.COLLECTION)).isEqualTo(before);
        assertThat(budget.isAuthBlocked()).isTrue();
    }
    @Test void projectAuthOperatorIsSharedIndependentAndAuditedWithoutChangingRateBudget() {
        PaymentOperatorSchema.installUnderMaintenance(db, true);
        var clock = new WorkerClock(); var budget = new RevenueCatProjectBudget(store, "fake-project", clock);
        var permit = budget.acquire(); budget.blockAuth(permit);
        var operator = new PaymentReviewOperator(store, clock, PaymentReviewOperator.Action.AUTH, "SHARED");
        var live = new PaymentReviewOperator.Command(UUID.randomUUID().toString(), "fake-project", first(RevenueCatBudgetSchema.COLLECTION).getLong("version"),
                0, null, null, PaymentReviewOperator.Reason.CREDENTIALS_FIXED, Map.of());
        assertThatThrownBy(() -> operator.execute(live, "a".repeat(64), false, false)).isInstanceOf(RuntimeException.class);
        budget.release(permit);
        var command = new PaymentReviewOperator.Command(UUID.randomUUID().toString(), "fake-project", first(RevenueCatBudgetSchema.COLLECTION).getLong("version"),
                0, null, null, PaymentReviewOperator.Reason.CREDENTIALS_FIXED, Map.of());
        var before = first(RevenueCatBudgetSchema.COLLECTION);
        assertThat(operator.execute(command, "a".repeat(64), false, false).status()).isEqualTo("DRY_RUN");
        assertThat(first(RevenueCatBudgetSchema.COLLECTION)).isEqualTo(before);
        assertThat(operator.execute(command, "a".repeat(64), true, true).status()).isEqualTo("APPLIED");
        assertThat(budget.isAuthBlocked()).isFalse();
        assertThat(first(RevenueCatBudgetSchema.COLLECTION).get("starts")).isEqualTo(before.get("starts"));
        assertThat(operator.execute(command, "a".repeat(64), true, true).status()).isEqualTo("ALREADY_APPLIED");
        assertThatThrownBy(() -> new PaymentReviewOperator(store, clock, PaymentReviewOperator.Action.AUTH, "SANDBOX")).isInstanceOf(RuntimeException.class);
    }
    @Test void operatorSchemaIsExplicitStrictAndRejectsDriftAndTTL() {
        assertThatThrownBy(() -> new PaymentReviewOperator(store, Clock.systemUTC(), PaymentReviewOperator.Action.ACCOUNT, "SANDBOX"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PaymentOperatorSchema.installUnderMaintenance(db, false)).isInstanceOf(RuntimeException.class);
        PaymentOperatorSchema.installUnderMaintenance(db, true); PaymentOperatorSchema.verify(db);
        assertThatThrownBy(() -> db.getCollection(PaymentOperatorSchema.COLLECTION).insertOne(new Document("_id", "bad"))).isInstanceOf(RuntimeException.class);
        db.getCollection(PaymentOperatorSchema.COLLECTION).createIndex(new Document("completedAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(1L, TimeUnit.SECONDS));
        assertThatThrownBy(() -> PaymentOperatorSchema.verify(db)).isInstanceOf(RuntimeException.class);
    }
    @Test void durablePurchaseCompletesBoundSyncAfterInboxDeletionWithoutHttpOrRepayment() {
        submit(request("tx")); var o = purchase("event", "tx"); core.receive(o);
        var row = first(PaymentSyncSchema.COLLECTION);
        sync.bindObservation(row.getString("_id"), row.getLong("version"), o.providerEventKey(), sync.capture());
        core.apply(o.providerEventKey()); db.getCollection("payment_provider_inbox").deleteMany(new Document()); // Simulate TTL of test evidence.
        var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(121));
        assertThat(lookupWorker(clock, (u, k) -> { throw new AssertionError("durable proof, no HTTP"); }).tick(100).completed()).isEqualTo(1);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("APPLIED");
        assertThat(pending()).isZero(); assertThat(db.getCollection("payment_ledger").countDocuments()).isEqualTo(2);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void durableCompletionIsGuardedAndDoesNotResolveReviewOrMissingFinancialEvidence() {
        submit(request("tx")); apply(purchase("e", "tx")); var row = first(PaymentSyncSchema.COLLECTION);
        assertThatThrownBy(() -> sync.completeVerifiedPurchase(row.getString("_id"), row.getLong("version"), sync.capture(), s -> { throw PaymentException.unavailable(); }))
                .isInstanceOf(PaymentException.class);
        apply(observation("refund", "tx", PaymentOffer.PREMIUM_1D, PurchaseObservation.Kind.REFUND));
        assertThat(sync.completeVerifiedPurchase(row.getString("_id"), row.getLong("version"), sync.capture(), s -> {})).isFalse();
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("PENDING");
        assertThat(pending()).isEqualTo(2);
    }
    @Test void resumedOldRequestGetsFreshBudgetAndLatePurchaseCompletesExactlyOnce() {
        submit(request("tx")); var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(2));
        lookupWorker(clock, (u, k) -> { throw new AssertionError("expired"); }).tick(100);
        var old = first(PaymentSyncSchema.COLLECTION); assertThat(old.getString("errorCode")).isEqualTo("RETRY_EXHAUSTED");
        assertThat(resumeLookup(clock, PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, commandVersions())).isEqualTo(1);
        var resumed = first(PaymentSyncSchema.COLLECTION);
        assertThat(resumed.getDate("createdAt")).isEqualTo(old.getDate("createdAt"));
        assertThat(resumed.getDate("retryStartedAt").toInstant()).isEqualTo(clock.now);
        assertThat(resumed.getLong("attempts")).isEqualTo(1); assertThat(pending()).isEqualTo(1);
        assertThat(lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("tx")), null)).tick(100).completed()).isEqualTo(1);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("APPLIED");
        assertThat(pending()).isZero(); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void resumedLookupRestartsBackoffAtFiveSecondsWithoutErasingCreationHistory() {
        submit(request("tx")); var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(2));
        lookupWorker(clock, (u, k) -> { throw new AssertionError("expired"); }).tick(100);
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.failure", "BLOCKED_AUTH")));
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, commandVersions())).isInstanceOf(PaymentException.class);
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.failure", "IDLE")));
        resumeLookup(clock, PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, commandVersions());
        lookupWorker(clock, (u, k) -> page(List.of(), null)).tick(100);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt").toInstant()).isEqualTo(clock.now.plusSeconds(5));
        assertThat(first(PaymentSyncSchema.COLLECTION).getDate("createdAt").toInstant()).isEqualTo(NOW);
    }
    @Test void resumeIsSelectedAtomicAndFencesOldLeaseAndVersion() {
        submit(request("a")); submit(request("b")); var clock = new WorkerClock(); var work = lookupWork(clock);
        var lease = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id"));
        for (var command : work.commands(lease)) sync.handoffLookup(command.getString("_id"), command.getLong("version"), PaymentSyncService.LookupFailure.PROVIDER_REVIEW,
                lease.ticket(), s -> work.guard(s, lease));
        var versions = commandVersions();
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.PROVIDER_REVIEW, versions)).isInstanceOf(PaymentException.class);
        clock.now = NOW.plusSeconds(30);
        var bad = new LinkedHashMap<>(versions); bad.put(UUID.randomUUID().toString(), 1L);
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.PROVIDER_REVIEW, bad)).isInstanceOf(PaymentException.class);
        assertThat(commandVersions()).isEqualTo(versions);
        var first = versions.entrySet().iterator().next();
        assertThat(resumeLookup(clock, PaymentSyncService.LookupFailure.PROVIDER_REVIEW, Map.of(first.getKey(), first.getValue()))).isEqualTo(1);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments(new Document("state", "REVIEW_REQUIRED"))).isEqualTo(1);
        assertThat(pending()).isEqualTo(2);
        assertThatThrownBy(() -> work.renew(lease)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.PROVIDER_REVIEW, versions)).isInstanceOf(PaymentException.class);
    }
    @Test void authResumeDoesNotClearProjectBlockAndNewRequestsStillRespectProviderAuth() {
        submit(request("tx")); var clock = new WorkerClock();
        lookupWorker(clock, (u, k) -> new RevenueCatReadClient.Response(401, Map.of(), new byte[0])).tick(100);
        assertThat(first(RevenueCatBudgetSchema.COLLECTION).getBoolean("blockedAuth")).isTrue();
        assertThat(resumeLookup(clock, PaymentSyncService.LookupFailure.BLOCKED_AUTH, commandVersions())).isEqualTo(1);
        lookupWorker(clock, (u, k) -> { throw new AssertionError("project still blocked"); }).tick(100);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("BLOCKED_AUTH");
        assertThat(pending()).isEqualTo(1);
    }
    @Test void resumeRejectsWrongReasonBoundCaseOverflowAndMissingIsolation() {
        submit(request("tx")); var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(2));
        lookupWorker(clock, (u, k) -> { throw new AssertionError("expired"); }).tick(100);
        var versions = commandVersions(); var cursor = first(PaymentAccountLookupSchema.COLLECTION);
        assertThatThrownBy(() -> new PaymentLookupResumer(store, props, clock).resumeUnderMaintenance(cursor.getString("accountId"), cursor.getLong("version"),
                cursor.get("lookup", Document.class).getLong("generation"), PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, versions, false)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.BLOCKED_AUTH, versions)).isInstanceOf(PaymentException.class);
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.generation", Long.MAX_VALUE)));
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, versions)).isInstanceOf(PaymentException.class);
        assertThat(commandVersions()).isEqualTo(versions);
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.generation", 1L)));
        var refund = observation("refund", "tx", PaymentOffer.PREMIUM_1D, PurchaseObservation.Kind.REFUND); apply(refund);
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.RETRY_EXHAUSTED, versions)).isInstanceOf(PaymentException.class);
        assertThat(commandVersions()).isEqualTo(versions);
    }
    @Test void syncR2MigrationRequiresExactOldSchemaIsolationAndNoIndexChange() {
        var old = PaymentSyncSchema.validator(); old.get("$jsonSchema", Document.class).get("properties", Document.class).remove("retryStartedAt");
        db.runCommand(new Document("collMod", PaymentSyncSchema.COLLECTION).append("validator", old));
        assertThatThrownBy(() -> PaymentSyncSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaymentSyncSchema.upgradeResumeUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        var before = db.getCollection(PaymentSyncSchema.COLLECTION).listIndexes().into(new ArrayList<>());
        barrier("ALL_BLOCKED"); PaymentSyncSchema.upgradeResumeUnderMaintenance(db, true); PaymentSyncSchema.upgradeResumeUnderMaintenance(db, true);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).listIndexes().into(new ArrayList<>())).isEqualTo(before);
        PaymentSyncSchema.verify(db);
    }
    @Test void periodicLookupRecoversWithoutSyncAndSchedulesActiveRightsSixHoursLater() {
        var clock = new WorkerClock(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        var worker = lookupWorker(clock, (u, k) -> { calls.incrementAndGet(); return page(List.of(apiPurchase("missed")), null); });
        assertThat(worker.tick(100).claimed()).isZero();
        assertThat(worker.tickPeriodic(100)).isEqualTo(new PaymentAccountLookupWorker.Tick(1, 1, 0, 0));
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments()).isZero(); assertThat(pending()).isZero();
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("reconcileAt").toInstant()).isEqualTo(NOW.plusSeconds(21600));
        assertThat(worker.tickPeriodic(100).claimed()).isZero();
        clock.now = NOW.plusSeconds(21600); assertThat(worker.tickPeriodic(100).completed()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(2); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void periodicPaginationPersistsWithoutPendingAndReplaysAfterCrash() {
        var clock = new WorkerClock();
        var worker = lookupWorker(clock, (u, k) -> u.getQuery().contains("starting_after")
                ? page(List.of(apiPurchase("second")), null) : page(List.of(apiPurchase("first")), "next-secret"));
        assertThat(worker.tickPeriodic(100).deferred()).isEqualTo(1);
        var cursor = first(PaymentAccountLookupSchema.COLLECTION);
        assertThat(cursor.toJson()).doesNotContain("next-secret"); assertThat(cursor.get("lookup", Document.class).getLong("pages")).isEqualTo(1);
        clock.now = NOW.plusSeconds(1); var l = lookupWork(clock).claim(cursor.getString("_id"), true); assertThat(l.cursor()).isEqualTo("next-secret");
        clock.now = NOW.plusSeconds(31); assertThat(worker.tickPeriodic(100).completed()).isEqualTo(1);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(2);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("reconcileStartedAt")).isNull();
    }
    @Test void periodicEmptyAnd404DoNotRevokeAndExpiredAccountsAreOutsideRecentScan() {
        var clock = new WorkerClock();
        var worker = lookupWorker(clock, (u, k) -> page(List.of(), null));
        assertThat(worker.tickPeriodic(100).completed()).isEqualTo(1);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("reconcileAt").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(db.getCollection("purchases").countDocuments()).isZero();
        clock.now = NOW.plus(Duration.ofDays(91)); assertThat(worker.tickPeriodic(100).claimed()).isZero();
        apply(purchase("known", "known")); clock.now = NOW.plus(Duration.ofHours(25));
        assertThat(lookupWorker(clock, (u, k) -> new RevenueCatReadClient.Response(404, Map.of(), new byte[0])).tickPeriodic(100).failed()).isEqualTo(1);
        assertThat(first("purchases").getString("accessState")).isEqualTo("ALLOWED");
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void periodicOnlyReviewCanBeExplicitlyResumedWithoutInventingCommand() {
        var clock = new WorkerClock();
        lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("ambiguous").append("quantity", 2)), null)).tickPeriodic(100);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt")).isNull();
        assertThatThrownBy(() -> resumeLookup(clock, PaymentSyncService.LookupFailure.BLOCKED_AUTH, Map.of())).isInstanceOf(PaymentException.class);
        assertThat(resumeLookup(clock, PaymentSyncService.LookupFailure.PROVIDER_REVIEW, Map.of())).isZero();
        assertThat(lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("confirmed")), null)).tickPeriodic(100).completed()).isEqualTo(1);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments()).isZero(); assertThat(pending()).isZero();
    }
    @Test void periodicScanHas24HourBudgetAndSharesSyncLease() {
        var clock = new WorkerClock(); submit(request("pending"));
        var lease = lookupWork(clock).claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id"));
        assertThat(lookupWorker(clock, (u, k) -> { throw new AssertionError("account leased"); }).tickPeriodic(100).claimed()).isZero();
        lookupWork(clock).defer(lease, NOW, "RETRY");
        lookupWorker(clock, (u, k) -> new RevenueCatReadClient.Response(404, Map.of(), new byte[0])).tickPeriodic(100);
        clock.now = NOW.plus(Duration.ofHours(24));
        lookupWorker(clock, (u, k) -> { throw new AssertionError("budget expired"); }).tickPeriodic(100);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getString("kind")).isEqualTo("ACCOUNT_LOOKUP");
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).get("lookup", Document.class).getString("failure")).isEqualTo("PROVIDER_REVIEW");
        assertThat(pending()).isEqualTo(1);
    }
    @Test void cursorR2MigrationKeepsExistingRowsAndRejectsDrift() {
        var old = PaymentAccountLookupSchema.validator(); var fields = old.get("$jsonSchema", Document.class).get("properties", Document.class);
        fields.remove("reconcileAt"); fields.remove("reconcileStartedAt");
        db.runCommand(new Document("collMod", PaymentAccountLookupSchema.COLLECTION).append("validator", old));
        assertThatThrownBy(() -> PaymentAccountLookupSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED"); PaymentAccountLookupSchema.upgradeReconciliationUnderMaintenance(db, true);
        PaymentAccountLookupSchema.upgradeReconciliationUnderMaintenance(db, true);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION)).doesNotContainKeys("reconcileAt", "reconcileStartedAt");
        PaymentAccountLookupSchema.verify(db);
    }
    PurchaseReferenceStore referenceStore(Clock clock) {
        return new PurchaseReferenceStore(store, props, clock, "fake-project", new PurchaseReferenceCipher("test-key", new byte[32]));
    }
    @Test void referenceIsEncryptedStableAndExpiresUsingFinancialRetentionNotReadTime() {
        var clock = new WorkerClock(); var worker = lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("encrypted")), null));
        worker.tickPeriodic(100); var purchase = first("purchases"); var stored = first(PurchaseReferenceSchema.COLLECTION);
        assertThat(stored.toJson()).doesNotContain("rc-encrypted");
        assertThat(referenceStore(clock).read(purchase.getString("_id"), purchase.getString("accountId"))).isEqualTo("rc-encrypted");
        clock.now = NOW.plusSeconds(21600); worker.tickPeriodic(100);
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isEqualTo(stored);
        clock.now = purchase.getDate("retentionExpiresAt").toInstant();
        assertThat(referenceStore(clock).read(purchase.getString("_id"), purchase.getString("accountId"))).isNull();
        assertThat(db.getCollection(PurchaseReferenceSchema.COLLECTION).countDocuments()).isEqualTo(1); // No physical purge enabled.
    }
    @Test void referenceConflictDoesNotReplaceCiphertextOrPayTwice() {
        var clock = new WorkerClock(); lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("same")), null)).tickPeriodic(100);
        var stored = first(PurchaseReferenceSchema.COLLECTION); clock.now = NOW.plusSeconds(21600);
        assertThat(lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("same").append("id", "different-reference")), null)).tickPeriodic(100).failed()).isEqualTo(1);
        assertThat(first(PurchaseReferenceSchema.COLLECTION)).isEqualTo(stored);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).get("lookup", Document.class).getString("failure")).isEqualTo("PROVIDER_CONTRACT");
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void referenceRejectsWrongOwnerTamperingMissingSchemaAndStaleEpoch() {
        var clock = new WorkerClock(); lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("sensitive")), null)).tickPeriodic(100);
        var purchase = first("purchases"); var references = referenceStore(clock);
        assertThatThrownBy(() -> references.read(purchase.getString("_id"), UUID.randomUUID().toString())).isInstanceOf(PaymentException.class);
        var ticket = sync.capture();
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> references.remember(purchase("ignored", "sensitive"), "rc-sensitive", purchase.getString("accountId"), ticket, s -> {})).isInstanceOf(PaymentException.class);
        db.getCollection(PurchaseReferenceSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("envelope.ciphertext", "broken")));
        assertThatThrownBy(() -> references.read(purchase.getString("_id"), purchase.getString("accountId"))).isInstanceOf(RevenueCatFailure.class);
        db.getCollection(PurchaseReferenceSchema.COLLECTION).drop(); // Isolated test schema only.
        assertThatThrownBy(() -> referenceStore(clock)).isInstanceOf(IllegalStateException.class);
    }
    @Test void referenceWriteFailureRollsBackGuardAndReplayAfterFinancialCommitRecovers() {
        var clock = new WorkerClock(); var observation = purchase("e", "tx"); apply(observation);
        assertThatThrownBy(() -> referenceStore(clock).remember(observation, "rc-tx", first("payment_accounts").getString("_id"), sync.capture(),
                s -> { throw PaymentException.unavailable(); })).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection(PurchaseReferenceSchema.COLLECTION).countDocuments()).isZero();
        lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("tx")), null)).tickPeriodic(100);
        assertThat(db.getCollection(PurchaseReferenceSchema.COLLECTION).countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void providerSummariesArePrivacySafeThrottledAndDisabledByDefault() {
        submit(request("secret-tx")); var clock = new WorkerClock(); var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var messages = new ArrayList<PaymentProviderAlerts.Summary>(); var blocked = new java.util.concurrent.atomic.AtomicBoolean(true);
        var off = new PaymentProviderAlerts(store, "SANDBOX", clock, blocked::get, registry, false, s -> { throw new AssertionError("disabled"); });
        off.poll(); assertThat(registry.get("billing_payment_provider_pending").gauge().value()).isEqualTo(1);
        var enabled = new PaymentProviderAlerts(store, "SANDBOX", clock, blocked::get, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), true,
                s -> { messages.add(s); return true; });
        enabled.poll(); enabled.poll(); assertThat(messages).hasSize(2);
        assertThat(messages.toString()).doesNotContain(USER, ref, "secret-tx");
        blocked.set(false); clock.now = NOW.plusSeconds(3600); enabled.poll();
        assertThat(messages).hasSize(3); assertThat(messages.getLast().kind()).isEqualTo(PaymentProviderAlerts.Kind.AUTH_RECOVERY);
        assertThat(pending()).isEqualTo(1);
        assertThatThrownBy(() -> new PaymentProviderAlerts(store, "SANDBOX", clock, blocked::get, registry, true, null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void alertTransportFailureDoesNotAffectFinancialStateOrHotLoop() {
        var clock = new WorkerClock(); var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        var alerts = new PaymentProviderAlerts(store, "SANDBOX", clock, () -> true, registry, true, s -> { calls.incrementAndGet(); throw new IllegalStateException("private transport error"); });
        alerts.poll(); alerts.poll(); assertThat(calls.get()).isEqualTo(2);
        assertThat(registry.get("billing_payment_provider_alert_delivery_failed").counter().count()).isEqualTo(2);
        clock.now = NOW.plusSeconds(3600); alerts.poll(); assertThat(calls.get()).isEqualTo(4);
        assertThat(db.getCollection("purchases").countDocuments()).isZero();
    }
    @Test void retentionHandoffAt119DaysPreservesPendingAndMinimumEvidence() {
        var o = purchase("old", "old"); core.receive(o); var clock = new WorkerClock();
        clock.now = NOW.plus(Duration.ofDays(119)).minusMillis(1);
        assertThat(retention(clock, false).tick(100).handedOff()).isZero();
        clock.now = clock.now.plusMillis(1);
        assertThat(retention(clock, false).tick(100).handedOff()).isEqualTo(1);
        assertThat(first("payment_purchase_cases").getString("reason")).isEqualTo("RETENTION_HANDOFF");
        assertThat(first("payment_purchase_cases").get("normalizedObservation")).isEqualTo(first("payment_provider_inbox").get("normalizedObservation"));
        assertThat(first("payment_purchase_cases")).doesNotContainKeys("purgeAt", "retentionDeadline", "work");
        assertThat(pending()).isEqualTo(1);
        assertThat(retention(clock, false).tick(100).handedOff()).isZero();
        assertThat(db.getCollection("payment_purchase_cases").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isZero();
    }
    @Test void retentionDryRunAnd120DayBoundaryDoNotDeleteFinancialRecords() {
        var r = request("tx"); submit(r); apply(purchase("event", "tx"));
        lookupWorker(new WorkerClock(), (u, k) -> { throw new AssertionError("already applied"); }).tick(100);
        var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(120)).minusMillis(1);
        assertThat(retention(clock, true).tick(100).candidates()).isZero();
        clock.now = clock.now.plusMillis(1);
        assertThat(retention(clock, false).tick(100)).isEqualTo(new PaymentRetentionWorker.Tick(0, 1, 0, 0));
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments()).isEqualTo(1);
        assertThat(retention(clock, true).tick(100)).isEqualTo(new PaymentRetentionWorker.Tick(0, 1, 1, 0));
        assertThat(retention(clock, true).tick(100).deleted()).isZero();
        assertThat(pending()).isZero();
        assertThat(db.getCollection("purchases").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("payment_ledger").countDocuments()).isEqualTo(2);
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(PaymentAccountLookupSchema.COLLECTION).countDocuments()).isEqualTo(1);
    }
    @Test void retentionNeverDeletesUnresolvedCommandsAndIsBounded() {
        for (int i = 0; i < 3; i++) submit(request("pending-" + i));
        var commands = db.getCollection(PaymentSyncSchema.COLLECTION);
        var list = commands.find().into(new ArrayList<>());
        commands.updateOne(new Document("_id", list.get(1).get("_id")), new Document("$set", new Document("state", "REVIEW_REQUIRED")));
        commands.updateOne(new Document("_id", list.get(2).get("_id")), new Document("$set", new Document("state", "BLOCKED_AUTH")));
        core.receive(purchase("a", "a")); core.receive(purchase("b", "b"));
        var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(121));
        assertThat(retention(clock, true).tick(1)).isEqualTo(new PaymentRetentionWorker.Tick(1, 0, 0, 0));
        assertThat(retention(clock, true).tick(1)).isEqualTo(new PaymentRetentionWorker.Tick(1, 0, 0, 0));
        assertThat(commands.countDocuments()).isEqualTo(3); assertThat(pending()).isEqualTo(5);
        assertThatThrownBy(() -> retention(clock, true).tick(101)).isInstanceOf(PaymentException.class);
    }
    @Test void retentionUsesUpdatedAtNotCommandCreationAndFiltersEnvironment() {
        submit(request("terminal"));
        var commands = db.getCollection(PaymentSyncSchema.COLLECTION);
        commands.updateOne(new Document(), new Document("$set", new Document("state", "REJECTED").append("counted", false)
                .append("updatedAt", Date.from(NOW.plusSeconds(1)))));
        var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(120));
        assertThat(retention(clock, true).tick(100).deleted()).isZero();
        clock.now = clock.now.plusSeconds(1);
        commands.updateOne(new Document(), new Document("$set", new Document("environment", "PRODUCTION")));
        assertThat(retention(clock, true).tick(100).deleted()).isZero();
        commands.updateOne(new Document(), new Document("$set", new Document("environment", "SANDBOX")));
        assertThat(retention(clock, true).tick(100).deleted()).isEqualTo(1);
    }
    @Test void retentionBlocksDeletionUnderRecoveryBarrierAndOldEpochHandoff() {
        submit(request("terminal"));
        db.getCollection(PaymentSyncSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("state", "REJECTED").append("counted", false)));
        var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(120));
        var o = purchase("aged", "aged"); core.receive(o);
        var ticket = new PaymentRecoveryFence(store, props).capture();
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        var agedCore = new PurchaseCore(store, props, clock, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
        assertThatThrownBy(() -> agedCore.handoffAged(o.providerEventKey(), ticket)).isInstanceOf(PaymentException.class);
        barrier("ALL_BLOCKED");
        assertThat(retention(clock, true).tick(100).failed()).isEqualTo(2);
        assertThat(db.getCollection("payment_purchase_cases").countDocuments()).isZero();
        barrier("PURCHASE_BLOCKED");
        var result = retention(clock, true).tick(100);
        assertThat(result.handedOff()).isEqualTo(1); assertThat(result.deleted()).isZero(); assertThat(result.failed()).isEqualTo(1);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments()).isEqualTo(1);
    }
    @Test void twoRetentionWorkersConvergeOnOneCaseAndOneTerminalDeletion() throws Exception {
        core.receive(purchase("aged", "aged")); submit(request("terminal"));
        db.getCollection(PaymentSyncSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("state", "REJECTED").append("counted", false)));
        var clock = new WorkerClock(); clock.now = NOW.plus(Duration.ofDays(120));
        var a = retention(clock, true); var b = retention(clock, true);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(() -> a.tick(100), () -> b.tick(100)));
            for (var result : results) result.get();
        }
        assertThat(db.getCollection("payment_purchase_cases").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments()).isZero();
        assertThat(pending()).isEqualTo(2); // Synthetic terminal fixture: retention never alters pending counters.
    }
    @Test void actualRuntimeWiresVerifyOnlyAndStaysDormantUntilApplicationReady() {
        String budgetDb = "shared_" + UUID.randomUUID().toString().replace("-", "");
        var shared = client.getDatabase(budgetDb); RevenueCatBudgetSchema.installUnderMaintenance(shared, true);
        var mongo = new web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties(); mongo.setRequireTransactions(true); mongo.setInitializeIndexes(true);
        var context = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(PaymentProviderWorkerConfiguration.class)
                .withBean(PaymentStore.class, () -> store).withBean(MongoClient.class, () -> client,
                        bd -> ((org.springframework.beans.factory.support.AbstractBeanDefinition) bd).setDestroyMethodName(""))
                .withBean(PaymentProperties.class, () -> props)
                .withBean(web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties.class, () -> mongo)
                .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .withPropertyValues("billing.payment.provider.worker.enabled=true", "billing.payment.provider.project=fake-project",
                        "billing.payment.provider.worker.api-key=fake-key", "billing.payment.provider.worker.budget-database=" + budgetDb,
                        "billing.payment.provider.lookup-key=" + Base64.getEncoder().encodeToString(new byte[32]), "billing.payment.provider.lookup-key-version=fake-v1",
                        "billing.payment.provider.worker.reference-key=" + Base64.getEncoder().encodeToString(new byte[32]), "billing.payment.provider.worker.reference-key-id=fake-ref-key",
                        "billing.payment.provider.apps[fake.app].store=PLAY_STORE",
                        "billing.payment.provider.apps[fake.app].products=PREMIUM_1D,PREMIUM_3D,PREMIUM_7D,PREMIUM_14D,PREMIUM_28D");
        try {
            context.run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(PaymentProviderWorkerRuntime.class);
                assertThat(shared.getCollection(RevenueCatBudgetSchema.COLLECTION).countDocuments()).isZero();
            });
            context.withPropertyValues("billing.payment.provider.worker.reference-key=invalid")
                    .run(c -> assertThat(c).hasFailed());
            context.withPropertyValues("billing.payment.provider.worker.alerts-enabled=true")
                    .run(c -> assertThat(c).hasFailed());
        } finally { shared.drop(); } // Isolated test budget database only.
    }
    @Test void boundedReviewHandoffSurvivesCrashAfterReclaimAndPageLimitDoesNotFetch() {
        var clock = new WorkerClock(); for (int i = 0; i < 101; i++) submit(request("tx-" + i));
        lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("unknown").append("quantity", 2)), null)).tick(10);
        clock.now = NOW.plusSeconds(1); var work = lookupWork(clock);
        var l = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id")); assertThat(l.failure()).isEqualTo("PROVIDER_REVIEW");
        clock.now = NOW.plusSeconds(31);
        lookupWorker(clock, (u, k) -> { throw new AssertionError("review disposition survives crash"); }).tick(10);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments(new Document("state", "REVIEW_REQUIRED"))).isEqualTo(101);
        assertThat(pending()).isEqualTo(101);
    }
    @Test void pageLimitIsReviewAndNeverConsumesAdditionalQuota() {
        var clock = new WorkerClock(); submit(request("tx")); var work = lookupWork(clock);
        var l = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id")); work.checkpoint(l, "page", NOW.plusSeconds(1));
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.pages", 1000L)));
        clock.now = NOW.plusSeconds(1); lookupWorker(clock, (u, k) -> { throw new AssertionError("page bound"); }).tick(10);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("errorCode")).isEqualTo("PAGE_LIMIT"); assertThat(pending()).isEqualTo(1);
    }
    @Test void accountLookupReusesLegacyTerminalSourceWithoutNetwork() {
        var request = request("tx"); submit(request); apply(purchase("legacy", "tx"));
        assertThat(first("payment_provider_inbox")).doesNotContainKey("work");
        assertThat(lookupWorker(new WorkerClock(), (u, k) -> { throw new AssertionError("No HTTP needed"); }).tick(10).completed()).isEqualTo(1);
        assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.APPLIED); assertThat(pending()).isZero();
    }
    @Test void successfulAccountDrainResetsBackoffForTheNextPurchase() {
        var clock = new WorkerClock(); submit(request("tx")); var work = lookupWork(clock);
        var l = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id")); work.defer(l, NOW, "RETRY");
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.attempts", 50L)));
        lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("tx")), null)).tick(10);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).get("lookup", Document.class).getString("failure")).isEqualTo("IDLE");
        submit(request("new-purchase")); lookupWorker(clock, (u, k) -> page(List.of(), null)).tick(10);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(5));
    }
    @Test void twoAccountsAreIndependentlyCoalescedAndNeverBorrowSources() {
        var clock = new WorkerClock(); submit(request("own"));
        String other = UUID.randomUUID().toString();
        String otherRef = new PaymentAccountService(store, props, clock).getOrCreate(other, NOW.plusSeconds(60)).purchaseAccountRefId();
        var otherRequest = request("other"); sync.sync(other, NOW.plusSeconds(60), otherRequest);
        var worker = lookupWorker(clock, (u, k) -> u.getPath().contains(otherRef)
                ? page(List.of(apiPurchase("other").append("customer_id", otherRef).append("original_customer_id", otherRef)), null)
                : page(List.of(apiPurchase("own")), null));
        assertThat(worker.tick(100).completed()).isEqualTo(2);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(2);
        assertThat(sync.sync(other, NOW.plusSeconds(60), otherRequest).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(db.getCollection("payment_account_lifecycles").find().into(new ArrayList<>())).allSatisfy(d -> assertThat(d.getLong("pendingEvidence")).isZero());
    }
    @Test void epochChangedDuringHttpCannotCommitResponseOrAdvanceCursor() {
        var clock = new WorkerClock(); submit(request("tx"));
        lookupWorker(clock, (u, k) -> {
            db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
            return page(List.of(apiPurchase("tx")), "next");
        }).tick(10);
        assertThat(db.getCollection("payment_provider_inbox").countDocuments()).isZero(); assertThat(pending()).isEqualTo(1);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).get("lookup", Document.class).getLong("pages")).isZero();
    }
    @Test void accountLookupPersistsEncryptedPaginationAndResumesWithNewWorker() {
        var clock = new WorkerClock(); submit(request("last"));
        var worker = lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("first")), "secret-page"));
        assertThat(worker.tick(100).deferred()).isEqualTo(1);
        Document cursor = first(PaymentAccountLookupSchema.COLLECTION); assertThat(cursor.toJson()).doesNotContain("secret-page");
        assertThat(cursor.get("lookup", Document.class).getLong("pages")).isEqualTo(1);
        clock.now = NOW.plusSeconds(1);
        var restarted = lookupWorker(clock, (u, k) -> { assertThat(u.getRawQuery()).contains("starting_after=secret-page"); return page(List.of(apiPurchase("last")), null); });
        assertThat(restarted.tick(10).completed()).isEqualTo(1); assertThat(pending()).isZero();
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).get("lookup", Document.class).get("cursor")).isNull();
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(2);
    }
    @Test void accountLookupCrashBeforeCheckpointReplaysWithoutDuplicateGrant() {
        var clock = new WorkerClock(); submit(request("missing")); var work = lookupWork(clock);
        var l = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id"));
        var o = purchase("pre-crash", "tx"); core.receive(o, l.ticket(), s -> work.guard(s, l)); core.apply(o.providerEventKey(), l.ticket(), s -> work.guard(s, l));
        clock.now = NOW.plusSeconds(30);
        var result = lookupWorker(clock, (u, k) -> { assertThat(u.getQuery()).doesNotContain("starting_after"); return page(List.of(apiPurchase("tx")), null); }).tick(10);
        assertThat(result.deferred()).isEqualTo(1); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
    }
    @Test void accountLeaseAndEpochFenceLateHttpAndStaleCheckpoint() {
        var clock = new WorkerClock(); submit(request("tx"));
        var worker = lookupWorker(clock, (u, k) -> { clock.now = NOW.plusSeconds(30); return page(List.of(apiPurchase("tx")), null); });
        assertThat(worker.tick(10).failed()).isEqualTo(1); assertThat(db.getCollection("payment_provider_inbox").countDocuments()).isZero();
        var work = lookupWork(clock); var l = work.claim(first(PaymentAccountLookupSchema.COLLECTION).getString("_id"));
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> work.checkpoint(l, "next", clock.instant())).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> core.receive(purchase("late", "tx"), l.ticket(), s -> work.guard(s, l))).isInstanceOf(PaymentException.class);
        assertThat(pending()).isEqualTo(1);
    }
    @Test void anotherAccountWorkerCannotDispatchWhileFirstHoldsLease() {
        var clock = new WorkerClock(); submit(request("tx"));
        var second = lookupWorker(clock, (u, k) -> { throw new AssertionError("coalesced"); });
        var first = lookupWorker(clock, (u, k) -> { assertThat(second.tick(10).claimed()).isZero(); return page(List.of(apiPurchase("tx")), null); });
        assertThat(first.tick(10).completed()).isEqualTo(1); assertThat(pending()).isZero();
    }
    @Test void emptyAnd404RetryWithoutInferringNoPurchaseAndHonorRetryAfter() {
        var clock = new WorkerClock(); submit(request("missing"));
        assertThat(lookupWorker(clock, (u, k) -> page(List.of(), null)).tick(10).deferred()).isEqualTo(1);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(5));
        clock.now = NOW.plusSeconds(5);
        assertThat(lookupWorker(clock, (u, k) -> new RevenueCatReadClient.Response(404, Map.of(), new byte[0])).tick(10).failed()).isEqualTo(1);
        clock.now = NOW.plusSeconds(15);
        assertThat(lookupWorker(clock, (u, k) -> new RevenueCatReadClient.Response(429, Map.of("Retry-After", List.of("600")), new byte[0])).tick(10).failed()).isEqualTo(1);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt").toInstant()).isEqualTo(clock.now.plusSeconds(600));
        assertThat(pending()).isEqualTo(1); assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("PENDING");
        assertThat(db.getCollection("purchases").countDocuments()).isZero();
    }
    @Test void authHandoffDrainsMoreThanHundredCommandsWithoutRepeatedHttp() {
        var clock = new WorkerClock(); for (int i = 0; i < 101; i++) submit(request("tx-" + i));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var worker = lookupWorker(clock, (u, k) -> { calls.incrementAndGet(); return new RevenueCatReadClient.Response(401, Map.of(), new byte[0]); });
        assertThat(worker.tick(10).failed()).isEqualTo(1);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments(new Document("state", "BLOCKED_AUTH"))).isEqualTo(100);
        clock.now = NOW.plusSeconds(1); worker.tick(10);
        assertThat(calls.get()).isEqualTo(1); assertThat(pending()).isEqualTo(101);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments(new Document("state", "BLOCKED_AUTH"))).isEqualTo(101);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt")).isNull();
    }
    @Test void retryBudgetExhaustionHandsOffInBatchesWithoutHttp() {
        var clock = new WorkerClock(); for (int i = 0; i < 101; i++) submit(request("tx-" + i)); clock.now = NOW.plus(Duration.ofHours(24));
        var worker = lookupWorker(clock, (u, k) -> { throw new AssertionError("budget exhausted"); });
        worker.tick(10); clock.now = clock.now.plusSeconds(1); worker.tick(10);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments(new Document("state", "REVIEW_REQUIRED"))).isEqualTo(101);
        assertThat(pending()).isEqualTo(101);
    }
    @Test void providerOwnerMismatchIsReviewNotGrant() {
        var clock = new WorkerClock(); submit(request("tx"));
        lookupWorker(clock, (u, k) -> page(List.of(apiPurchase("tx").append("original_customer_id", UUID.randomUUID().toString())), null)).tick(10);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(db.getCollection("purchases").countDocuments()).isZero(); assertThat(pending()).isEqualTo(1);
        var fresh = submit(request("later")); assertThat(fresh.status()).isEqualTo(PaymentSyncResult.Status.PENDING);
        assertThat(db.getCollection(PaymentSyncSchema.COLLECTION).countDocuments(new Document("state", "REVIEW_REQUIRED"))).isEqualTo(2);
        assertThat(pending()).isEqualTo(2);
    }
    @Test void withdrawalDuringHttpNeverGrantsPaidAccess() {
        var clock = new WorkerClock(); submit(request("tx"));
        lookupWorker(clock, (u, k) -> { withdraw(); return page(List.of(apiPurchase("tx")), null); }).tick(10);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isZero(); assertThat(pending()).isEqualTo(2);
    }
    @Test void corruptEncryptedCursorIsHandedOffWithoutRepeatedNetworkOrPlaintextStorage() {
        var clock = new WorkerClock(); submit(request("tx")); lookupWorker(clock, (u, k) -> page(List.of(), "sensitive-cursor")).tick(10);
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("lookup.cursor.ciphertext", "invalid")));
        clock.now = NOW.plusSeconds(1); lookupWorker(clock, (u, k) -> { throw new AssertionError("invalid checkpoint"); }).tick(10);
        assertThat(first(PaymentSyncSchema.COLLECTION).getString("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(first(PaymentAccountLookupSchema.COLLECTION).getDate("nextAttemptAt")).isNull();
    }
    @Test void cursorMigrationValidatesExistingRowsAndRejectsDrift() {
        barrier("ALL_BLOCKED"); PaymentAccountLookupSchema.installUnderMaintenance(db, true);
        assertThatThrownBy(() -> PaymentAccountLookupSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        db.runCommand(new Document("collMod", PaymentAccountLookupSchema.COLLECTION).append("validator", new Document()));
        PaymentAccountLookupSchema.installUnderMaintenance(db, true); PaymentAccountLookupSchema.verify(db);
        db.runCommand(new Document("collMod", PaymentAccountLookupSchema.COLLECTION).append("validator", new Document()));
        db.getCollection(PaymentAccountLookupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("unexpected", true)));
        assertThatThrownBy(() -> PaymentAccountLookupSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        assertThat(first(PaymentAccountLookupSchema.COLLECTION)).containsKey("unexpected");
    }
    @Test void networkReceptionRetainsDispatchEpochAndLeaseEvenForDuplicates() {
        var clock = new WorkerClock(); var work = new PaymentSourceWork(store, props, clock);
        var seed = purchase("seed", "seed"); core.receive(seed);
        var lease = work.claim(first("payment_provider_inbox").getString("_id"));
        var response = purchase("network", "network-tx");
        assertThat(core.receive(response, lease.ticket(), s -> work.guard(s, lease))).isEqualTo(PurchaseCore.Outcome.RECEIVED);
        assertThat(pending()).isEqualTo(2);
        clock.now = NOW.plusSeconds(30);
        assertThatThrownBy(() -> core.receive(response, lease.ticket(), s -> work.guard(s, lease))).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> core.receive(purchase("late", "late"), lease.ticket(), s -> work.guard(s, lease))).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("payment_provider_inbox").countDocuments()).isEqualTo(2);
        assertThat(pending()).isEqualTo(2);
    }
    @Test void networkReceptionRollsBackLeaseWriteAndPendingWhenGuardFails() {
        var work = new PaymentSourceWork(store, props, Clock.fixed(NOW, ZoneOffset.UTC)); core.receive(purchase("seed", "seed"));
        var lease = work.claim(first("payment_provider_inbox").getString("_id"));
        assertThatThrownBy(() -> core.receive(purchase("failed", "tx"), lease.ticket(), s -> {
            work.guard(s, lease); throw PaymentException.unavailable();
        })).isInstanceOf(PaymentException.class);
        assertThat(first("payment_provider_inbox").get("work", Document.class).getLong("fenceVersion")).isZero();
        assertThat(pending()).isEqualTo(1);
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> core.receive(purchase("epoch", "tx"), lease.ticket(), s -> work.guard(s, lease))).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("payment_provider_inbox").countDocuments()).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
    }
    @Test void networkReceptionRejectsAllBlockedButAllowsInputDuringPurchaseBlock() {
        var fence = new PaymentRecoveryFence(store, props); var ticket = fence.capture(); barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> core.receive(purchase("blocked", "tx"), ticket, s -> {})).isInstanceOf(PaymentException.class);
        assertThat(pending()).isZero(); barrier("PURCHASE_BLOCKED");
        assertThat(core.receive(purchase("input", "tx"), ticket, s -> {})).isEqualTo(PurchaseCore.Outcome.RECEIVED);
        assertThat(pending()).isEqualTo(1); assertThat(db.getCollection("purchases").countDocuments()).isZero();
    }
    @Test void sourceWorkerAppliesAndCompletesAllMatchingHintsWithoutProviderCall() {
        var a = request("same-tx"); var b = request("same-tx"); submit(a); submit(b); core.receive(purchase("webhook", "same-tx"));
        assertThat(pending()).isEqualTo(3);
        var result = worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(100);
        assertThat(result).isEqualTo(new PaymentSourceWorker.Tick(1, 1, 0, 0));
        assertThat(submit(a).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(submit(b).status()).isEqualTo(PaymentSyncResult.Status.APPLIED); assertThat(pending()).isZero();
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
        assertThat(worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(100).claimed()).isZero();
    }
    @Test void sourceWorkerNeverSelectsUnsignedHintsOrIgnoredWebhook() {
        submit(request("hint-only")); var o = purchase("ignored", "unused");
        core.receiveWebhook(new WebhookReceipt("SANDBOX", o.providerEventKey(), "a".repeat(64), lookup.version(), null, ref,
                WebhookReceipt.Disposition.IGNORED, "TEST"));
        long control = first("payment_recovery_controls").getLong("version");
        assertThat(worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(100)).isEqualTo(new PaymentSourceWorker.Tick(0, 0, 0, 0));
        assertThat(first("payment_recovery_controls").getLong("version")).isEqualTo(control);
        assertThat(pending()).isEqualTo(1); assertThat(db.getCollection("purchases").countDocuments()).isZero();
    }
    @Test void leaseExpiryAndReclaimRejectOldWorkerInsideFinancialTransaction() {
        var clock = new WorkerClock(); var work = new PaymentSourceWork(store, props, clock);
        var o = purchase("leased", "tx"); core.receive(o); String id = first("payment_provider_inbox").getString("_id");
        var old = work.claim(id); assertThat(work.claim(id)).isNull(); clock.now = NOW.plusSeconds(30);
        assertThatThrownBy(() -> core.apply(o.providerEventKey(), old.ticket(), s -> work.guard(s, old))).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("purchases").countDocuments()).isZero(); assertThat(pending()).isEqualTo(1);
        var next = work.claim(id); assertThat(next.version()).isGreaterThan(old.version());
        assertThatThrownBy(() -> work.finish(old, null, null)).isInstanceOf(PaymentException.class);
        assertThat(core.apply(o.providerEventKey(), next.ticket(), s -> work.guard(s, next))).isEqualTo(PurchaseCore.Outcome.APPLIED);
        assertThat(pending()).isZero();
    }
    @Test void recoveryEpochChangeDoesNotLetOldWorkerCaptureNewAuthority() {
        var work = new PaymentSourceWork(store, props, Clock.fixed(NOW, ZoneOffset.UTC)); var o = purchase("epoch", "tx"); core.receive(o);
        var lease = work.claim(first("payment_provider_inbox").getString("_id"));
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> core.apply(o.providerEventKey(), lease.ticket(), s -> work.guard(s, lease))).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> work.renew(lease)).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("purchases").countDocuments()).isZero(); assertThat(pending()).isEqualTo(1);
    }
    @Test void restartAfterApplyBeforeSyncCompletionDoesNotPayAgain() {
        var clock = new WorkerClock(); var request = request("tx"); submit(request); var o = purchase("crash", "tx"); core.receive(o);
        var work = new PaymentSourceWork(store, props, clock); var lease = work.claim(first("payment_provider_inbox").getString("_id"));
        core.apply(o.providerEventKey(), lease.ticket(), s -> work.guard(s, lease));
        assertThat(pending()).isEqualTo(1); clock.now = NOW.plusSeconds(31);
        assertThat(worker(clock).tick(10).completed()).isEqualTo(1);
        assertThat(pending()).isZero(); assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(db.getCollection("payment_ledger").countDocuments()).isEqualTo(2);
    }
    @Test void blockedPurchaseDefersWithoutBusyLoopAndLaterOpens() {
        var clock = new WorkerClock(); submit(request("tx")); core.receive(purchase("blocked", "tx")); barrier("PURCHASE_BLOCKED");
        assertThat(worker(clock).tick(100)).isEqualTo(new PaymentSourceWorker.Tick(1, 0, 1, 0));
        assertThat(worker(clock).tick(100).claimed()).isZero(); assertThat(pending()).isEqualTo(2);
        barrier("OPEN"); clock.now = NOW.plusSeconds(8);
        assertThat(worker(clock).tick(100).completed()).isEqualTo(1); assertThat(pending()).isZero();
    }
    @Test void reviewHandoffKeepsCasePendingAndMarksSyncForSupport() {
        var r = request("tx"); submit(r); withdraw(); core.receive(purchase("withdrawn-source", "tx"));
        assertThat(worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(10).completed()).isEqualTo(1);
        assertThat(first("payment_sync_commands").getString("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(first("payment_purchase_cases").containsKey("work")).isFalse();
        assertThat(pending()).isEqualTo(2); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isZero();
    }
    @Test void workerRetryBudgetMovesUnappliedSourceToReviewWithoutDroppingPending() {
        var clock = new WorkerClock(); submit(request("tx")); core.receive(purchase("budget", "tx")); barrier("PURCHASE_BLOCKED");
        worker(clock).tick(10); clock.now = NOW.plus(Duration.ofHours(24));
        assertThat(worker(clock).tick(10).completed()).isEqualTo(1);
        assertThat(first("payment_purchase_cases").getString("reason")).isEqualTo("WORKER_RETRY_EXHAUSTED");
        assertThat(pending()).isEqualTo(2); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isZero();
    }
    @Test void failureAfterBindingResumesUsingDurableSource() {
        var clock = new WorkerClock(); var r = request("tx"); submit(r); core.receive(purchase("bind-crash", "tx"));
        var fail = new PaymentSyncService(store, props, clock, lookup, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app")) {
            @Override public void complete(String id, long version, PaymentRecoveryFence.Ticket ticket, java.util.function.Consumer<com.mongodb.client.ClientSession> guard) {
                throw PaymentException.unavailable();
            }
        };
        assertThat(new PaymentSourceWorker(store, props, core, fail, clock, () -> 0).tick(10).failed()).isEqualTo(1);
        assertThat(first("payment_sync_commands").getString("sourceObservationKey")).isNotNull(); assertThat(pending()).isEqualTo(1);
        clock.now = NOW.plusSeconds(5); assertThat(worker(clock).tick(10).completed()).isEqualTo(1); assertThat(pending()).isZero();
        assertThat(db.getCollection("payment_ledger").countDocuments()).isEqualTo(2);
    }
    @Test void staleLeaseCannotBindOrCompleteSyncAndAllBlockedCannotClaim() {
        var clock = new WorkerClock(); var o = purchase("stale-sync", "tx"); submit(request("tx")); core.receive(o);
        var work = new PaymentSourceWork(store, props, clock); var lease = work.claim(first("payment_provider_inbox").getString("_id"));
        core.apply(o.providerEventKey(), lease.ticket(), s -> work.guard(s, lease)); var command = first("payment_sync_commands");
        clock.now = NOW.plusSeconds(31);
        assertThatThrownBy(() -> sync.bindObservation(command.getString("_id"), 1, o.providerEventKey(), lease.ticket(), s -> work.guard(s, lease)))
                .isInstanceOf(PaymentException.class);
        assertThat(first("payment_sync_commands").getString("sourceObservationKey")).isNull(); assertThat(pending()).isEqualTo(1);
        barrier("ALL_BLOCKED"); assertThat(worker(clock).tick(10).failed()).isEqualTo(1); assertThat(pending()).isEqualTo(1);
    }
    @Test void twoWorkersConvergeWithoutDuplicateGrant() throws Exception {
        submit(request("tx")); core.receive(purchase("concurrent", "tx"));
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(10));
            var b = pool.submit(() -> worker(Clock.fixed(NOW, ZoneOffset.UTC)).tick(10));
            assertThat(a.get().failed() + b.get().failed()).isZero();
        }
        assertThat(pending()).isZero(); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void syncFanoutIsBoundedAndRemainingCommandsResumeNextTick() {
        var clock = new WorkerClock(); for (int i = 0; i < 101; i++) submit(request("many"));
        submit(request("unrelated")); core.receive(purchase("many-source", "many"));
        assertThat(worker(clock).tick(10)).isEqualTo(new PaymentSourceWorker.Tick(1, 0, 1, 0));
        assertThat(pending()).isEqualTo(2); clock.now = NOW.plusSeconds(8);
        assertThat(worker(clock).tick(10).completed()).isEqualTo(1); assertThat(pending()).isEqualTo(1);
        assertThat(db.getCollection("payment_sync_commands").countDocuments(new Document("state", "APPLIED"))).isEqualTo(101);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void terminalSyncFailureBudgetParksWithoutDeletingFinancialOrPendingEvidence() {
        var clock = new WorkerClock(); submit(request("tx")); core.receive(purchase("terminal-failure", "tx"));
        var fail = new PaymentSyncService(store, props, clock, lookup, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app")) {
            @Override public void complete(String id, long version, PaymentRecoveryFence.Ticket ticket, java.util.function.Consumer<com.mongodb.client.ClientSession> guard) {
                throw PaymentException.unavailable();
            }
        };
        var worker = new PaymentSourceWorker(store, props, core, fail, clock, () -> 0);
        worker.tick(10); clock.now = NOW.plus(Duration.ofHours(24)); worker.tick(10);
        Document work = first("payment_provider_inbox").get("work", Document.class);
        assertThat(work.getString("failure")).isEqualTo("RETRY_EXHAUSTED"); assertThat(work.getDate("nextAttemptAt")).isNull();
        assertThat(pending()).isEqualTo(1); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
        assertThat(worker.tick(10).claimed()).isZero();
    }
    @Test void duplicatePendingIsAtomicAndStoresNoRawHint() {
        var r = request("sensitive-store-hint"); assertThat(submit(r).status()).isEqualTo(PaymentSyncResult.Status.PENDING); submit(r);
        assertThat(pending()).isEqualTo(1); assertThat(db.getCollection("payment_sync_commands").countDocuments()).isEqualTo(1);
        assertThat(first("payment_sync_commands").toJson()).doesNotContain("sensitive-store-hint");
        assertThat(db.getCollection("purchases").countDocuments()).isZero();
        assertThatThrownBy(() -> submit(new PaymentSyncRequest(r.operationId(), r.store(), "different")))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(pending()).isEqualTo(1);
    }
    @Test void sourceAndCommandHaveIndependentPendingAndCompletionNeverPaysTwice() {
        var r = request("tx"); submit(r); var o = purchase("event", "tx"); core.receive(o); assertThat(pending()).isEqualTo(2);
        var row = first("payment_sync_commands"); var ticket = sync.capture();
        long v = sync.bindObservation(row.getString("_id"), 1L, o.providerEventKey(), ticket);
        sync.complete(row.getString("_id"), v, ticket); assertThat(pending()).isEqualTo(2);
        core.apply(o.providerEventKey()); assertThat(pending()).isEqualTo(1);
        sync.complete(row.getString("_id"), v, ticket); sync.complete(row.getString("_id"), v, ticket);
        assertThat(pending()).isZero(); assertThat(submit(r).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isEqualTo(1);
    }
    @Test void existingVerifiedOwnPurchaseReturnsAppliedWithoutPending() {
        apply(purchase("event", "tx")); assertThat(submit(request("tx")).status()).isEqualTo(PaymentSyncResult.Status.APPLIED); assertThat(pending()).isZero();
    }
    @Test void replaySettlesVerifiedPurchaseOnceWithoutWorkerOrAnotherGrant() throws Exception {
        var r = request("tx"); submit(r); var before = first("payment_sync_commands");
        apply(purchase("event", "tx"));
        long ledgerCount = db.getCollection("payment_ledger").countDocuments();
        assertThat(pending()).isOne();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> submit(r)); var b = pool.submit(() -> submit(r));
            assertThat(a.get(10, TimeUnit.SECONDS).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
            assertThat(b.get(10, TimeUnit.SECONDS).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        }
        assertThat(pending()).isZero();
        assertThat(first("payment_sync_commands").get("createdAt")).isEqualTo(before.get("createdAt"));
        assertThat(first("payment_sync_commands").get("payloadDigest")).isEqualTo(before.get("payloadDigest"));
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isOne();
        assertThat(db.getCollection("payment_ledger").countDocuments()).isEqualTo(ledgerCount);
    }
    @Test void replayDoesNotBypassBoundReviewOrUnverifiedPurchase() {
        var r = request("tx"); submit(r);
        assertThat(submit(r).status()).isEqualTo(PaymentSyncResult.Status.PENDING);
        var o = purchase("event", "tx"); core.receive(o);
        var row = first("payment_sync_commands");
        sync.bindObservation(row.getString("_id"), row.getLong("version"), o.providerEventKey(), sync.capture());
        apply(o);
        assertThat(submit(r).status()).isEqualTo(PaymentSyncResult.Status.PENDING);
        assertThat(pending()).isOne();
    }
    @Test void foreignPurchaseCannotBeClaimed() {
        apply(purchase("event", "tx")); String other = "22222222-2222-4222-8222-222222222222";
        new PaymentAccountService(store, props, Clock.fixed(NOW, ZoneOffset.UTC)).getOrCreate(other, NOW.plusSeconds(60));
        assertThatThrownBy(() -> sync.sync(other, NOW.plusSeconds(60), request("tx"))).isInstanceOfSatisfying(PaymentException.class,
                e -> assertThat(e.code()).isEqualTo("PURCHASE_BINDING_CONFLICT"));
        assertThat(db.getCollection("payment_sync_commands").countDocuments()).isZero();
    }
    @Test void missingAccountExpiryAndWithdrawalDoNotCreateCommands() {
        assertThatThrownBy(() -> sync.sync(UUID.randomUUID().toString(), NOW.plusSeconds(60), request("tx"))).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> sync.sync(USER, NOW, request("tx"))).isInstanceOf(PaymentException.class);
        withdraw(); assertThatThrownBy(() -> submit(request("tx"))).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(403));
        assertThat(db.getCollection("payment_sync_commands").countDocuments()).isZero(); assertThat(pending()).isZero();
    }
    @Test void withdrawalAfterAcceptancePreservesReviewPending() {
        submit(request("tx")); var o = purchase("event", "tx"); core.receive(o);
        var row = first("payment_sync_commands"); var ticket = sync.capture(); long v = sync.bindObservation(row.getString("_id"), 1, o.providerEventKey(), ticket);
        withdraw(); core.apply(o.providerEventKey()); sync.complete(row.getString("_id"), v, ticket);
        assertThat(first("payment_sync_commands").getString("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(pending()).isEqualTo(2); assertThat(db.getCollection("subscription_entitlements").countDocuments()).isZero();
    }
    @Test void staleEpochAndWrongSourceCannotComplete() {
        submit(request("tx")); var wrong = purchase("wrong", "other"); core.receive(wrong); var row = first("payment_sync_commands"); var ticket = sync.capture();
        assertThatThrownBy(() -> sync.bindObservation(row.getString("_id"), 1, wrong.providerEventKey(), ticket)).isInstanceOf(PaymentException.class);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> sync.complete(row.getString("_id"), 1, ticket)).isInstanceOf(PaymentException.class); assertThat(pending()).isEqualTo(2);
    }
    @Test void overflowAndBarrierRollBackCommand() {
        barrier("ALL_BLOCKED"); assertThatThrownBy(() -> submit(request("tx"))).isInstanceOf(PaymentException.class); barrier("OPEN");
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$set", new Document("pendingEvidence", Long.MAX_VALUE)));
        assertThatThrownBy(() -> submit(request("tx"))).isInstanceOf(PaymentException.class); assertThat(db.getCollection("payment_sync_commands").countDocuments()).isZero();
    }
    @Test void concurrentRetriesConverge() throws Exception {
        var r = request("tx"); try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> submit(r)); var b = pool.submit(() -> submit(r));
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        assertThat(pending()).isEqualTo(1); assertThat(db.getCollection("payment_sync_commands").countDocuments()).isEqualTo(1);
    }
    @ParameterizedTest @EnumSource(PaymentOffer.class)
    void paidReadUsesExactPeriodAndExclusiveEnd(PaymentOffer offer) {
        apply(observation("event", "tx", offer, PurchaseObservation.Kind.PURCHASE));
        var active = read(NOW); assertThat(active.active()).isTrue(); assertThat(active.activeOfferCode()).isEqualTo(offer.name());
        assertThat(read(NOW.minusSeconds(100).plusSeconds(offer.durationSeconds)).active()).isFalse();
    }
    @Test void pendingIsNotAnEntitlementAndRepeatedReadNeverWrites() {
        submit(request("tx")); Document before = first("payment_sync_commands");
        assertThat(read(NOW).hasPendingSync()).isTrue(); assertThat(read(NOW).active()).isFalse();
        assertThat(first("payment_sync_commands")).isEqualTo(before); assertThat(pending()).isEqualTo(1);
    }
    @Test void stackingAndRefundUncertainty() {
        apply(purchase("a", "a")); apply(purchase("b", "b"));
        assertThat(read(NOW).continuousAccessEndsAt()).isEqualTo(NOW.minusSeconds(100).plusSeconds(172800));
        core.receive(observation("refund", "a", PaymentOffer.PREMIUM_1D, PurchaseObservation.Kind.REFUND));
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void unattributedEvidenceDoesNotBlockOtherAccountsOrCreateRights(boolean reviewed) {
        apply(purchase("owned", "owned"));
        var unknown = new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app",
                lookup.transaction("SANDBOX", "PLAY_STORE", "fake.app", "unknown"), lookup.version(),
                lookup.event("SANDBOX", "fake-project", "unknown"), PurchaseObservation.Kind.PURCHASE,
                UUID.randomUUID().toString(), PaymentOffer.PREMIUM_1D.name(), NOW.minusSeconds(100), null);
        core.receive(unknown);
        if (reviewed) core.apply(unknown.providerEventKey());
        var before = first("payment_purchase_cases");
        if (reviewed) assertThat(before.getString("reason")).isEqualTo("OWNER_UNRESOLVED");
        var inbox = db.getCollection("payment_provider_inbox").find(new Document("accountId", null)).into(new ArrayList<>());
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.READY);
        assertThat(read(NOW).active()).isTrue();
        assertThat(read(NOW).hasPendingSync()).isFalse();
        var absent = snapshots.execute(() -> reader.read("22222222-2222-4222-8222-222222222222", NOW));
        assertThat(absent.status()).isEqualTo(PaidQueryResponse.Status.READY);
        assertThat(absent.active()).isFalse();
        assertThat(first("payment_purchase_cases")).isEqualTo(before);
        assertThat(db.getCollection("payment_provider_inbox").find(new Document("accountId", null)).into(new ArrayList<>())).isEqualTo(inbox);
        assertThat(db.getCollection("purchases").countDocuments()).isOne();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"QUANTITY_UNSUPPORTED", "QUANTITY_REQUIRES_LOOKUP", "OWNER_UNRESOLVED", "OWNERSHIP_UNSUPPORTED"})
    void unverifiedReviewPreservesExistingRightsButNeverGrantsOrDropsEvidence(String reason) {
        core.receiveWebhook(new WebhookReceipt("SANDBOX", lookup.event("SANDBOX", "fake-project", "quantity"),
                "a".repeat(64), lookup.version(), null, ref, WebhookReceipt.Disposition.REVIEW, reason));
        var review = first("payment_purchase_cases");
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
        assertThat(db.getCollection("purchases").countDocuments()).isZero();
        apply(purchase("owned", "owned"));
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.READY);
        assertThat(read(NOW).active()).isTrue();
        assertThat(first("payment_purchase_cases")).isEqualTo(review);
        assertThat(pending()).isOne();
        assertThat(read(NOW.plusSeconds(86401)).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
        core.receive(observation("refund", "owned", PaymentOffer.PREMIUM_1D, PurchaseObservation.Kind.REFUND));
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
    }
    @Test void quantityLookupCaseUsesStrictRestEvidenceWithoutDroppingReview() {
        core.receiveWebhook(new WebhookReceipt("SANDBOX", lookup.event("SANDBOX", "fake-project", "quantity"),
                "a".repeat(64), lookup.version(), null, ref, WebhookReceipt.Disposition.REVIEW, "QUANTITY_REQUIRES_LOOKUP"));
        var request = request("tx"); submit(request);
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
        assertThat(lookupWorker(new WorkerClock(), (u, k) -> page(List.of(apiPurchase("tx")), null)).tick(10).failed()).isZero();
        assertThat(submit(request).status()).isEqualTo(PaymentSyncResult.Status.APPLIED);
        assertThat(read(NOW).active()).isTrue();
        assertThat(db.getCollection("purchases").countDocuments()).isOne();
        assertThat(db.getCollection("payment_purchase_cases").countDocuments()).isOne();
        assertThat(pending()).isOne(); // Unknown webhook identity is not silently marked resolved.
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"EVENT_REQUIRES_LOOKUP", "IMMUTABLE_FACT_CONFLICT", "TRANSACTION_REVERSAL_UNRESOLVED"})
    void otherAccountReviewsContinueToBlock(String reason) {
        apply(purchase("owned", "owned"));
        core.receiveWebhook(new WebhookReceipt("SANDBOX", lookup.event("SANDBOX", "fake-project", "review"),
                "a".repeat(64), lookup.version(), null, ref, WebhookReceipt.Disposition.REVIEW, reason));
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"OWNER_UNRESOLVED", "OWNERSHIP_UNSUPPORTED"})
    void normalizedOwnershipReviewStillBlocksExistingRights(String reason) {
        apply(purchase("owned", "owned"));
        var observation = purchase("conflict", "conflict");
        core.receiveWebhook(new WebhookReceipt("SANDBOX", observation.providerEventKey(),
                "a".repeat(64), lookup.version(), null, ref, WebhookReceipt.Disposition.REVIEW, reason));
        db.getCollection("payment_purchase_cases").updateOne(new Document("providerEventKey", observation.providerEventKey()),
                new Document("$set", new Document("normalizedObservation", first("payment_provider_inbox").get("normalizedObservation"))));
        assertThat(read(NOW).status()).isEqualTo(PaidQueryResponse.Status.PENDING);
        assertThat(db.getCollection("purchases").countDocuments()).isOne();
    }
    private void enableReviewResolution() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        RefundCoreSchema.upgradeUnderMaintenance(db, true);
        MixedStorePurchaseSchema.upgradeUnderMaintenance(db, true);
        PurchaseReviewResolutionSchema.upgradeUnderMaintenance(db, true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "OPEN")));
        props.setReviewResolutionEnabled(true);
        core = new PurchaseCore(store, props, Clock.fixed(NOW, ZoneOffset.UTC), Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
    }
    private WebhookReceipt matchableReview(String event, String tx) {
        var p = purchase(event, tx);
        return new WebhookReceipt("SANDBOX", p.providerEventKey(), "a".repeat(64), lookup.version(), null, ref,
                WebhookReceipt.Disposition.REVIEW, "OWNER_UNRESOLVED",
                new ReviewPurchaseCandidate(p.environment(), p.store(), p.appId(), p.transactionKey(), p.lookupKeyVersion(),
                        p.ownerRefId(), p.storeProductId(), p.purchasedAt()));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void exactVerifiedPurchaseResolvesReviewInEitherOrderWithoutRegrant(boolean purchaseFirst) {
        enableReviewResolution();
        var receipt = matchableReview("review", "tx");
        if (purchaseFirst) apply(purchase("verified", "tx"));
        core.receiveWebhook(receipt);
        if (!purchaseFirst) {
            assertThat(pending()).isOne();
            assertThat(first("payment_purchase_cases").getString("reviewState")).isNull();
            apply(purchase("verified", "tx"));
        }
        var resolved = first("payment_purchase_cases");
        assertThat(resolved.getString("reviewState")).isEqualTo("RESOLVED");
        assertThat(resolved.getString("resolvedObservationKey")).isNotBlank();
        assertThat(resolved.getString("purchaseId")).isEqualTo(first("purchases").getString("_id"));
        assertThat(resolved.getBoolean("counted")).isFalse();
        assertThat(pending()).isZero();
        core.receiveWebhook(receipt); apply(purchase("verified", "tx"));
        assertThat(first("payment_purchase_cases")).isEqualTo(resolved);
        assertThat(db.getCollection("subscription_entitlements").countDocuments()).isOne();
        assertThat(pending()).isZero();
        assertThat(read(NOW.plusSeconds(86401)).status()).isEqualTo(PaidQueryResponse.Status.READY);
    }
    @Test void unrelatedVerifiedPurchaseDoesNotResolveReview() {
        enableReviewResolution();
        core.receiveWebhook(matchableReview("review", "unverified"));
        apply(purchase("verified", "other"));
        assertThat(first("payment_purchase_cases").getString("resolvedObservationKey")).isNull();
        assertThat(pending()).isOne();
        assertThat(read(NOW).active()).isTrue();
    }
    @Test void legacyReviewRequiresSameDigestAuthenticatedReplayToRecoverHint() {
        var receipt = matchableReview("review", "tx");
        core.receiveWebhook(receipt); // old writer does not persist hints
        apply(purchase("verified", "tx"));
        enableReviewResolution();
        assertThat(first("payment_purchase_cases").getString("resolvedObservationKey")).isNull();
        core.receiveWebhook(receipt);
        assertThat(first("payment_purchase_cases").getString("reviewState")).isEqualTo("RESOLVED");
        assertThat(pending()).isZero();
    }
    @Test void reviewResolutionRequiresExplicitSchemaPreparation() {
        props.setReviewResolutionEnabled(true);
        assertThatThrownBy(() -> new PurchaseCore(store, props, Clock.fixed(NOW, ZoneOffset.UTC),
                Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version())).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"environment", "store", "appId", "transactionKey", "lookupKeyVersion", "ownerRefId", "storeProductId", "purchasedAt"})
    void everyCandidateIdentityFieldMustMatchBeforeResolution(String field) {
        enableReviewResolution(); core.receiveWebhook(matchableReview("review", "tx"));
        Object different = switch (field) {
            case "environment" -> "PRODUCTION";
            case "store" -> "APP_STORE";
            case "transactionKey" -> "f".repeat(64);
            case "ownerRefId" -> "22222222-2222-4222-8222-222222222222";
            case "purchasedAt" -> Date.from(NOW.minusSeconds(1));
            default -> "different";
        };
        db.getCollection("payment_purchase_cases").updateOne(new Document(), new Document("$set", new Document("reviewCandidate." + field, different)));
        apply(purchase("verified", "tx"));
        assertThat(first("payment_purchase_cases").getString("reviewState")).isNull();
        assertThat(pending()).isOne();
    }
    @Test void legacyReplayDigestConflictNeverBackfillsOrResolves() {
        var receipt = matchableReview("review", "tx"); core.receiveWebhook(receipt);
        apply(purchase("verified", "tx")); enableReviewResolution();
        var changed = new WebhookReceipt(receipt.environment(), receipt.eventKey(), "b".repeat(64), receipt.lookupKeyVersion(),
                null, ref, receipt.disposition(), receipt.reason(), receipt.reviewCandidate());
        assertThatThrownBy(() -> core.receiveWebhook(changed)).isInstanceOf(PaymentException.class);
        assertThat(first("payment_purchase_cases").get("reviewCandidate")).isNull();
        assertThat(pending()).isOne();
    }
    @Test void sameSnapshotDoesNotMixConcurrentPaymentCommit() throws Exception {
        reader.prepare();
        snapshots.execute(() -> {
            assertThat(reader.read(USER, NOW).active()).isFalse();
            try (var pool = Executors.newSingleThreadExecutor()) { pool.submit(() -> apply(purchase("event", "tx"))).get(1, TimeUnit.SECONDS); }
            catch (Exception e) { throw new AssertionError(e); }
            assertThat(reader.read(USER, NOW).active()).isFalse(); return null;
        });
        assertThat(read(NOW).active()).isTrue();
    }
    @Test void missingSchemaNeverAutoCreatesAndReadWithoutSnapshotFails() {
        assertThatThrownBy(() -> reader.read(USER, NOW)).isInstanceOf(RuntimeException.class);
        db.getCollection("payment_sync_commands").drop();
        assertThatThrownBy(reader::prepare).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PaymentSyncSchema.verify(db)).isInstanceOf(RuntimeException.class);
        assertThat(db.listCollectionNames().into(new ArrayList<>())).doesNotContain("payment_sync_commands");
    }
    @Test void newRouteQuotasAreIndependentAndResetAtMinuteBoundary() {
        props.setLimiterKey(Base64.getEncoder().encodeToString(new byte[32]));
        var limiter = new PaymentRateLimiter(store, props, Clock.fixed(NOW.plusMillis(1500), ZoneOffset.UTC));
        for (int i = 0; i < 12; i++) assertThat(limiter.acquire(PaymentRateLimiter.Route.SYNC, USER)).isZero();
        assertThat(limiter.acquire(PaymentRateLimiter.Route.SYNC, USER)).isEqualTo(59);
        for (int i = 0; i < 60; i++) assertThat(limiter.acquire(PaymentRateLimiter.Route.ENTITLEMENTS, USER)).isZero();
        assertThat(limiter.acquire(PaymentRateLimiter.Route.ENTITLEMENTS, USER)).isEqualTo(59);
        assertThat(new PaymentRateLimiter(store, props, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC)).acquire(PaymentRateLimiter.Route.SYNC, USER)).isZero();
    }
    @Test void missingFutureSlotIsFailureNotFalse() {
        apply(purchase("event", "tx")); db.getCollection("subscription_entitlements").deleteMany(new Document());
        assertThatThrownBy(() -> read(NOW)).isInstanceOf(RuntimeException.class);
    }
    @Test void commandSchemaRejectsInt32AndIndexDrift() {
        submit(request("tx"));
        assertThatThrownBy(() -> db.getCollection("payment_sync_commands").updateOne(new Document(), new Document("$set", new Document("version", 2))))
                .isInstanceOf(com.mongodb.MongoException.class);
        db.getCollection("payment_sync_commands").dropIndex("ix_payment_sync_due");
        assertThatThrownBy(() -> PaymentSyncSchema.verify(db)).isInstanceOf(IllegalStateException.class);
    }
}
