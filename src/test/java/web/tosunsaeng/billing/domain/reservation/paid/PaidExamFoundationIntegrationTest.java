package web.tosunsaeng.billing.domain.reservation.paid;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.*;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class PaidExamFoundationIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    static final String USER = "11111111-1111-4111-8111-111111111111";
    static final String OP = "22222222-2222-4222-8222-222222222222";
    static final String HASH = "a".repeat(64);
    static final List<String> COLLECTIONS = List.of("payment_probe", "reservations", "entitlement_grants", "entitlement_ledger",
            "reservation_allocations", "attempt_groups", "attempt_sessions", PaidExamStore.GUARDS, PaidExamStore.RECEIPTS);
    MongoClient client; MongoDatabase db; MongoTemplate template; SimpleMongoClientDatabaseFactory factory;
    PaymentStore payment; PaidExamTransactions tx;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        factory = new SimpleMongoClientDatabaseFactory(client, "paid_exam_" + UUID.randomUUID().toString().replace("-", ""));
        template = new MongoTemplate(factory); payment = new PaymentStore(client, factory); db = payment.database();
        for (String name : COLLECTIONS) if (!name.equals(PaidExamStore.GUARDS) && !name.equals(PaidExamStore.RECEIPTS)) db.createCollection(name);
        PaidExamSchema.installUnderMaintenance(db, true);
        tx = new PaidExamTransactions(payment, template);
    }
    @AfterEach void close() {
        if (client != null) {
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); client.close(); // Disposable, randomly named Testcontainers database only.
        }
    }
    Document owner() { return new Document("kind", "MEMBER_ACCOUNT").append("reference", "account").append("version", 1L).append("epoch", null); }
    PaidExamStore.Key key() { return new PaidExamStore.Key("test", USER, OP, "RESERVE"); }
    EntitlementSourceSnapshot source() { return new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.EXAM_PASS, "grant", null, null, null, null, null, NOW, null, null); }
    void writes(int failureAt) {
        tx.execute(c -> {
            List<Runnable> writes = List.of(
                    () -> db.getCollection("payment_probe").insertOne(c.session(), new Document("_id", "payment")),
                    () -> c.reservations().insert(Reservation.reserved("reservation", "subject", OP, HASH, Reservation.Kind.INITIAL,
                            "group", "session", "mock", null, null, NOW, NOW.plusSeconds(300))),
                    () -> c.grants().insert(EntitlementGrant.restoredTrial("grant", "test", USER, "consumption", 1, NOW)),
                    () -> c.ledger().insert(EntitlementLedgerEntry.granted("ledger", "trialGrant", "subject", "claim", NOW)),
                    () -> c.allocations().insert(ReservationAllocation.held("allocation", "reservation", "grant", NOW)),
                    () -> c.groups().insert(AttemptGroup.open("group", "subject", "claim", "consumption", "mock", "session", NOW)),
                    () -> c.sessions().insert(AttemptSession.proposed("session", "group", "subject", OP, NOW)),
                    () -> c.paid().createGuard("test", USER, owner()),
                    () -> c.paid().insertWaiting(key(), HASH, owner(), NOW));
            for (int i = 0; i < writes.size(); i++) {
                writes.get(i).run();
                if (failureAt == i + 1) throw new IllegalStateException("injected failure");
            }
            return null;
        });
    }
    @ParameterizedTest @ValueSource(ints = {1,2,3,4,5,6,7,8,9})
    void everyParticipantRollsBackWithRawPaymentWrite(int failureAt) {
        assertThatThrownBy(() -> writes(failureAt)).isInstanceOf(IllegalStateException.class).hasMessage("injected failure");
        for (String name : COLLECTIONS) assertThat(db.getCollection(name).countDocuments()).as(name).isZero();
        writes(0); // Scope is cleared on failure; subsequent work can commit.
        for (String name : COLLECTIONS) assertThat(db.getCollection(name).countDocuments()).as(name).isOne();
    }
    @Test void duplicateConsumptionForDifferentTargetCannotPartiallyCommit() {
        tx.execute(c -> c.grants().insert(EntitlementGrant.restoredTrial("g1", "test", USER, "original", 1, NOW)));
        assertThatThrownBy(() -> tx.execute(c -> {
            db.getCollection("payment_probe").insertOne(c.session(), new Document("_id", "must-rollback"));
            return c.grants().insert(EntitlementGrant.restoredTrial("g2", "test", "33333333-3333-4333-8333-333333333333", "original", 1, NOW));
        })).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("payment_probe").countDocuments()).isZero();
    }
    @Test void fifoOrderUniqueAndNotSharedAcrossOwners() {
        tx.execute(c -> c.grants().insert(EntitlementGrant.restoredTrial("g1", "test", USER, "c1", 1, NOW)));
        assertThatThrownBy(() -> tx.execute(c -> c.grants().insert(EntitlementGrant.restoredTrial("g2", "test", USER, "c2", 1, NOW))))
                .isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
    }
    @Test void oneGuardWinsAndStaleOrWrongOperationCannotRelease() throws Exception {
        tx.execute(c -> c.paid().createGuard("test", USER, owner()));
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String operation = UUID.randomUUID().toString();
                results.add(pool.submit(() -> { start.await(); return tx.execute(c -> c.paid().acquire("test", USER, 1, operation, "r", "g")); }));
            }
            start.countDown();
            int successes = 0; for (var result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isOne();
        }
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        assertThat(tx.<Boolean>execute(c -> c.paid().release("test", USER, 2, OP, "r"))).isFalse();
        assertThat(tx.<Boolean>execute(c -> c.paid().release("test", USER, 1, guard.getString("pendingOperationId"), "r"))).isFalse();
        assertThat(tx.<Boolean>execute(c -> c.paid().release("test", USER, 2, guard.getString("pendingOperationId"), "r"))).isTrue();
        assertThat(tx.<Long>execute(c -> c.paid().nextGrantOrder("test", USER, 3))).isEqualTo(1L);
    }
    @Test void waitingReceiptIsDurableUniqueAndCasCannotOverwriteReservedSnapshot() {
        tx.execute(c -> c.paid().insertWaiting(key(), HASH, owner(), NOW));
        assertThatThrownBy(() -> tx.execute(c -> c.paid().insertWaiting(key(), HASH, owner(), NOW))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> tx.execute(c -> c.paid().receipt(key(), "b".repeat(64)))).hasMessage("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(tx.<Boolean>execute(c -> c.paid().reserveWaiting(key(), HASH, 1, "r", source(), new Document("kind", "INITIAL")))).isTrue();
        assertThat(tx.<Boolean>execute(c -> c.paid().reserveWaiting(key(), HASH, 1, "other", source(), new Document()))).isFalse();
        Document receipt = tx.execute(c -> c.paid().receipt(key(), HASH).orElseThrow());
        assertThat(receipt.getString("reservationId")).isEqualTo("r");
        assertThat(receipt.getDate("waitingSince").toInstant()).isEqualTo(NOW);
        assertThat(db.getCollection("reservations").countDocuments()).isZero(); // This test exercises persistence, not reserve business logic.
    }
    @Test void strictSchemaRejectsPendingTerminalAndMixedSource() {
        tx.execute(c -> c.paid().insertWaiting(key(), HASH, owner(), NOW));
        assertThatThrownBy(() -> db.getCollection(PaidExamStore.RECEIPTS).updateOne(new Document(),
                new Document("$set", new Document("terminalAt", Date.from(NOW))))).isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> db.getCollection(PaidExamStore.RECEIPTS).updateOne(new Document(),
                new Document("$set", new Document("owner.epoch", 1L)))).isInstanceOf(MongoWriteException.class);
        Document mixed = source().toDocument().append("purchaseId", "not-allowed");
        assertThatThrownBy(() -> db.getCollection(PaidExamStore.RECEIPTS).updateOne(new Document(), new Document("$set",
                new Document("state", "RESERVED").append("reservationId", "r").append("source", mixed)
                        .append("responseSnapshot", new Document()).append("errorCode", null)))).isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> db.getCollection(PaidExamStore.RECEIPTS).updateOne(new Document(),
                new Document("$set", new Document("purgeAt", Date.from(NOW))))).isInstanceOf(MongoWriteException.class);
    }
    @Test void nestedAndCrossFactoryTransactionsFailBeforeWrites() {
        assertThatThrownBy(() -> tx.execute(c -> payment.transaction(s -> 1))).hasMessage("Nested transaction boundaries are not allowed");
        var spring = new MongoTransactionExecutor(new MongoTransactionManager(factory));
        assertThatThrownBy(() -> tx.execute(c -> spring.execute(() -> 1))).hasMessage("Nested transaction boundaries are not allowed");
        var springTemplate = new TransactionTemplate(new MongoTransactionManager(factory));
        assertThatThrownBy(() -> springTemplate.execute(status -> tx.execute(c -> 1)))
                .hasMessage("Cannot start an explicit transaction inside a Spring transaction");
        MongoTemplate other = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, db.getName()));
        assertThatThrownBy(() -> payment.transactionWithTemplate(other, (s, m) -> 1)).isInstanceOf(IllegalArgumentException.class);
        PaidExamTransactions.Context escaped = tx.execute(c -> c);
        assertThatThrownBy(escaped::operations).hasMessage("Transaction scope closed");
    }
    @Test void unknownCommitRetriesCommitWithoutReexecutingWork() {
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 1)).append("data", new Document("failCommands", List.of("commitTransaction"))
                        .append("errorCode", 1).append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        AtomicInteger calls = new AtomicInteger();
        tx.execute(c -> { calls.incrementAndGet(); c.paid().insertWaiting(key(), HASH, owner(), NOW); return null; });
        assertThat(calls.get()).isOne();
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isOne();
    }
    @Test void lateCompletionCannotReleaseNewGroupOrPendingCreation() {
        tx.execute(c -> c.paid().createGuard("test", USER, owner()));
        assertThat(tx.<Boolean>execute(c -> c.paid().acquire("test", USER, 1, OP, "r1", "old"))).isTrue();
        assertThat(tx.<Boolean>execute(c -> c.paid().confirmGuard("test", USER, 2, OP, "r1", "old", "s1"))).isTrue();
        String newOp = UUID.randomUUID().toString();
        assertThat(tx.<Boolean>execute(c -> c.paid().acquire("test", USER, 3, newOp, "r2", "new"))).isTrue();
        assertThat(tx.<Boolean>execute(c -> c.paid().completeCurrent("test", USER, 4, "old", "s1"))).isFalse();
        assertThat(tx.<Boolean>execute(c -> c.paid().confirmGuard("test", USER, 4, newOp, "r2", "new", "s2"))).isTrue();
        assertThat(tx.<Boolean>execute(c -> c.paid().completeCurrent("test", USER, 5, "old", "s1"))).isFalse();
        assertThat(tx.<Boolean>execute(c -> c.paid().completeCurrent("test", USER, 5, "new", "s2"))).isTrue();
    }
    @Test void existingEntityReadersRoundTripOptionalSourceWithoutLegacyFallback() {
        var paid = new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.SUBSCRIPTION, null, null, null,
                "account", "purchase", "entitlement", NOW, NOW, NOW.plusSeconds(60));
        tx.execute(c -> {
            c.reservations().insert(Reservation.reserved("r", "subject", OP, HASH, Reservation.Kind.INITIAL,
                    "g", "s", "mock", null, null, NOW, NOW.plusSeconds(300)).withSourceSnapshot(paid));
            c.groups().insert(AttemptGroup.open("g", "subject", "claim", "c", "mock", "s", NOW).withSourceSnapshot(paid));
            c.sessions().insert(AttemptSession.proposed("s", "g", "subject", OP, NOW).withSourceSnapshot(paid));
            return null;
        });
        assertThat(template.findById("r", Reservation.class).getSourceSnapshot()).isEqualTo(paid);
        assertThat(template.findById("g", AttemptGroup.class).getSourceSnapshot()).isEqualTo(paid);
        assertThat(template.findById("s", AttemptSession.class).getSourceSnapshot()).isEqualTo(paid);
        // Legacy readers can still load records with no source; no runtime paid writer is enabled here.
        db.getCollection("reservations").updateOne(new Document("_id", "r"), new Document("$unset", new Document("sourceSnapshot", "")));
        assertThat(template.findById("r", Reservation.class).getSourceSnapshot()).isNull();
    }
    @Test void restorationGrantAndLedgerNeverInventPhoneOrTrialClaim() {
        tx.execute(c -> {
            var grant = EntitlementGrant.restoredTrial("restored", "test", USER, "original-consumption", 1, NOW);
            c.grants().insert(grant);
            c.ledger().insert(EntitlementLedgerEntry.restoredTrial("restored-ledger", grant, NOW));
            return null;
        });
        Document grant = db.getCollection("entitlement_grants").find().first();
        Document ledger = db.getCollection("entitlement_ledger").find().first();
        assertThat(grant.get("subjectRefId")).isNull();
        assertThat(grant.getString("ownerUserId")).isEqualTo(USER);
        assertThat(ledger.get("trialClaimId")).isNull();
        assertThat(ledger.getString("dedupeKey")).isEqualTo("TRIAL_PAID_RESTORATION:test:original-consumption");
    }
    @Test void explicitSchemaVerifierRejectsTtlAndDoesNotRepairValidators() {
        PaidExamSchema.installUnderMaintenance(db, true);
        assertThatThrownBy(() -> PaidExamSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        db.getCollection(PaidExamStore.RECEIPTS).createIndex(new Document("terminalAt", 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
        assertThatThrownBy(() -> PaidExamSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).listIndexes().into(new ArrayList<>())).hasSize(3);
    }
}
