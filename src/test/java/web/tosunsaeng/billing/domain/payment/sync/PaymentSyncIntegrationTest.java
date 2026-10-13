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
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class PaymentSyncIntegrationTest {
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
        PurchaseCoreSchema.installUnderMaintenance(db, true); PaymentSyncSchema.installUnderMaintenance(db, true); barrier("OPEN");
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
