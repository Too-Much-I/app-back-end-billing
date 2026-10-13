package web.tosunsaeng.billing.domain.payment;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import static web.tosunsaeng.billing.domain.payment.recovery.RecoveryPayloadDecoder.*;
import static web.tosunsaeng.billing.domain.payment.recovery.WithdrawalSnapshotProcessor.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = false)
class WithdrawalSnapshotIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final String STREAM = "11111111-1111-4111-8111-111111111111";
    static final String SNAPSHOT = "22222222-2222-4222-8222-222222222222";
    static final String OP = "33333333-3333-4333-8333-333333333333";
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    MongoClient mongo;
    MongoDatabase db;
    PaymentStore store;
    PaymentProperties props;
    MutableClock clock;
    IdentityWithdrawalRecoveryClient remote;
    WithdrawalSnapshotProcessor processor;
    static final class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @BeforeEach void setup() {
        mongo = MongoClients.create(MONGO.getReplicaSetUrl());
        store = new PaymentStore(mongo, new SimpleMongoClientDatabaseFactory(mongo, "snap_" + UUID.randomUUID().toString().replace("-", "")));
        db = store.database();
        PaymentFoundationInitializer.initialize(db);
        PaymentFoundationInitializer.initializeWithdrawal(db);
        PaymentRecoverySchema.initializeEmpty(db);
        PaymentSnapshotSchema.initializeEmpty(db);
        props = new PaymentProperties(); props.setEnvironment("SANDBOX");
        props.getRecovery().setFenceRequired(true); props.getRecovery().setSnapshotEnabled(true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", "control")
                .append("environment", "SANDBOX").append("consumer", "billing").append("schemaVersion", 2L)
                .append("epoch", UUID.randomUUID().toString()).append("version", 1L).append("barrier", "PURCHASE_BLOCKED"));
        clock = new MutableClock();
        remote = mock(IdentityWithdrawalRecoveryClient.class);
        processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
    }
    @AfterEach void cleanup() {
        if (mongo != null) {
            mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); mongo.close(); // Only this disposable test database.
        }
    }
    static List<WithdrawalFact> facts(int count) {
        List<WithdrawalFact> facts = new ArrayList<>();
        for (int i=0; i<count; i++) facts.add(new WithdrawalFact(
                String.format("44444444-4444-1444-8444-%012d", i), NOW.minusNanos(i + 1)));
        return facts;
    }
    static String digest(List<WithdrawalFact> facts) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (var fact : facts) {
            byte[] row = ("{\"userId\":\"" + fact.userId() + "\",\"withdrawnAt\":\"" + fact.withdrawnAt() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(row.length).array()); digest.update(row);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    Manifest manifest(List<WithdrawalFact> rows) throws Exception {
        return new Manifest(SNAPSHOT, STREAM, 10, 2, 100, rows.size(), Math.max(1, (rows.size()+19)/20),
                digest(rows), "UNKNOWN", List.of(), NOW.plus(Duration.ofDays(7)));
    }
    void source(List<WithdrawalFact> rows) throws Exception {
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "READY", manifest(rows), null));
        when(remote.snapshotPage(eq(SNAPSHOT), nullable(String.class))).thenAnswer(inv -> {
            String cursor = inv.getArgument(1);
            int from = cursor == null ? 0 : Integer.parseInt(cursor.substring(1));
            int to = Math.min(from + 20, rows.size());
            return new SnapshotPage(rows.subList(from, to), to == rows.size() ? null : "p"+to, to == rows.size());
        });
    }
    Document control() { return db.getCollection(PaymentRecoveryFence.COLLECTION).find().first(); }
    void complete() {
        for (int i=0; i<10 && !"COMPLETE".equals(processor.state()); i++) processor.step();
        assertThat(processor.state()).isEqualTo("COMPLETE");
    }

    @Test void verificationYieldsAndResumesSameLeaseWithoutInstallingPartialDigest() throws Exception {
        source(facts(241)); processor.start(STREAM, OP, true);
        while (!"VERIFYING".equals(processor.state())) processor.step();
        assertThat(processor.step()).isEqualTo("VERIFYING"); // First 200 rows only.
        String lease = control().get("snapshotJob", Document.class).getString("leaseId");
        assertThat(lease).isNotNull();
        assertThat(control()).doesNotContainKey("baseline");
        var anotherWorker = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        assertThat(anotherWorker.step()).isEqualTo("VERIFYING");
        assertThat(control().get("snapshotJob", Document.class).getString("leaseId")).isEqualTo(lease);
        assertThat(processor.step()).isEqualTo("COMPLETE");
        assertThat(control().getLong("checkpoint")).isEqualTo(100);
    }

    @Test void crashedHashRestartsAfterLeaseAndDetectsEarlierRowTampering() throws Exception {
        source(facts(221)); processor.start(STREAM, OP, true);
        while (!"VERIFYING".equals(processor.state())) processor.step();
        processor.step();
        db.getCollection(ROWS).updateOne(new Document("userId", facts(1).getFirst().userId()),
                new Document("$set", new Document("withdrawnAt", NOW.minusSeconds(60).toString())));
        clock.now = NOW.plusSeconds(61);
        processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        assertThat(processor.step()).isEqualTo("VERIFYING");
        assertThatThrownBy(processor::step).isInstanceOf(RecoveryContractException.class);
        assertThat(control()).doesNotContainKey("baseline");
    }

    @Test void oldHashContinuationCannotInstallIntoNewEpoch() throws Exception {
        source(facts(221)); processor.start(STREAM, OP, true);
        while (!"VERIFYING".equals(processor.state())) processor.step();
        processor.step();
        var op = new RecoveryOperator(store, props, clock);
        op.execute(operatorCommand(RecoveryOperator.Action.BLOCK, false), true);
        op.execute(operatorCommand(RecoveryOperator.Action.ROTATE, false), true);
        op.execute(operatorCommand(RecoveryOperator.Action.RECOVER, false), true);
        assertThatThrownBy(processor::step).isInstanceOf(RuntimeException.class);
        assertThat(control()).doesNotContainKeys("baseline", "checkpoint");
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(221);
    }

    @Test void operatorReceiptsDoNotConsumeAutomaticSnapshotRunBudget() throws Exception {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document("_id", "control"),
                new Document("$set", new Document("schemaVersion", 4L)));
        var op = new RecoveryOperator(store, props, clock);
        for (int i=0; i<3; i++) {
            op.execute(operatorCommand(RecoveryOperator.Action.BLOCK, false), true);
            op.execute(operatorCommand(RecoveryOperator.Action.RECOVER, false), true);
        }
        source(List.of()); processor.start(STREAM, OP, true); complete();
        // Metadata remains append-only without being mistaken for six extra remote snapshot creations.
        assertThat(db.getCollection(OPERATIONS).countDocuments(new Document("operator", new Document("$exists", true)))).isEqualTo(6);
        assertThat(db.getCollection(OPERATIONS).countDocuments(new Document("operator", new Document("$exists", false)))).isEqualTo(1);
    }

    @Test void operatorDryRunDuplicateConflictAndInitialBlockedInstallation() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).deleteOne(new Document("_id", "control"));
        var op = new RecoveryOperator(store, props, clock);
        var command = new RecoveryOperator.Command(RecoveryOperator.Action.INITIAL, OP, null, 0,
                UUID.randomUUID().toString(), STREAM, false);
        assertThat(op.execute(command, false)).isEqualTo("DRY_RUN");
        assertThat(control()).isNull();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isZero();
        assertThat(op.execute(command, true)).isEqualTo("APPLIED");
        assertThat(op.execute(command, true)).isEqualTo("ALREADY_APPLIED");
        assertThat(control().getString("barrier")).isEqualTo("ALL_BLOCKED");
        var different = new RecoveryOperator.Command(RecoveryOperator.Action.INITIAL, OP, null, 0,
                UUID.randomUUID().toString(), STREAM, false);
        assertThatThrownBy(() -> op.execute(different, true)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
    }

    @Test void operatorReceiptFailureRollsBackBarrierAndUnknownCommitIsIdempotent() {
        var op = new RecoveryOperator(store, props, clock);
        var command = operatorCommand(RecoveryOperator.Action.BLOCK, false);
        var before = control();
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("insert")).append("errorCode", 121)));
        assertThatThrownBy(() -> op.execute(command, true)).isInstanceOf(RuntimeException.class);
        assertThat(control()).isEqualTo(before);
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isZero();
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        assertThat(op.execute(command, true)).isEqualTo("APPLIED");
        assertThat(op.execute(command, true)).isEqualTo("ALREADY_APPLIED");
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
    }

    RecoveryOperator.Command operatorCommand(RecoveryOperator.Action action, boolean preserve) {
        Document c = control();
        return new RecoveryOperator.Command(action, UUID.randomUUID().toString(), c.getString("epoch"), c.getLong("version"),
                action == RecoveryOperator.Action.ROTATE ? UUID.randomUUID().toString() : null, STREAM, preserve);
    }
    @Test void restoreRotationFencesOldTicketsAndPreservesOnlyApprovedBaseline() throws Exception {
        source(facts(1)); processor.start(STREAM, OP, true); complete();
        var op = new RecoveryOperator(store, props, clock);
        var old = new PaymentRecoveryFence(store, props).capture();
        assertThatThrownBy(() -> op.execute(operatorCommand(RecoveryOperator.Action.ROTATE, true), true)).isInstanceOf(RuntimeException.class);
        op.execute(operatorCommand(RecoveryOperator.Action.BLOCK, false), true);
        var rotation = operatorCommand(RecoveryOperator.Action.ROTATE, true);
        op.execute(rotation, true);
        assertThat(control().getLong("checkpoint")).isEqualTo(100);
        assertThat(control()).doesNotContainKeys("snapshotJob", "feedJob");
        assertThat(control().getString("barrier")).isEqualTo("ALL_BLOCKED");
        op.execute(operatorCommand(RecoveryOperator.Action.RECOVER, false), true);
        assertThatThrownBy(() -> store.transaction(s -> { new PaymentRecoveryFence(store, props)
                .acquire(s, old, PaymentRecoveryFence.Writer.PUSH); return null; })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> op.execute(operatorCommand(RecoveryOperator.Action.OPEN, false), true)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(1);
    }

    @Test void openRequiresSameEpochVerifiedFeedAndKeepsHistoricalUncertainty() throws Exception {
        source(facts(1)); processor.start(STREAM, OP, true); complete();
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document("_id", "control"),
                new Document("$set", new Document("schemaVersion", 4L)));
        props.getRecovery().setFeedEnabled(true);
        when(remote.feedPage(STREAM, 100, null, null)).thenReturn(new FeedPage(List.of(), "opaque",
                100, 100, 100, true, new Availability(STREAM, 0, 100, 100, 100, NOW)));
        new WithdrawalFeedProcessor(store, props, clock, remote, new RecoveryRetryPolicy(() -> .5)).tick();
        var operator = new RecoveryOperator(store, props, clock);
        assertThat(operator.execute(operatorCommand(RecoveryOperator.Action.OPEN, false), true)).isEqualTo("APPLIED");
        assertThat(control().getString("barrier")).isEqualTo("OPEN");
        assertThat(control().getBoolean("historicalUncertainty")).isTrue();
    }

    @Test void concurrentOperatorsCannotInstallTwoEpochsAndResnapshotDoesNotEraseFacts() throws Exception {
        source(facts(1)); processor.start(STREAM, OP, true); complete();
        var op = new RecoveryOperator(store, props, clock);
        op.execute(operatorCommand(RecoveryOperator.Action.BLOCK, false), true);
        var a = operatorCommand(RecoveryOperator.Action.ROTATE, false);
        var b = operatorCommand(RecoveryOperator.Action.ROTATE, false);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var c : List.of(a,b)) results.add(pool.submit(() -> {
                gate.await(); try { op.execute(c, true); return true; } catch (RuntimeException refused) { return false; }
            }));
            gate.countDown();
            int wins = 0; for (var result : results) if (result.get()) wins++;
            assertThat(wins).isEqualTo(1);
        }
        assertThat(control()).doesNotContainKeys("baseline", "checkpoint", "snapshotJob", "feedJob");
        assertThat(control().getBoolean("historicalUncertainty")).isTrue();
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(1);
    }

    @Test void schemaMigrationRecognizesOnlyPreviousValidatorAndIsResumable() {
        var op = new RecoveryOperator(store, props, clock);
        op.execute(operatorCommand(RecoveryOperator.Action.BLOCK, false), true);
        var meta = db.listCollections().filter(new Document("name", OPERATIONS)).first();
        var old = meta.get("options", Document.class).get("validator", Document.class);
        old.get("$jsonSchema", Document.class).get("properties", Document.class).remove("operator");
        db.runCommand(new Document("collMod", OPERATIONS).append("validator", old));
        assertThatThrownBy(() -> PaymentSnapshotSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        RecoveryOperatorCli.migrateSchema(store); RecoveryOperatorCli.migrateSchema(store);
        PaymentSnapshotSchema.verify(db);
        old.get("$jsonSchema", Document.class).put("additionalProperties", true);
        db.runCommand(new Document("collMod", OPERATIONS).append("validator", old));
        assertThatThrownBy(() -> RecoveryOperatorCli.migrateSchema(store)).isInstanceOf(IllegalStateException.class);
    }

    @Test void multiPageRestartInstallsOnlyVerifiedBaselineWithoutInventingInbox() throws Exception {
        source(facts(27)); processor.start(STREAM, OP, true);
        processor.start(STREAM, OP, true); // Same operation is a no-op, not a new run.
        assertThat(processor.step()).isEqualTo("APPLYING");
        processor.step();
        assertThat(db.getCollection(ROWS).countDocuments()).isEqualTo(20);
        assertThat(control()).doesNotContainKey("baseline");
        processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        assertThat(processor.step()).isEqualTo("VERIFYING");
        assertThat(control()).doesNotContainKey("checkpoint");
        assertThat(processor.step()).isEqualTo("COMPLETE");
        assertThat(control().getLong("checkpoint")).isEqualTo(100);
        assertThat(control().getBoolean("historicalUncertainty")).isTrue();
        assertThat(control().getString("barrier")).isEqualTo("PURCHASE_BLOCKED");
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(27);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isZero();
        assertThat(db.getCollection(PAGES).countDocuments()).isEqualTo(2);
        assertThat(processor.step()).isEqualTo("COMPLETE");
        verify(remote, times(1)).createSnapshot(STREAM, OP);
    }
    @Test void emptySnapshotHasOnePageAndEmptyHash() throws Exception {
        source(List.of()); processor.start(STREAM, OP, false); complete();
        assertThat(db.getCollection(ROWS).countDocuments()).isZero();
        assertThat(db.getCollection(PAGES).countDocuments()).isEqualTo(1);
    }
    @Test void networkResponseLossReusesDurableOperationAcrossRestart() throws Exception {
        source(facts(1));
        when(remote.createSnapshot(STREAM, OP)).thenThrow(new IllegalStateException("synthetic network"))
                .thenReturn(new SnapshotStatus(SNAPSHOT, "READY", manifest(facts(1)), null));
        processor.start(STREAM, OP, true);
        assertThatThrownBy(processor::step).isInstanceOf(IllegalStateException.class);
        processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        complete();
        verify(remote, times(2)).createSnapshot(STREAM, OP);
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
    }
    @Test void buildingPollUsesSameSnapshotAndOperationConflictIsRejected() throws Exception {
        source(facts(1));
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "BUILDING", null, null));
        when(remote.snapshotStatus(SNAPSHOT, STREAM)).thenReturn(new SnapshotStatus(SNAPSHOT, "READY", manifest(facts(1)), null));
        processor.start(STREAM, OP, false);
        assertThat(processor.step()).isEqualTo("POLLING");
        assertThatThrownBy(() -> processor.start(UUID.randomUUID().toString(), OP, false)).isInstanceOf(RuntimeException.class);
        complete();
        verify(remote, times(1)).snapshotStatus(SNAPSHOT, STREAM);
    }
    @Test void wrongDigestLeavesFactsWithdrawnButNeverInstallsBaseline() throws Exception {
        source(facts(1)); Manifest m = manifest(facts(1));
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "READY",
                new Manifest(m.snapshotId(),m.streamId(),m.seconds(),m.increment(),m.highWater(),m.itemCount(),
                        m.pageCount(),"0".repeat(64),m.legacyCoverage(),m.knownGaps(),m.expiresAt()),null));
        processor.start(STREAM, OP, true); processor.step(); processor.step();
        assertThatThrownBy(processor::step).isInstanceOf(RecoveryContractException.class);
        assertThat(processor.state()).isEqualTo("FAILED");
        assertThat(control()).doesNotContainKeys("baseline", "checkpoint");
        assertThat(db.getCollection("payment_account_lifecycles").find().first().getString("state")).isEqualTo("WITHDRAWN");
    }
    @Test void crossPageDuplicateRollsBackEntireSecondPage() throws Exception {
        var rows = facts(21); source(rows);
        when(remote.snapshotPage(SNAPSHOT, "p20")).thenReturn(new SnapshotPage(List.of(rows.getFirst()), null, true));
        processor.start(STREAM, OP, false); processor.step(); processor.step();
        assertThatThrownBy(processor::step).isInstanceOf(RecoveryContractException.class);
        assertThat(db.getCollection(ROWS).countDocuments()).isEqualTo(20);
        assertThat(db.getCollection(PAGES).countDocuments()).isEqualTo(1);
        assertThat(control()).doesNotContainKey("baseline");
    }
    @Test void pageReceiptFailureRollsBackFactsRowsAndProgressThenRetryWorks() throws Exception {
        source(facts(2)); processor.start(STREAM, OP, false); processor.step();
        db.runCommand(new Document("collMod", PAGES).append("validator", new Document("impossible", new Document("$exists", true))));
        assertThatThrownBy(processor::step).isInstanceOf(RuntimeException.class);
        for (String collection : List.of(ROWS, PAGES, "payment_account_lifecycles", "payment_account_cleanup_jobs"))
            assertThat(db.getCollection(collection).countDocuments()).isZero();
        assertThat(db.getCollection(RUNS).find().first().getLong("pagesApplied")).isZero();
        // Restore exact validator only inside the disposable test fixture.
        var schema = Document.parse(new String(getClass().getResourceAsStream("/payment-recovery-snapshot-schema.json").readAllBytes(),
                StandardCharsets.UTF_8)).get("validators", Document.class).get(PAGES, Document.class);
        db.runCommand(new Document("collMod", PAGES).append("validator", schema));
        complete();
    }
    @Test void expiryDuringHttpRejectsLatePageAndNextStepRecordsExpired() throws Exception {
        source(facts(1)); processor.start(STREAM, OP, false); processor.step();
        when(remote.snapshotPage(SNAPSHOT, null)).thenAnswer(inv -> {
            clock.now = NOW.plus(Duration.ofDays(7));
            return new SnapshotPage(facts(1), null, true);
        });
        assertThatThrownBy(processor::step).isInstanceOf(RuntimeException.class);
        assertThat(processor.step()).isEqualTo("EXPIRED");
        assertThat(db.getCollection(ROWS).countDocuments()).isZero();
        assertThat(control()).doesNotContainKey("checkpoint");
    }
    @Test void epochChangedDuringHttpRejectsOldResponse() throws Exception {
        var m = manifest(facts(1));
        when(remote.createSnapshot(STREAM, OP)).thenAnswer(inv -> {
            db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),
                    new Document("$set", new Document("epoch", UUID.randomUUID().toString()))
                            .append("$inc", new Document("version", 1L)));
            return new SnapshotStatus(SNAPSHOT, "READY", m, null);
        });
        processor.start(STREAM, OP, false);
        assertThatThrownBy(processor::step).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(RUNS).countDocuments()).isZero();
    }
    @Test void expiredLeaseCanBeTakenOverButLateResponseCannotDoubleApply() throws Exception {
        source(facts(1)); processor.start(STREAM, OP, false); processor.step();
        var second = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(remote.snapshotPage(SNAPSHOT, null)).thenAnswer(inv -> {
            if (first.getAndSet(false)) { clock.now = NOW.plusSeconds(61); second.step(); }
            return new SnapshotPage(facts(1), null, true);
        });
        assertThatThrownBy(processor::step).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(PAGES).countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(ROWS).countDocuments()).isEqualTo(1);
        assertThat(processor.step()).isEqualTo("COMPLETE");
    }
    @Test void expiredStagingIsPurgedInBatchesWithExecutionOffAndBaselinePreserved() throws Exception {
        source(facts(45)); processor.start(STREAM, OP, false); complete();
        Document baseline = control().get("baseline", Document.class);
        clock.now = NOW.plus(Duration.ofDays(7)); props.getRecovery().setSnapshotEnabled(false);
        assertThat(processor.purgeExpiredBatch()).isEqualTo(23); // 20 rows, 3 page receipts.
        assertThat(db.getCollection(ROWS).countDocuments()).isEqualTo(25);
        assertThat(processor.purgeExpiredBatch()).isEqualTo(20);
        assertThat(processor.purgeExpiredBatch()).isEqualTo(6); // 5 rows + run metadata/digest.
        assertThat(control().get("baseline", Document.class)).isEqualTo(baseline);
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(45);
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
    }
    @Test void unknownCommitAndTransientWriteConvergeWithoutDuplicateRows() throws Exception {
        source(facts(1)); processor.start(STREAM, OP, false); processor.step();
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("insert")).append("errorCode", 112)
                        .append("errorLabels", List.of("TransientTransactionError"))));
        processor.step();
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        processor.step();
        assertThat(processor.state()).isEqualTo("COMPLETE");
        assertThat(db.getCollection(ROWS).countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(PAGES).countDocuments()).isEqualTo(1);
    }
    @Test void oldControlVersionCannotStartSnapshotAndSchemaDriftIsNotRepaired() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("schemaVersion", 1L)));
        assertThatThrownBy(() -> processor.start(STREAM, OP, false)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isZero();
        PaymentSnapshotSchema.verify(db);
        db.runCommand(new Document("collMod", ROWS).append("validationLevel", "moderate"));
        assertThatThrownBy(() -> PaymentSnapshotSchema.verify(db)).isInstanceOf(IllegalStateException.class);
    }
    @Test void earlierSourceExpiryWinsAndCannotInstallBaseline() throws Exception {
        source(facts(1)); Manifest m = manifest(facts(1));
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "READY",
                new Manifest(m.snapshotId(), m.streamId(), m.seconds(), m.increment(), m.highWater(), m.itemCount(),
                        m.pageCount(), m.contentDigest(), m.legacyCoverage(), m.knownGaps(), NOW.plusSeconds(90)), null));
        processor.start(STREAM, OP, false); processor.step(); processor.step();
        clock.now = NOW.plusSeconds(90);
        assertThat(processor.step()).isEqualTo("EXPIRED");
        assertThat(control()).doesNotContainKey("baseline");
        processor.purgeExpiredBatch();
        assertThat(db.getCollection(ROWS).countDocuments()).isZero();
    }
    @Test void verifiedSnapshotCannotRewindExistingCheckpoint() throws Exception {
        source(facts(1));
        Document baseline = new Document("streamId", STREAM).append("tSeconds", 10L).append("tIncrement", 1L)
                .append("highWater", 90L).append("installedAt", Date.from(NOW));
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),
                new Document("$set", new Document("baseline", baseline).append("checkpoint", 101L)));
        processor.start(STREAM, OP, false); processor.step(); processor.step();
        assertThatThrownBy(processor::step).isInstanceOf(RecoveryContractException.class);
        assertThat(control().getLong("checkpoint")).isEqualTo(101);
        assertThat(control().get("baseline")).isEqualTo(baseline);
        assertThat(processor.state()).isEqualTo("FAILED");
    }
    void automatic() {
        props.getRecovery().setSnapshotWorkerEnabled(true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("schemaVersion", 4L)));
    }
    Document job() { return control().get("snapshotJob", Document.class); }
    void due() { clock.now = job().getDate("nextAttemptAt").toInstant(); }
    @Test void automaticBuildingUsesFiveSecondPollAndDoesNotRecreateAtFiveMinutes() {
        automatic();
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "BUILDING", null, null));
        when(remote.snapshotStatus(SNAPSHOT, STREAM)).thenReturn(new SnapshotStatus(SNAPSHOT, "BUILDING", null, null));
        processor.start(STREAM, OP, false); processor.tick();
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(5));
        processor.tick(); verify(remote, never()).snapshotStatus(anyString(), anyString());
        clock.now = NOW.plusSeconds(300); processor.tick();
        assertThat(job().getBoolean("buildOverdue")).isTrue();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
        assertThat(job().getLong("failures")).isZero();
    }
    @Test void automaticTimeoutRetriesTwiceThenStopsEvenAfterRollingWindow() {
        automatic();
        when(remote.createSnapshot(eq(STREAM), anyString())).thenAnswer(inv -> new SnapshotStatus(
                UUID.randomUUID().toString(), "FAILED", null, "SNAPSHOT_BUILD_TIMEOUT"));
        processor.start(STREAM, OP, false); processor.tick();
        assertThat(processor.state()).isEqualTo("FAILED");
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(60));
        processor.tick(); assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
        due(); processor.tick();
        assertThat(job().getLong("runNumber")).isEqualTo(2);
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(clock.now.plusSeconds(300));
        due(); processor.tick(); processor.tick();
        assertThat(job().getBoolean("blocked")).isTrue();
        assertThat(job().getString("failureCode")).isEqualTo("RUN_BUDGET_EXHAUSTED");
        clock.now = NOW.plus(Duration.ofDays(2)); processor.tick();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(3);
        verify(remote, times(3)).createSnapshot(eq(STREAM), anyString());
    }
    @Test void successfulBuildingRetryAfterSurvivesProcessorRestart() {
        automatic();
        Instant retryAt = NOW.plusSeconds(90000);
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "BUILDING", null, null, retryAt));
        processor.start(STREAM, OP, false); processor.tick();
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(retryAt);
        processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        clock.now = retryAt.minusSeconds(1); processor.tick();
        verify(remote, never()).snapshotStatus(anyString(), anyString());
        when(remote.snapshotStatus(SNAPSHOT, STREAM)).thenReturn(new SnapshotStatus(SNAPSHOT, "BUILDING", null, null));
        clock.now = retryAt; processor.tick();
        verify(remote).snapshotStatus(SNAPSHOT, STREAM);
    }
    @Test void httpEnabledManualStepAlsoHonorsDurableWaitAndRequiresNewSchema() {
        props.getRecovery().setHttpEnabled(true);
        assertThatThrownBy(() -> new PaymentSnapshotSchema(store, props).run(null)).isInstanceOf(IllegalStateException.class);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("schemaVersion", 4L)));
        new PaymentSnapshotSchema(store, props).run(null);
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "BUILDING", null, null, NOW.plusSeconds(90)));
        processor.start(STREAM, OP, false); processor.step(); processor.step();
        verify(remote, never()).snapshotStatus(anyString(), anyString());
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(90));
        assertThat(job().getLong("attempts")).isEqualTo(1);
    }
    @Test void automaticUnknownFailureAndLocalExpiryDoNotCreateNewRun() {
        automatic();
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "FAILED", null, "SNAPSHOT_BUILD_FAILED"));
        processor.start(STREAM, OP, false); processor.tick();
        clock.now = NOW.plusSeconds(600); processor.tick();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
        assertThat(job().getString("failureCode")).isEqualTo("SNAPSHOT_BUILD_FAILED");
    }
    @Test void automaticAuthStopsAndLongWaitSurvivesRestart() {
        automatic();
        when(remote.createSnapshot(STREAM, OP)).thenThrow(RecoveryRemoteException.transientFailure(NOW.plusSeconds(172800)));
        processor.start(STREAM, OP, false); processor.tick();
        processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        clock.now = NOW.plusSeconds(1000); processor.tick();
        verify(remote, times(1)).createSnapshot(STREAM, OP);
        clock.now = NOW.plusSeconds(172800);
        doThrow(RecoveryRemoteException.auth()).when(remote).createSnapshot(STREAM, OP);
        processor.tick(); processor.tick();
        assertThat(job().getBoolean("blocked")).isTrue();
        assertThat(job().getString("failureCode")).isEqualTo("AUTH");
        assertThat(job().getLong("attempts")).isEqualTo(2);
    }
    @Test void automaticTwentyFailuresPersistAndSuccessfulResponseResets() throws Exception {
        automatic();
        when(remote.createSnapshot(STREAM, OP)).thenThrow(RecoveryRemoteException.transientFailure(null));
        processor.start(STREAM, OP, false);
        for (int i=1; i<=20; i++) {
            processor.tick(); assertThat(job().getLong("failures")).isEqualTo(i);
            processor = new WithdrawalSnapshotProcessor(store, props, clock, remote);
            if (i < 20) due();
        }
        assertThat(Duration.between(clock.now, job().getDate("nextAttemptAt").toInstant()).toSeconds()).isBetween(240L, 360L);
        doReturn(new SnapshotStatus(SNAPSHOT, "READY", manifest(List.of()), null)).when(remote).createSnapshot(STREAM, OP);
        due(); processor.tick();
        assertThat(job().getLong("failures")).isZero();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
    }
    @Test void retentionWorksWithExecutionOffAndDeletesOnlyRecoveryMaterial() throws Exception {
        automatic(); source(facts(2)); processor.start(STREAM, OP, false); complete();
        props.getRecovery().setSnapshotEnabled(false);
        props.getRecovery().setSnapshotWorkerEnabled(false);
        var retention = new web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryRetention(store, props, clock);
        clock.now = NOW.plus(Duration.ofDays(7)); retention.batch();
        assertThat(db.getCollection(ROWS).countDocuments()).isZero();
        assertThat(db.getCollection(RUNS).countDocuments()).isZero();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(1);
        clock.now = NOW.plus(Duration.ofDays(120)); retention.batch();
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isZero();
        assertThat(control()).doesNotContainKey("snapshotJob");
        assertThat(control().getLong("checkpoint")).isEqualTo(100);
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(2);
    }
    @Test void monitorSuppressesRepeatedSignalsAndEmitsResolutionWithoutIds() {
        automatic();
        when(remote.createSnapshot(STREAM, OP)).thenThrow(RecoveryRemoteException.auth());
        processor.start(STREAM, OP, false); processor.tick();
        var metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var monitor = new PaymentRecoveryMonitor(store, props, clock, metrics);
        monitor.sample(); monitor.sample();
        assertThat(metrics.get("billing.recovery.alert").tags("reason", "AUTH", "severity", "CRITICAL").counter().count()).isEqualTo(1);
        clock.now = NOW.plusSeconds(3600); monitor.sample();
        assertThat(metrics.get("billing.recovery.alert").tags("reason", "AUTH", "severity", "CRITICAL").counter().count()).isEqualTo(2);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),
                new Document("$set", new Document("snapshotJob.blocked", false).append("snapshotJob.failureCode", null)));
        monitor.sample();
        assertThat(metrics.get("billing.recovery.alert").tags("reason", "AUTH", "severity", "RESOLVED").counter().count()).isEqualTo(1);
        assertThat(metrics.getMeters()).allSatisfy(m -> assertThat(m.getId().getTags().toString())
                .doesNotContain(OP, SNAPSHOT, STREAM));
    }
    @Test void sourceExpiredIsRetryableButLocalExpiryDoesNotInventRemoteConfirmation() {
        automatic();
        when(remote.createSnapshot(STREAM, OP)).thenReturn(new SnapshotStatus(SNAPSHOT, "EXPIRED", null, null));
        when(remote.createSnapshot(eq(STREAM), argThat(id -> !OP.equals(id))))
                .thenReturn(new SnapshotStatus(UUID.randomUUID().toString(), "BUILDING", null, null));
        processor.start(STREAM, OP, false); processor.tick(); due(); processor.tick();
        assertThat(job().getLong("runNumber")).isEqualTo(2);
        clock.now = job().getDate("expiresAt").toInstant(); processor.tick(); processor.tick();
        assertThat(job().getString("failureCode")).isEqualTo("LOCAL_EXPIRED");
        assertThat(db.getCollection(OPERATIONS).countDocuments()).isEqualTo(2);
    }
    @Test void automaticStartupRejectsLegacyJobWithoutRetryEvidence() {
        automatic(); processor.start(STREAM, OP, false);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),
                new Document("$unset", new Document("snapshotJob.attempts", "")));
        assertThatThrownBy(() -> new PaymentSnapshotSchema(store, props).run(null)).isInstanceOf(IllegalStateException.class);
    }
}
