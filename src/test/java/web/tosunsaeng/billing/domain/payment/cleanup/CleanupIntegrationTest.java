package web.tosunsaeng.billing.domain.payment.cleanup;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class CleanupIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    static final String USER = "11111111-1111-4111-8111-111111111111";
    static final class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    MongoClient mongo; MongoDatabase db; PaymentStore store; PaymentProperties props; MutableClock clock;
    CleanupProcessor processor;
    Local local = Local.UNKNOWN;
    Provider remote = Provider.unknown();
    Coverage proof;
    @BeforeEach void setup() {
        mongo = MongoClients.create(MONGO.getReplicaSetUrl());
        store = new PaymentStore(mongo, new SimpleMongoClientDatabaseFactory(mongo, "cleanup_" + UUID.randomUUID().toString().replace("-", "")));
        db = store.database();
        PaymentFoundationInitializer.initialize(db); PaymentFoundationInitializer.initializeWithdrawal(db);
        PaymentRecoverySchema.initializeEmpty(db);
        db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", "control").append("environment", "SANDBOX")
                .append("consumer", "billing").append("schemaVersion", 4L).append("epoch", UUID.randomUUID().toString())
                .append("version", 1L).append("barrier", "ALL_BLOCKED"));
        CleanupSchema.installUnderMaintenance(db, true);
        barrier("OPEN");
        props = new PaymentProperties(); props.setEnvironment("SANDBOX"); props.getRecovery().setFenceRequired(true);
        clock = new MutableClock();
        processor = new CleanupProcessor(store, props, clock, (session, subject) -> local,
                (subject, deadline) -> remote, (subject, observation, now) -> proof);
    }
    @AfterEach void close() {
        if (mongo != null) {
            mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); mongo.close(); // Only the per-test database in disposable Mongo.
        }
    }
    void barrier(String state) {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", state)));
    }
    String seed(boolean account, Instant withdrawal) {
        if (account) new PaymentAccountService(store, props, clock).getOrCreate(USER, clock.now.plusSeconds(60));
        var applier = new PaymentWithdrawalApplier(store, props, clock);
        store.transaction(s -> applier.applyFact(s, new WithdrawalFact(USER, withdrawal)));
        return job().getString("_id");
    }
    Document job() { return db.getCollection(CleanupSchema.COLLECTION).find().first(); }
    Document cleanup() { return job().get("cleanup", Document.class); }
    void absent() {
        local = Local.NONE; remote = new Provider(Observation.ABSENT_OBSERVED, true, null);
        proof = new Coverage("test-adapter", "test-coverage", "test-drain", clock.now.plusSeconds(7200));
    }
    CleanupPurger purger() {
        return new CleanupPurger(store, props, clock, (s, subject) -> local,
                (subject, recorded, checked, now) -> proof != null
                        && recorded.adapterVersion().equals(proof.adapterVersion())
                        && recorded.coverageVersion().equals(proof.coverageVersion())
                        && recorded.drainPolicyVersion().equals(proof.drainPolicyVersion()) && now.isBefore(proof.validUntil()));
    }
    CleanupProcessor deletingProcessor(CleanupPurger purge) {
        return new CleanupProcessor(store, props, clock, (s, subject) -> local,
                (subject, deadline) -> remote, (subject, observation, now) -> proof, purge);
    }
    String ready(boolean account) {
        String id = seed(account, NOW.minus(Duration.ofDays(15))); absent();
        var p = deletingProcessor(purger());
        var lease = p.claim(id);
        assertThat(p.complete(lease, new Decision(State.READY, null, proof))).isEqualTo(State.READY);
        return id;
    }
    @Test void purgeClaimPreservesProofAndExcludesOtherDeletionAndVerification() {
        String id = ready(true); var original = new Document(cleanup().get("proof", Document.class));
        var purge = purger(); var lease = purge.claim(id);
        assertThat(lease).isNotNull(); assertThat(job().getString("state")).isEqualTo("READY");
        assertThat(cleanup().get("proof")).isEqualTo(original);
        assertThat(purge.claim(id)).isNull(); assertThat(processor.claim(id)).isNull();
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.PURGED);
        for (String collection : List.of("payment_accounts", "purchase_account_refs", "payment_reconcile_cursors",
                "payment_account_lifecycles", CleanupSchema.COLLECTION)) assertThat(db.getCollection(collection).countDocuments()).isZero();
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.ALREADY_ABSENT);
    }
    @Test void immediatePurgeDoesNotWaitOneHourAndPreservesUnrelatedData() {
        String id = seed(true, NOW.minus(Duration.ofDays(15))); absent();
        db.getCollection("test_financial_history").insertOne(new Document("_id", "other-purchase"));
        db.getCollection("test_free_history").insertOne(new Document("_id", "free"));
        assertThat(deletingProcessor(purger()).processResult(id).purge()).isEqualTo(CleanupPurger.Result.PURGED);
        assertThat(clock.now).isEqualTo(NOW); assertThat(job()).isNull();
        assertThat(db.getCollection("test_financial_history").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("test_free_history").countDocuments()).isEqualTo(1);
    }
    @Test void accountlessPurgeAndLateWithdrawalReplayNeverCreateAccount() {
        String id = ready(false); assertThat(purger().tryPurge(id)).isEqualTo(CleanupPurger.Result.PURGED);
        store.transaction(s -> new PaymentWithdrawalApplier(store, props, clock).applyFact(s,
                new WithdrawalFact(USER, NOW.minus(Duration.ofDays(15)))));
        assertThat(job().getString("state")).isEqualTo("WAITING");
        assertThat(db.getCollection("payment_accounts").countDocuments()).isZero();
    }
    @Test void expiredDeletionLeaseCanBeReclaimedButOldWorkerCannotDelete() {
        String id = ready(true); var purge = purger(); var old = purge.claim(id);
        clock.now = NOW.plusSeconds(30); var next = purge.claim(id);
        assertThat(next).isNotNull(); assertThat(next.token()).isNotEqualTo(old.token());
        assertThatThrownBy(() -> purge.purge(old)).isInstanceOf(RuntimeException.class);
        assertThat(purge.purge(next)).isEqualTo(CleanupPurger.Result.PURGED);
    }
    @Test void exactProofExpiryRequiresReconfirmationAndNeverExtendsItForLease() {
        String id = ready(false); var purge = purger();
        clock.now = NOW.plusSeconds(3599); var lease = purge.claim(id);
        assertThat(cleanup().get("proof", Document.class).getDate("validUntil").toInstant()).isEqualTo(NOW.plusSeconds(3600));
        clock.now = NOW.plusSeconds(3600);
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.RECHECK);
        assertThat(job().getString("state")).isEqualTo("WAITING"); assertThat(cleanup()).doesNotContainKeys("proof", "lease");
    }
    @Test void policyChangeAfterClaimRequiresReconfirmation() {
        String id = ready(false); var purge = purger(); var lease = purge.claim(id);
        proof = new Coverage("test-adapter", "new-coverage", "test-drain", NOW.plusSeconds(7200));
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.RECHECK);
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(1);
    }
    @Test void financialEvidenceAtFinalReadIsRetainedAndUnknownBlocked() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id); local = Local.UNKNOWN;
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.BLOCKED);
        assertThat(job().getString("state")).isEqualTo("BLOCKED");
        clock.now = NOW.plus(Duration.ofDays(1)); absent();
        var verify = deletingProcessor(purge); var check = verify.claim(id);
        verify.complete(check, new Decision(State.READY, null, proof));
        lease = purge.claim(id); local = Local.FOUND;
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.BLOCKED);
        assertThat(job().getString("state")).isEqualTo("FINANCIAL_RETAINED");
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
    }
    @Test void financialWriterFirstInvalidatesProofThroughCommonLifecycle() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        fakeFinancialInput();
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.RECHECK);
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("test_mapped_inbox").countDocuments()).isEqualTo(1);
    }
    void fakeFinancialInput() {
        var fence = new PaymentRecoveryFence(store, props); var ticket = fence.capture();
        store.transaction(s -> {
            fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PUSH);
            var lives = db.getCollection("payment_account_lifecycles");
            var life = lives.find(s, new Document("userId", USER).append("environment", "SANDBOX")).first();
            if (life == null) db.getCollection("test_unmapped_inbox").insertOne(s, new Document("_id", "late"));
            else {
                lives.updateOne(s, new Document("_id", life.get("_id")).append("version", life.getLong("version")),
                        new Document("$inc", new Document("version", 1L).append("pendingEvidence", 1L)));
                db.getCollection("test_mapped_inbox").insertOne(s, new Document("_id", "mapped"));
            }
            return null;
        });
    }
    @Test void purgeFirstQuarantinesLateFakeInputAndExpiredUserRequestCannotRecreate() {
        String id = ready(true); assertThat(purger().tryPurge(id)).isEqualTo(CleanupPurger.Result.PURGED);
        fakeFinancialInput();
        assertThat(db.getCollection("test_unmapped_inbox").countDocuments()).isEqualTo(1);
        assertThatThrownBy(() -> new PaymentAccountService(store, props, clock).getOrCreate(USER, NOW))
                .isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("payment_accounts").countDocuments()).isZero();
    }
    @Test void purgeBarriersAndMissingFenceNeverAllowDeletion() {
        String id = ready(false); var purge = purger();
        for (String state : List.of("PURCHASE_BLOCKED", "ALL_BLOCKED")) {
            barrier(state); assertThatThrownBy(() -> purge.claim(id)).isInstanceOf(RuntimeException.class);
        }
        barrier("OPEN"); props.getRecovery().setFenceRequired(false);
        assertThatThrownBy(() -> purge.claim(id)).isInstanceOf(RuntimeException.class);
        assertThat(job()).isNotNull();
    }
    @Test void epochChangeRejectsOldLeaseAndNewWorkerDiscardsOldProof() {
        String id = ready(false); var purge = purger(); var old = purge.claim(id);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set",
                new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> purge.purge(old)).isInstanceOf(RuntimeException.class);
        clock.now = NOW.plusSeconds(30); assertThat(purge.claim(id)).isNull();
        assertThat(job().getString("state")).isEqualTo("WAITING");
    }
    @Test void cursorActivityAndCursorLeasePreventDeletion() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        db.getCollection("payment_reconcile_cursors").updateOne(new Document(), new Document("$set",
                new Document("lastActivityAt", Date.from(NOW.plusMillis(1)))));
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.RECHECK);
        var p = deletingProcessor(purge); var verify = p.claim(id); p.complete(verify, new Decision(State.READY, null, proof));
        var retry = purge.claim(id);
        db.getCollection("payment_reconcile_cursors").updateOne(new Document(), new Document("$set", new Document("lease", new Document("until", Date.from(NOW.plusSeconds(60))))));
        assertThat(purge.purge(retry)).isEqualTo(CleanupPurger.Result.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("PENDING_EVIDENCE");
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
    }
    @Test void totalDocumentLimitIncludesAccountCursorJobAndLifecycle() {
        String id = seed(true, NOW.minus(Duration.ofDays(15))); absent();
        Document account = db.getCollection("payment_accounts").find().first();
        for (int i = 1; i < 97; i++) db.getCollection("purchase_account_refs").insertOne(new Document("_id", UUID.randomUUID().toString())
                .append("accountId", account.get("_id")).append("environment", "SANDBOX").append("active", false));
        var p = deletingProcessor(purger()); var lease = p.claim(id); p.complete(lease, new Decision(State.READY, null, proof));
        assertThat(purger().tryPurge(id)).isEqualTo(CleanupPurger.Result.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("DOCUMENT_LIMIT");
        assertThat(db.getCollection("purchase_account_refs").countDocuments()).isEqualTo(97);
        db.getCollection("purchase_account_refs").deleteOne(new Document("_id", new Document("$ne", account.get("activeRefId"))));
        clock.now = NOW.plus(Duration.ofDays(1)); absent();
        lease = p.claim(id); p.complete(lease, new Decision(State.READY, null, proof));
        assertThat(purger().tryPurge(id)).isEqualTo(CleanupPurger.Result.PURGED);
    }
    @Test void partialAbsenceIsNotSuccessfulDeletion() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        db.getCollection(CleanupSchema.COLLECTION).deleteOne(new Document("_id", id));
        assertThatThrownBy(() -> purge.purge(lease)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
    }
    @Test void deleteFailureRollsBackEarlierDeletesAndLifecycleCas() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        var originalLife = db.getCollection("payment_account_lifecycles").find().first();
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("skip", 1)).append("data", new Document("failCommands", List.of("delete"))
                        .append("errorCode", 2)));
        assertThatThrownBy(() -> purge.purge(lease)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("purchase_account_refs").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("payment_account_lifecycles").find().first()).isEqualTo(originalLife);
        assertThat(job()).isNotNull();
    }
    @Test void unknownCommitRetryDeletesOnceWithoutPersistentReceipt() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 1)).append("data", new Document("failCommands", List.of("commitTransaction"))
                        .append("errorCode", 1).append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.PURGED);
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.ALREADY_ABSENT);
    }
    @Test void proofExpiringInsideTransactionRollsBackAllDeletes() {
        String id = ready(true);
        var purge = new CleanupPurger(store, props, clock, (s, subject) -> { clock.now = NOW.plusSeconds(3600); return Local.NONE; },
                (subject, recorded, checked, now) -> true);
        var lease = purge.claim(id);
        assertThatThrownBy(() -> purge.purge(lease)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("purchase_account_refs").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
    }
    @Test void concurrentDeletionClaimsConvergeOnOneLease() throws Exception {
        String id = ready(false); var purge = purger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<CleanupPurger.Lease> work = () -> { gate.await(); return purge.claim(id); };
            var a = pool.submit(work); var b = pool.submit(work); gate.countDown();
            var leases = Arrays.asList(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertThat(leases.stream().filter(Objects::nonNull).count()).isEqualTo(1);
        }
    }
    @Test void readyCrashIsRecoveredByNextTickBeforeHourlyDiscovery() {
        var metrics = new SimpleMeterRegistry(); var purge = purger();
        var worker = new CleanupWorker(deletingProcessor(purge), new CleanupAlerts(store, "SANDBOX", clock, x -> true, metrics), clock, metrics);
        worker.tick(); String id = ready(true);
        clock.now = NOW.plusSeconds(10); worker.tick();
        assertThat(job()).isNull();
        assertThat(metrics.get("billing_cleanup_purge_result").tag("result", "PURGED").counter().count()).isEqualTo(1);
    }
    @Test void r1RequiresExplicitR2MigrationAndAllowsReadyLeaseOnlyAfterwards() {
        String id = ready(false);
        db.runCommand(new Document("collMod", CleanupSchema.COLLECTION).append("validator", CleanupSchema.manifest("r1").get("validator")));
        assertThatThrownBy(() -> CleanupSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> purger().claim(id)).isInstanceOf(RuntimeException.class);
        barrier("ALL_BLOCKED"); CleanupSchema.installUnderMaintenance(db, true); barrier("OPEN");
        CleanupSchema.verify(db); assertThat(purger().claim(id)).isNotNull();
    }
    @Test void concurrentFinancialWriterWaitsForPurgeThenQuarantinesWithoutUpsert() throws Exception {
        String id = ready(true);
        // Pre-create fake inbox collections: real adapters/schema belong to PLAN-010, not this test core.
        db.createCollection("test_mapped_inbox"); db.createCollection("test_unmapped_inbox");
        var finalRead = new CountDownLatch(1); var release = new CountDownLatch(1);
        var purge = new CleanupPurger(store, props, clock, (s, subject) -> {
            finalRead.countDown();
            try { if (!release.await(1, TimeUnit.SECONDS)) throw new IllegalStateException(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            return Local.NONE;
        }, (subject, recorded, checked, now) -> true);
        var lease = purge.claim(id);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var deleting = pool.submit(() -> purge.purge(lease));
            assertThat(finalRead.await(1, TimeUnit.SECONDS)).isTrue();
            var entered = new CountDownLatch(1);
            var input = pool.submit(() -> { entered.countDown(); fakeFinancialInput(); });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue(); release.countDown();
            assertThat(deleting.get(5, TimeUnit.SECONDS)).isEqualTo(CleanupPurger.Result.PURGED);
            input.get(5, TimeUnit.SECONDS);
        }
        assertThat(db.getCollection("test_unmapped_inbox").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("test_mapped_inbox").countDocuments()).isZero();
        assertThat(db.getCollection("payment_accounts").countDocuments()).isZero();
    }
    @Test void blockAfterClaimAndMissingControlPreventFinalDeletion() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        barrier("PURCHASE_BLOCKED"); assertThatThrownBy(() -> purge.purge(lease)).isInstanceOf(RuntimeException.class);
        barrier("OPEN"); db.getCollection(PaymentRecoveryFence.COLLECTION).deleteOne(new Document());
        assertThatThrownBy(() -> purge.purge(lease)).isInstanceOf(RuntimeException.class);
        assertThat(job()).isNotNull();
    }
    @Test void unknownCommitExhaustionIsNotReportedAsPurged() {
        String id = ready(false); var purge = purger(); var lease = purge.claim(id);
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 3)).append("data", new Document("failCommands", List.of("commitTransaction"))
                        .append("errorCode", 1).append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        assertThatThrownBy(() -> purge.purge(lease)).isInstanceOf(RuntimeException.class);
        // This injected error occurred before commit. It does not prove the result of a real lost response.
        assertThat(job()).isNotNull();
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.PURGED);
    }
    @Test void additionalCursorOutsideDeletionScopeBlocksWithoutDeletingIt() {
        String id = ready(true); var purge = purger(); var lease = purge.claim(id);
        db.getCollection("payment_reconcile_cursors").insertOne(new Document("_id", "other-cursor")
                .append("accountId", lease.subject().accountId()).append("kind", "OTHER").append("version", 1L));
        assertThat(purge.purge(lease)).isEqualTo(CleanupPurger.Result.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("DATA_INCONSISTENT");
        assertThat(db.getCollection("payment_reconcile_cursors").countDocuments()).isEqualTo(2);
    }
    @Test void readyRecoveryAfterExpiredLeaseDoesNotNeedNextHourlyRound() {
        var metrics = new SimpleMeterRegistry(); var purge = purger();
        var worker = new CleanupWorker(deletingProcessor(purge), new CleanupAlerts(store, "SANDBOX", clock, x -> true, metrics), clock, metrics);
        worker.tick(); String id = ready(true); purge.claim(id);
        clock.now = NOW.plusSeconds(29); worker.tick(); assertThat(job()).isNotNull();
        clock.now = NOW.plusSeconds(30); worker.tick(); assertThat(job()).isNull();
    }
    @Test void invalidFinalPolicyCannotBusyRetrySameCandidateWithinTick() {
        String id = ready(false);
        var purge = new CleanupPurger(store, props, clock, (s, subject) -> Local.NONE,
                (subject, recorded, checked, now) -> false);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var p = new CleanupProcessor(store, props, clock, (s, subject) -> Local.NONE,
                (subject, deadline) -> { calls.incrementAndGet(); clock.now = clock.now.plusMillis(1); return remote; },
                (subject, observation, now) -> proof, purge);
        var metrics = new SimpleMeterRegistry();
        var worker = new CleanupWorker(p, new CleanupAlerts(store, "SANDBOX", clock, x -> true, metrics), clock, metrics);
        worker.tick();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(job().getString("state")).isEqualTo("WAITING");
        assertThat(job().getString("_id")).isEqualTo(id);
    }
    @Test void purgeCapabilityDoesNotBlockNonDeletingCleanupDuringPurchaseBarrier() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); absent(); barrier("PURCHASE_BLOCKED");
        assertThat(deletingProcessor(purger()).process(id)).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("RECOVERY_BLOCKED");
    }
    @Test void legacyWaitingAndDefaultUnknownNeverDeleteOrMakeReady() {
        String id = seed(true, NOW.minus(Duration.ofDays(15)));
        Document account = db.getCollection("payment_accounts").find().first();
        Document lifecycle = db.getCollection("payment_account_lifecycles").find().first();
        assertThat(job()).doesNotContainKey("cleanup");
        assertThat(processor.process(id)).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("LOCAL_EVIDENCE_UNAVAILABLE");
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(cleanup().getDate("reviewDueAt").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(db.getCollection("payment_accounts").find().first()).isEqualTo(account);
        assertThat(db.getCollection("payment_account_lifecycles").find().first()).isEqualTo(lifecycle);
        assertThat(db.getCollection("purchase_account_refs").countDocuments()).isEqualTo(1);
    }
    @Test void noAccountWithdrawalStillRequiresEvidenceAndPreservesCanonicalTime() {
        String id = seed(false, NOW.minus(Duration.ofDays(15)).plusNanos(1));
        assertThat(processor.claim(id)).isNull();
        clock.now = NOW.plusMillis(1);
        assertThat(processor.process(id)).isEqualTo(State.BLOCKED);
        assertThat(job().get("accountId")).isNull();
        assertThat(db.getCollection("payment_accounts").countDocuments()).isZero();
    }
    @Test void fakeConfirmedAbsenceCreatesBoundedProofButDoesNotPurge() {
        String id = seed(true, NOW.minus(Duration.ofDays(15))); absent();
        assertThat(processor.process(id)).isEqualTo(State.READY);
        var evidence = cleanup().get("proof", Document.class);
        assertThat(evidence.getDate("validUntil").toInstant()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(evidence).doesNotContainKeys("userId", "references", "raw");
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(db.getCollection("payment_accounts").countDocuments()).isEqualTo(1);
        assertThat(processor.claim(id)).isNull();
    }
    @Test void foundPurchaseLeavesFinancialRetainedRowWithoutDueSchedule() {
        String id = seed(true, NOW.minus(Duration.ofDays(15))); local = Local.FOUND;
        assertThat(processor.process(id)).isEqualTo(State.FINANCIAL_RETAINED);
        assertThat(job()).doesNotContainKey("nextAttemptAt");
        assertThat(cleanup()).doesNotContainKeys("lease", "proof");
        assertThat(processor.due(null, 100)).isEmpty();
    }
    @Test void emptyProviderAndPendingEvidenceCannotMakeReady() {
        String id = seed(true, NOW.minus(Duration.ofDays(15)));
        local = Local.NONE; remote = new Provider(Observation.ABSENT_OBSERVED, true, null);
        assertThat(processor.process(id)).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("COVERAGE_UNPROVEN");
        clock.now = NOW.plus(Duration.ofDays(1)); absent();
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$inc", new Document("version", 1L).append("pendingEvidence", 1L)));
        assertThat(processor.process(id)).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("PENDING_EVIDENCE");
    }
    @Test void reasonsAndDuplicateWithdrawalNeverResetFirstReviewDeadline() {
        Instant withdrawn = NOW.minus(Duration.ofDays(15)); String id = seed(false, withdrawn);
        processor.process(id); var before = cleanup();
        clock.now = NOW.plus(Duration.ofDays(1)); local = Local.NONE;
        processor.process(id);
        store.transaction(s -> new PaymentWithdrawalApplier(store, props, clock).applyFact(s, new WithdrawalFact(USER, withdrawn)));
        assertThat(cleanup().get("firstUnresolvedAt")).isEqualTo(before.get("firstUnresolvedAt"));
        assertThat(cleanup().get("reviewDueAt")).isEqualTo(before.get("reviewDueAt"));
        assertThat(job().getDate("eligibleAt").toInstant()).isEqualTo(NOW);
    }
    @Test void concurrentClaimHasOneWinnerAndExpiredLeaseCannotComplete() throws Exception {
        String id = seed(true, NOW.minus(Duration.ofDays(15)));
        List<CleanupProcessor.Lease> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var task : pool.invokeAll(List.<Callable<CleanupProcessor.Lease>>of(() -> processor.claim(id), () -> processor.claim(id)))) results.add(task.get());
        }
        assertThat(results.stream().filter(Objects::nonNull).count()).isEqualTo(1);
        var old = results.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        clock.now = NOW.plusSeconds(30);
        assertThatThrownBy(() -> processor.complete(old, Decision.blocked(Reason.COVERAGE_UNPROVEN))).isInstanceOf(RuntimeException.class);
        var newer = processor.claim(id); assertThat(newer).isNotNull();
        assertThat(newer.token()).isNotEqualTo(old.token());
        assertThat(processor.complete(newer, Decision.blocked(Reason.COVERAGE_UNPROVEN))).isEqualTo(State.BLOCKED);
    }
    @Test void renewalInvalidatesOldVersionAndMaintainsLease() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); var lease = processor.claim(id);
        clock.now = NOW.plusSeconds(10); var renewed = processor.renew(lease);
        assertThat(renewed.version()).isEqualTo(lease.version()+1);
        assertThatThrownBy(() -> processor.complete(lease, Decision.blocked(Reason.PROVIDER_UNAVAILABLE))).isInstanceOf(RuntimeException.class);
        clock.now = NOW.plusSeconds(31);
        assertThat(processor.complete(renewed, Decision.blocked(Reason.PROVIDER_UNAVAILABLE))).isEqualTo(State.BLOCKED);
    }
    @Test void lifecycleAndCursorChangeInvalidateProof() {
        String id = seed(true, NOW.minus(Duration.ofDays(15))); absent();
        var lease = processor.claim(id);
        db.getCollection("payment_reconcile_cursors").updateOne(new Document(), new Document("$set", new Document("lastActivityAt", Date.from(NOW.plusSeconds(1)))));
        assertThat(processor.complete(lease, new Decision(State.READY, null, proof))).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("VERSION_CHANGED");
        clock.now = NOW.plus(Duration.ofDays(1)); absent(); lease = processor.claim(id);
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$inc", new Document("version", 1L)));
        assertThat(processor.complete(lease, new Decision(State.READY, null, proof))).isEqualTo(State.BLOCKED);
    }
    @Test void epochAndBarriersFenceAllCleanupWrites() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); absent(); var old = processor.claim(id);
        var before = job();
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> processor.complete(old, new Decision(State.READY, null, proof))).isInstanceOf(RuntimeException.class);
        assertThat(job()).isEqualTo(before);
        clock.now = NOW.plusSeconds(30); var current = processor.claim(id);
        barrier("PURCHASE_BLOCKED");
        assertThat(processor.complete(current, new Decision(State.READY, null, proof))).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("RECOVERY_BLOCKED");
        clock.now = NOW.plus(Duration.ofDays(2)); barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> processor.claim(id)).isInstanceOf(RuntimeException.class);
        barrier("OPEN"); props.getRecovery().setFenceRequired(false);
        assertThatThrownBy(() -> processor.claim(id)).isInstanceOf(RuntimeException.class);
    }
    @Test void numericAndSchemaDriftAreRejectedWithoutRepair() {
        String id = seed(false, NOW.minus(Duration.ofDays(15)));
        assertThatThrownBy(() -> db.getCollection(CleanupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("version", 2))))
                .isInstanceOf(RuntimeException.class);
        db.getCollection(CleanupSchema.COLLECTION).updateOne(new Document(), new Document("$set", new Document("version", Long.MAX_VALUE)));
        assertThatThrownBy(() -> processor.claim(id)).isInstanceOf(RuntimeException.class);
        assertThat(job().getString("state")).isEqualTo("WAITING");
        db.getCollection(CleanupSchema.COLLECTION).createIndex(new Document("createdAt", 1), new IndexOptions().name("bad_ttl").expireAfter(0L, TimeUnit.SECONDS));
        assertThatThrownBy(() -> CleanupSchema.verify(db)).isInstanceOf(IllegalStateException.class);
    }
    @Test void schemaMigrationRequiresBlockedIsolatedWritersAndRecognizedValidator() {
        assertThatThrownBy(() -> CleanupSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> CleanupSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        CleanupSchema.installUnderMaintenance(db, true);
        db.runCommand(new Document("collMod", CleanupSchema.COLLECTION).append("validator", new Document("unknown", new Document("$exists", true))));
        assertThatThrownBy(() -> CleanupSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void dailySummaryHasOnlyCountsAndSinkFailureDoesNotChangeJob() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); processor.process(id);
        clock.now = NOW.plus(Duration.ofDays(8));
        var sent = new ArrayList<CleanupAlerts.Summary>(); var metrics = new SimpleMeterRegistry();
        var alerts = new CleanupAlerts(store, "SANDBOX", clock, s -> { sent.add(s); return true; }, metrics);
        alerts.daily(); alerts.daily();
        assertThat(sent).hasSize(1); assertThat(sent.getFirst().overdueCount()).isEqualTo(1);
        assertThat(sent.toString()).doesNotContain(USER, id);
        var before = job();
        var broken = new CleanupAlerts(store, "SANDBOX", clock, s -> { throw new RuntimeException("private-provider-response"); }, metrics);
        assertThatCode(broken::daily).doesNotThrowAnyException(); assertThat(job()).isEqualTo(before);
        assertThat(metrics.get("billing_cleanup_alert_failure").counter().count()).isEqualTo(1);
    }
    @Test void workerRunsOnlyDueJobsAndDoesNotRepeatDailyWorkAtEachTick() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); var metrics = new SimpleMeterRegistry();
        var alerts = new CleanupAlerts(store, "SANDBOX", clock, s -> true, metrics);
        var worker = new CleanupWorker(processor, alerts, clock, metrics);
        worker.tick(); var before = job(); worker.tick();
        assertThat(job()).isEqualTo(before);
        clock.now = NOW.plusSeconds(3600); worker.tick(); assertThat(job()).isEqualTo(before);
        clock.now = NOW.plus(Duration.ofDays(1)); worker.tick(); assertThat(job().getLong("version")).isGreaterThan(before.getLong("version"));
        assertThat(job().getString("_id")).isEqualTo(id);
    }
    @Test void failedResultWriteRollsBackControlAndLeavesLeaseForRecovery() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); var lease = processor.claim(id);
        var before = job(); var control = db.getCollection(PaymentRecoveryFence.COLLECTION).find().first();
        db.runCommand(new Document("collMod", CleanupSchema.COLLECTION).append("validator", new Document("state", "VERIFYING")));
        assertThatThrownBy(() -> processor.complete(lease, Decision.blocked(Reason.PROVIDER_UNAVAILABLE))).isInstanceOf(RuntimeException.class);
        assertThat(job()).isEqualTo(before);
        assertThat(db.getCollection(PaymentRecoveryFence.COLLECTION).find().first()).isEqualTo(control);
    }
    @Test void unknownCommitRetryReturnsOneClaimAndNoDuplicateEffect() {
        String id = seed(false, NOW.minus(Duration.ofDays(15)));
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 1)).append("data", new Document("failCommands", List.of("commitTransaction"))
                        .append("errorCode", 1).append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        var lease = processor.claim(id);
        assertThat(lease).isNotNull(); assertThat(job().getLong("version")).isEqualTo(2L);
        assertThat(processor.claim(id)).isNull();
        assertThat(processor.complete(lease, Decision.blocked(Reason.COVERAGE_UNPROVEN))).isEqualTo(State.BLOCKED);
    }
    @Test void schemaRejectsAlteredReviewDeadlineAndProofRelations() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); processor.process(id);
        assertThatThrownBy(() -> db.getCollection(CleanupSchema.COLLECTION).updateOne(new Document(), new Document("$set",
                new Document("cleanup.reviewDueAt", Date.from(NOW.plus(Duration.ofDays(8))))))).isInstanceOf(RuntimeException.class);
        clock.now = NOW.plus(Duration.ofDays(1)); absent(); processor.process(id);
        assertThatThrownBy(() -> db.getCollection(CleanupSchema.COLLECTION).updateOne(new Document(), new Document("$set",
                new Document("cleanup.proof.validUntil", Date.from(clock.now.plusSeconds(3601)))))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> db.getCollection(CleanupSchema.COLLECTION).updateOne(new Document(), new Document("$set",
                new Document("cleanup.proof.adapterVersion", "v1\n")))).isInstanceOf(RuntimeException.class);
    }
    @Test void workerReclaimsCrashedLeaseBeforeNextHourlyDiscovery() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); processor.claim(id);
        var metrics = new SimpleMeterRegistry();
        var worker = new CleanupWorker(processor, new CleanupAlerts(store, "SANDBOX", clock, s -> true, metrics), clock, metrics);
        worker.tick(); assertThat(job().getString("state")).isEqualTo("VERIFYING");
        clock.now = NOW.plusSeconds(30); worker.tick(); assertThat(job().getString("state")).isEqualTo("BLOCKED");
    }
    @Test void defaultRuntimeWiringCannotUseTestAbsenceProof() {
        seed(false, NOW.minus(Duration.ofDays(15))); absent();
        var worker = new CleanupConfiguration().cleanupWorker(store, props, clock, new SimpleMeterRegistry());
        worker.tick();
        assertThat(cleanup().getString("reason")).isEqualTo("LOCAL_EVIDENCE_UNAVAILABLE");
        assertThat(job().getString("state")).isEqualTo("BLOCKED");
    }
    @Test void providerDeadlineAndExceptionNeverProduceReady() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); absent();
        var slow = new CleanupProcessor(store, props, clock, (s, x) -> Local.NONE,
                (x, deadline) -> { clock.now = deadline; return remote; }, (s, r, now) -> proof);
        assertThat(slow.process(id)).isEqualTo(State.BLOCKED);
        assertThat(cleanup().getString("reason")).isEqualTo("PROVIDER_UNAVAILABLE");
    }
    @Test void legacyValidatorMigrationAcceptsExistingCanonicalUserIdsWithoutNarrowingWire() {
        db.runCommand(new Document("collMod", CleanupSchema.COLLECTION).append("validator", new Document()));
        store.transaction(s -> new PaymentWithdrawalApplier(store, props, clock).applyFact(s,
                new WithdrawalFact("00000000-0000-0000-0000-000000000000", NOW.minus(Duration.ofDays(15)))));
        barrier("ALL_BLOCKED"); CleanupSchema.installUnderMaintenance(db, true);
        barrier("OPEN"); assertThat(processor.process(job().getString("_id"))).isEqualTo(State.BLOCKED);
    }
    @Test void malformedLegacyRowsPreventInstallingValidatorWithoutRewritingRows() {
        String id = seed(false, NOW.minus(Duration.ofDays(15)));
        db.runCommand(new Document("collMod", CleanupSchema.COLLECTION).append("validator", new Document()));
        db.getCollection(CleanupSchema.COLLECTION).updateOne(new Document("_id", id), new Document("$set", new Document("version", 1)));
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> CleanupSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        assertThat(job().get("version")).isInstanceOf(Integer.class);
    }
    @Test void expiredReadyProofIsRecheckedRatherThanReused() {
        String id = seed(false, NOW.minus(Duration.ofDays(15))); absent(); processor.process(id);
        clock.now = NOW.plusSeconds(3600); local = Local.UNKNOWN;
        assertThat(processor.process(id)).isEqualTo(State.BLOCKED);
        assertThat(cleanup()).doesNotContainKey("proof");
    }
}
