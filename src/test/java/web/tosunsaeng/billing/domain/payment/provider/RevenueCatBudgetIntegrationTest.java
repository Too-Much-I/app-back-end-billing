package web.tosunsaeng.billing.domain.payment.provider;

import com.mongodb.client.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class RevenueCatBudgetIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all");
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    static final URI URI_A = URI.create("https://api.revenuecat.com/v2/projects/fake-project/customers/fake/purchases?environment=sandbox");
    static class MutableClock extends Clock {
        volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    MongoClient client; MongoDatabase db; PaymentStore store; MutableClock clock; RevenueCatProjectBudget budget;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        store = new PaymentStore(client, new SimpleMongoClientDatabaseFactory(client, "budget_" + UUID.randomUUID().toString().replace("-", "")));
        db = store.database(); clock = new MutableClock(); RevenueCatBudgetSchema.installUnderMaintenance(db, true);
        budget = new RevenueCatProjectBudget(store, "fake-project", clock);
    }
    @AfterEach void close() { if (client != null) { db.drop(); client.close(); } } // Isolated test database only.
    Document row() { return db.getCollection(RevenueCatBudgetSchema.COLLECTION).find(new Document("_id", "fake-project")).first(); }
    RevenueCatReadClient.Response response(int status) { return new RevenueCatReadClient.Response(status, Map.of(), new byte[0]); }
    void failure(Runnable action, RevenueCatFailure.Kind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RevenueCatFailure.class, e -> assertThat(e.kind()).isEqualTo(kind));
    }
    @Test void sameProjectAcrossTwoInstancesAndEnvironmentsSharesFourSlots() {
        var other = new RevenueCatProjectBudget(store, "fake-project", clock);
        var sandbox = new RevenueCatBudgetedTransport(budget, (uri, secret) -> {
            assertThat(row().getList("slots", Document.class)).hasSize(4); return response(200);
        });
        var leases = List.of(budget.acquire(), other.acquire(), budget.acquire());
        assertThat(sandbox.get(URI_A, "fake").status()).isEqualTo(200);
        var fourth = other.acquire(); failure(budget::acquire, RevenueCatFailure.Kind.RETRY);
        var production = new RevenueCatBudgetedTransport(other, (uri, secret) -> { throw new AssertionError("must not call"); });
        failure(() -> production.get(URI.create(URI_A.toString().replace("sandbox", "production")), "fake"), RevenueCatFailure.Kind.RETRY);
        budget.release(fourth); leases.forEach(other::release);
        assertThat(row().getList("slots", Document.class)).isEmpty();
        assertThat(row().getList("starts", Date.class)).hasSize(5);
    }
    @Test void rollingMinuteSharedAcrossInstancesCannotBeRefundedByRelease() {
        var second = new RevenueCatProjectBudget(store, "fake-project", clock);
        for (int i = 0; i < 240; i++) { var b = i % 2 == 0 ? budget : second; b.release(b.acquire()); }
        assertThatThrownBy(second::acquire).isInstanceOfSatisfying(RevenueCatFailure.class,
                e -> { assertThat(e.kind()).isEqualTo(RevenueCatFailure.Kind.RETRY); assertThat(e.retryAt()).isEqualTo(NOW.plusSeconds(60)); });
        clock.now = NOW.plusSeconds(59).plusMillis(999); failure(budget::acquire, RevenueCatFailure.Kind.RETRY);
        clock.now = NOW.plusSeconds(60); second.release(second.acquire());
        assertThat(row().getList("starts", Date.class)).hasSize(1);
    }
    @Test void concurrentInstancesCannotOverbook() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1); List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(executor.submit(() -> {
                start.await();
                try { new RevenueCatProjectBudget(store, "fake-project", clock).acquire(); return true; }
                catch (RevenueCatFailure e) { assertThat(e.kind()).isEqualTo(RevenueCatFailure.Kind.RETRY); return false; }
            }));
            start.countDown(); int admitted = 0; for (var f : results) if (f.get(10, TimeUnit.SECONDS)) admitted++;
            assertThat(admitted).isEqualTo(4); assertThat(row().getList("slots", Document.class)).hasSize(4);
            assertThat(row().getList("starts", Date.class)).hasSize(4);
        }
    }
    @Test void expiredSlotReclaimsAtBoundaryAndOldReleaseCannotReleaseReplacement() {
        var old = budget.acquire(); clock.now = NOW.plusSeconds(30);
        failure(() -> budget.validate(old), RevenueCatFailure.Kind.RETRY);
        failure(() -> budget.blockAuth(old), RevenueCatFailure.Kind.RETRY);
        var replacement = budget.acquire(); budget.release(old); budget.validate(replacement);
        assertThat(row().getList("slots", Document.class)).hasSize(1); assertThat(row().getBoolean("blockedAuth")).isFalse();
    }
    @Test void lateResponseIsNotReturnedAndDoesNotKeepSlot() {
        var transport = new RevenueCatBudgetedTransport(budget, (uri, secret) -> { clock.now = NOW.plusSeconds(30); return response(200); });
        failure(() -> transport.get(URI_A, "fake"), RevenueCatFailure.Kind.RETRY);
        assertThat(row().getList("slots", Document.class)).isEmpty(); assertThat(row().getList("starts", Date.class)).hasSize(1);
    }
    @Test void anotherCallsAuthFailureFencesAlreadyRunningResponse() {
        var second = new RevenueCatProjectBudget(store, "fake-project", clock);
        var transport = new RevenueCatBudgetedTransport(budget, (uri, secret) -> {
            var permit = second.acquire(); second.blockAuth(permit); second.release(permit); return response(200);
        });
        failure(() -> transport.get(URI_A, "fake"), RevenueCatFailure.Kind.AUTH);
        assertThat(row().getList("slots", Document.class)).isEmpty(); assertThat(row().getBoolean("blockedAuth")).isTrue();
    }
    @Test void unavailableSharedDatabaseNeverFallsBackToHttp() {
        try (MongoClient separate = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var otherStore = new PaymentStore(separate, new SimpleMongoClientDatabaseFactory(separate, db.getName()));
            var otherBudget = new RevenueCatProjectBudget(otherStore, "fake-project", clock);
            var calls = new AtomicInteger();
            var transport = new RevenueCatBudgetedTransport(otherBudget, (uri, secret) -> { calls.incrementAndGet(); return response(200); });
            separate.close();
            assertThatThrownBy(() -> transport.get(URI_A, "fake")).isInstanceOf(RuntimeException.class);
            assertThat(calls.get()).isZero(); assertThat(row()).isNull();
        }
    }
    @Test void failuresReleaseConcurrencyButConsumeRate() {
        var transport = new RevenueCatBudgetedTransport(budget, (uri, secret) -> { throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null); });
        failure(() -> transport.get(URI_A, "fake"), RevenueCatFailure.Kind.RETRY);
        assertThat(row().getList("slots", Document.class)).isEmpty(); assertThat(row().getList("starts", Date.class)).hasSize(1);
    }
    @Test void bothAuthStatusesBlockSubsequentDispatchPersistently() {
        for (int status : List.of(401, 403)) {
            AtomicInteger calls = new AtomicInteger();
            var transport = new RevenueCatBudgetedTransport(budget, (uri, secret) -> { calls.incrementAndGet(); return response(status); });
            assertThat(transport.get(URI_A, "fake").status()).isEqualTo(status);
            failure(() -> transport.get(URI_A, "fake"), RevenueCatFailure.Kind.AUTH);
            var restarted = new RevenueCatProjectBudget(store, "fake-project", clock);
            failure(restarted::acquire, RevenueCatFailure.Kind.AUTH); assertThat(calls.get()).isEqualTo(1);
            long version = row().getLong("version");
            assertThatThrownBy(() -> restarted.resumeUnderMaintenance(version, false)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> restarted.resumeUnderMaintenance(version - 1, true)).isInstanceOf(RuntimeException.class);
            restarted.resumeUnderMaintenance(version, true);
        }
        assertThat(row().getList("starts", Date.class)).hasSize(2);
    }
    @Test void authResumeRequiresNoLivePermitAndPreservesUsage() {
        var permit = budget.acquire(); budget.blockAuth(permit); long version = row().getLong("version");
        assertThatThrownBy(() -> budget.resumeUnderMaintenance(version, true)).isInstanceOf(RuntimeException.class);
        clock.now = NOW.plusSeconds(30); budget.resumeUnderMaintenance(version, true);
        assertThat(row().getList("starts", Date.class)).hasSize(1); assertThat(row().getList("slots", Document.class)).isEmpty();
    }
    @Test void wrongHostOrProjectDoesNotConsumeBudgetOrCallTransport() {
        var transport = new RevenueCatBudgetedTransport(budget, (uri, secret) -> { throw new AssertionError("must not call"); });
        failure(() -> transport.get(URI.create("https://api.revenuecat.com/v2/projects/other/purchases/fake"), "fake"), RevenueCatFailure.Kind.CONTRACT);
        failure(() -> transport.get(URI.create("https://evil.invalid/v2/projects/fake-project/purchases/fake"), "fake"), RevenueCatFailure.Kind.CONTRACT);
        assertThat(row()).isNull();
    }
    @Test void clockRegressionWaitsWithoutCreatingRequestsAndNanosecondsSurviveBsonPrecision() {
        clock.now = NOW.plusNanos(123456); var lease = budget.acquire(); budget.validate(lease);
        clock.now = NOW.minusSeconds(1); failure(budget::acquire, RevenueCatFailure.Kind.RETRY);
        failure(() -> budget.validate(lease), RevenueCatFailure.Kind.RETRY);
        budget.release(lease); assertThat(row().getList("starts", Date.class)).hasSize(1);
    }
    @Test void independentProjectsHaveIndependentBudgets() {
        for (int i = 0; i < 4; i++) budget.acquire();
        assertThat(new RevenueCatProjectBudget(store, "other-project", clock).acquire()).isNotNull();
        failure(budget::acquire, RevenueCatFailure.Kind.RETRY);
    }
    @Test void schemaIsExplicitAndRejectsDriftWithoutRepair() {
        assertThatThrownBy(() -> RevenueCatBudgetSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        RevenueCatBudgetSchema.installUnderMaintenance(db, true);
        db.getCollection(RevenueCatBudgetSchema.COLLECTION).createIndex(new Document("updatedAt", 1));
        assertThatThrownBy(() -> new RevenueCatProjectBudget(store, "fake-project", clock)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RevenueCatBudgetSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        assertThat(db.getCollection(RevenueCatBudgetSchema.COLLECTION).listIndexes().into(new ArrayList<>())).hasSize(2);
    }
    @Test void malformedStoredShapeAndMissingCollectionFailClosed() {
        assertThatThrownBy(() -> db.getCollection(RevenueCatBudgetSchema.COLLECTION).insertOne(new Document("_id", "bad"))).isInstanceOf(RuntimeException.class);
        db.getCollection(RevenueCatBudgetSchema.COLLECTION).drop(); // Isolated test collection only.
        assertThatThrownBy(() -> new RevenueCatProjectBudget(store, "fake-project", clock)).isInstanceOf(IllegalStateException.class);
    }
}
