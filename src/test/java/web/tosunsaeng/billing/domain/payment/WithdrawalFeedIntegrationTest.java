package web.tosunsaeng.billing.domain.payment;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.api.PaymentWithdrawalDecoder;
import web.tosunsaeng.billing.domain.payment.application.PaymentWithdrawalApplier;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.domain.PaymentWithdrawal;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import static web.tosunsaeng.billing.domain.payment.recovery.RecoveryPayloadDecoder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = false)
class WithdrawalFeedIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final String STREAM = "11111111-1111-4111-8111-111111111111";
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    MongoClient mongo;
    MongoDatabase db;
    PaymentStore store;
    PaymentProperties props;
    MutableClock clock;
    IdentityWithdrawalRecoveryClient remote;
    WithdrawalFeedProcessor processor;
    static final class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @BeforeEach void setup() {
        mongo = MongoClients.create(MONGO.getReplicaSetUrl());
        store = new PaymentStore(mongo, new SimpleMongoClientDatabaseFactory(mongo, "feed_" + UUID.randomUUID().toString().replace("-", "")));
        db = store.database();
        PaymentFoundationInitializer.initialize(db); PaymentFoundationInitializer.initializeWithdrawal(db);
        PaymentRecoverySchema.initializeEmpty(db); PaymentSnapshotSchema.initializeEmpty(db);
        props = new PaymentProperties(); props.setEnvironment("SANDBOX");
        props.getRecovery().setFenceRequired(true); props.getRecovery().setFeedEnabled(true); props.getRecovery().setSnapshotEnabled(true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", "control")
                .append("environment", "SANDBOX").append("consumer", "billing").append("schemaVersion", 3L)
                .append("epoch", UUID.randomUUID().toString()).append("version", 1L).append("barrier", "OPEN")
                .append("baseline", new Document("streamId", STREAM).append("tSeconds", 10L).append("tIncrement", 2L)
                        .append("highWater", 100L).append("installedAt", Date.from(NOW)))
                .append("checkpoint", 100L));
        clock = new MutableClock(); remote = mock(IdentityWithdrawalRecoveryClient.class); rebuild();
    }
    void rebuild() { processor = new WithdrawalFeedProcessor(store, props, clock, remote, new RecoveryRetryPolicy(() -> 0.5)); }
    @AfterEach void cleanup() {
        if (mongo != null) {
            mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); mongo.close(); // Disposable per-test database only.
        }
    }
    Document control() { return db.getCollection(PaymentRecoveryFence.COLLECTION).find().first(); }
    Document job() { return control().get("feedJob", Document.class); }
    long checkpoint() { return control().getLong("checkpoint"); }
    PaymentWithdrawal event(long seq) {
        String id = String.format("44444444-4444-1444-8444-%012d", seq);
        String body = "{\"eventId\":\""+id+"\",\"schemaVersion\":1,\"userId\":\""+id+"\",\"withdrawnAt\":\"2026-10-11T00:00:00Z\"}";
        return new PaymentWithdrawalDecoder(clock).decode(body.getBytes(StandardCharsets.UTF_8));
    }
    FeedPage page(long after, long target) {
        long end = after + Math.min(20, target - after);
        List<FeedItem> rows = new ArrayList<>();
        for (long i=after+1; i<=end; i++) rows.add(new FeedItem(i, event(i)));
        return new FeedPage(rows, "opaque-cursor", target, end, end, end == target,
                new Availability(STREAM, 0, target, after, end, NOW));
    }
    void source(long target) {
        when(remote.feedPage(eq(STREAM), anyLong(), nullable(String.class), nullable(Long.class)))
                .thenAnswer(inv -> page(inv.getArgument(1), target));
    }
    @Test void appliesFixedTargetAcrossPagesAndRestartsFromCommittedCheckpoint() {
        source(127);
        assertThat(processor.tick()).isEqualTo(2);
        assertThat(checkpoint()).isEqualTo(127);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isEqualTo(27);
        assertThat(job().getString("state")).isEqualTo("CAUGHT_UP");
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(5));
        verify(remote).feedPage(STREAM, 120, "opaque-cursor", 127L);
        rebuild(); assertThat(processor.tick()).isZero();
        clock.now = NOW.plusSeconds(5);
        assertThat(processor.tick()).isEqualTo(1);
        verify(remote).feedPage(STREAM, 127, null, null);
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(clock.now.plusSeconds(30));
    }
    @Test void pushDuplicateAdvancesFeedButDoesNotDuplicateLifecycleOrInbox() {
        var applier = new PaymentWithdrawalApplier(store, props, clock);
        store.transaction(s -> applier.applyEvent(s, event(101)));
        source(101); processor.tick();
        assertThat(checkpoint()).isEqualTo(101);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(1);
    }
    @Test void conflictingEventRollsBackPageAndBlocksWithoutAdvancing() {
        var applier = new PaymentWithdrawalApplier(store, props, clock);
        var original = event(102);
        store.transaction(s -> applier.applyEvent(s, new PaymentWithdrawal(original.eventId(), original.userId(), original.withdrawnAt(), "different")));
        source(102); assertThat(processor.tick()).isZero();
        assertThat(checkpoint()).isEqualTo(100);
        assertThat(job().getString("state")).isEqualTo("BLOCKED_CONTRACT");
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(WithdrawalSnapshotProcessor.PAGES).countDocuments()).isZero();
    }
    @Test void receiptFailureRollsBackInboxAndCheckpointThenRetries() throws Exception {
        source(102);
        db.runCommand(new Document("collMod", WithdrawalSnapshotProcessor.PAGES)
                .append("validator", new Document("impossible", new Document("$exists", true))));
        assertThat(processor.tick()).isZero();
        assertThat(checkpoint()).isEqualTo(100);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isZero();
        assertThat(db.getCollection("payment_account_lifecycles").countDocuments()).isZero();
        try (var in = getClass().getResourceAsStream("/payment-recovery-snapshot-schema.json")) {
            var validator = Document.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .get("validators", Document.class).get(WithdrawalSnapshotProcessor.PAGES, Document.class);
            db.runCommand(new Document("collMod", WithdrawalSnapshotProcessor.PAGES).append("validator", validator));
        }
        clock.now = job().getDate("nextAttemptAt").toInstant(); rebuild();
        assertThat(processor.tick()).isEqualTo(1); assertThat(checkpoint()).isEqualTo(102);
    }
    @Test void invalidSequenceOrChangedTargetCannotCommit() {
        source(125);
        when(remote.feedPage(STREAM, 120, "opaque-cursor", 125L)).thenReturn(page(120, 126));
        assertThat(processor.tick()).isEqualTo(1);
        assertThat(checkpoint()).isEqualTo(120);
        assertThat(job().getString("state")).isEqualTo("BLOCKED_CONTRACT");
    }
    @Test void twentyFailuresPersistAcrossRestartAndProbeRecovers() {
        when(remote.feedPage(eq(STREAM), anyLong(), nullable(String.class), nullable(Long.class)))
                .thenThrow(RecoveryRemoteException.transientFailure(null));
        for (int i=1; i<=20; i++) {
            assertThat(processor.tick()).isZero(); assertThat(job().getLong("failures")).isEqualTo(i);
            clock.now = job().getDate("nextAttemptAt").toInstant(); rebuild();
        }
        assertThat(job().getString("state")).isEqualTo("CIRCUIT");
        // Replace the throwing stub without invoking it during stubbing.
        doReturn(page(100, 101)).when(remote).feedPage(STREAM, 100, null, null);
        assertThat(processor.tick()).isEqualTo(1);
        assertThat(job().getLong("failures")).isZero(); assertThat(job().getLong("attempts")).isEqualTo(21);
        assertThat(checkpoint()).isEqualTo(101);
    }
    @Test void longRetryAfterIsPersistedWithoutEarlyRetry() {
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(RecoveryRemoteException.transientFailure(NOW.plusSeconds(172800)));
        processor.tick(); rebuild(); clock.now = NOW.plusSeconds(86400);
        assertThat(processor.tick()).isZero();
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(172800));
        verify(remote, times(1)).feedPage(STREAM, 100, null, null);
    }
    @Test void sourceLossRequiresSnapshotBut503OnlyRetries() {
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(RecoveryRemoteException.application(
                new RemoteError(ErrorCode.SOURCE_UNAVAILABLE, null), null));
        processor.tick(); assertThat(job().getString("state")).isEqualTo("RETRY");
        assertThat(control()).doesNotContainKey("historicalUncertainty");
        clock.now = job().getDate("nextAttemptAt").toInstant();
        doThrow(RecoveryRemoteException.application(new RemoteError(ErrorCode.REPLAY_UNAVAILABLE,
                new SourceFailure(STREAM, 105, 110, 101, 1L, 105L, NOW)), null))
                .when(remote).feedPage(STREAM, 100, null, null);
        processor.tick(); assertThat(job().getString("state")).isEqualTo("SNAPSHOT_REQUIRED");
        assertThat(control().getBoolean("historicalUncertainty")).isTrue();
        assertThat(control().getString("barrier")).isEqualTo("OPEN"); assertThat(checkpoint()).isEqualTo(100);
        clock.now = NOW.plusSeconds(500); assertThat(processor.tick()).isZero();
    }
    @Test void authStopsAndNonretryable500IsNotTransient() {
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(RecoveryRemoteException.auth());
        processor.tick(); assertThat(job().getString("state")).isEqualTo("BLOCKED_AUTH");
        clock.now = NOW.plusSeconds(500); processor.tick(); verify(remote, times(1)).feedPage(STREAM, 100, null, null);
        // Explicit fixture reset, not a public automatic resume endpoint.
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$unset", new Document("feedJob", "")));
        doThrow(RecoveryRemoteException.application(new RemoteError(ErrorCode.RESPONSE_LIMIT_EXCEEDED, null), null))
                .when(remote).feedPage(STREAM, 100, null, null);
        processor.tick(); assertThat(job().getString("state")).isEqualTo("BLOCKED_CONTRACT");
    }
    @Test void tenPageBudgetYieldsAndCursorExpiryResumesFromC() {
        source(325);
        assertThat(processor.tick()).isEqualTo(10); assertThat(checkpoint()).isEqualTo(300);
        assertThat(processor.tick()).isZero();
        assertThat(job().getDate("nextAttemptAt").toInstant()).isEqualTo(NOW.plusSeconds(1));
        clock.now = NOW.plusSeconds(3600); rebuild();
        assertThat(processor.tick()).isEqualTo(2);
        verify(remote).feedPage(STREAM, 300, null, null);
    }
    @Test void snapshotStartFencesInFlightFeedResponse() {
        when(remote.feedPage(STREAM, 100, null, null)).thenAnswer(inv -> {
            new WithdrawalSnapshotProcessor(store, props, clock, remote).start(STREAM, UUID.randomUUID().toString(), true);
            return page(100, 101);
        });
        assertThatThrownBy(processor::tick).isInstanceOf(RuntimeException.class);
        assertThat(checkpoint()).isEqualTo(100); assertThat(control()).doesNotContainKey("feedJob");
        assertThat(processor.tick()).isZero();
    }
    @Test void crashConsumesAttemptAndLeasePreventsSecondWorkerUntilExpiry() {
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(new AssertionError("synthetic process stop"));
        assertThatThrownBy(processor::tick).isInstanceOf(AssertionError.class);
        assertThat(job().getLong("failures")).isEqualTo(1);
        rebuild(); clock.now = NOW.plusSeconds(59); assertThat(processor.tick()).isZero();
        doReturn(page(100, 100)).when(remote).feedPage(STREAM, 100, null, null);
        clock.now = NOW.plusSeconds(60); assertThat(processor.tick()).isEqualTo(1);
        assertThat(job().getLong("attempts")).isEqualTo(2);
    }
    @Test void transientAndUnknownCommitConvergeAndReceiptsPurgeIndependently() {
        source(101);
        mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        processor.tick(); assertThat(checkpoint()).isEqualTo(101);
        assertThat(db.getCollection(WithdrawalSnapshotProcessor.PAGES).countDocuments()).isEqualTo(1);
        clock.now = NOW.plus(Duration.ofDays(7)); props.getRecovery().setFeedEnabled(false);
        assertThat(processor.purgeExpiredReceipts()).isEqualTo(1);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isEqualTo(1);
        assertThat(checkpoint()).isEqualTo(101);
    }
    @Test void cursorExpiredErrorReopensAtCWithoutSnapshot() {
        source(125);
        when(remote.feedPage(STREAM, 120, "opaque-cursor", 125L)).thenThrow(RecoveryRemoteException.application(
                new RemoteError(ErrorCode.CURSOR_EXPIRED, null), null));
        assertThat(processor.tick()).isEqualTo(1); assertThat(checkpoint()).isEqualTo(120);
        assertThat(job().getString("state")).isEqualTo("READY");
        assertThat(job().getString("cursor")).isNull();
        assertThat(control()).doesNotContainKey("historicalUncertainty");
        clock.now = job().getDate("nextAttemptAt").toInstant();
        assertThat(processor.tick()).isEqualTo(1);
        verify(remote).feedPage(STREAM, 120, null, null);
    }
    @Test void oldEpochLatePageCannotWriteEvenDuplicate() {
        when(remote.feedPage(STREAM, 100, null, null)).thenAnswer(inv -> {
            db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),
                    new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
            return page(100, 101);
        });
        assertThatThrownBy(processor::tick).isInstanceOf(RuntimeException.class);
        assertThat(checkpoint()).isEqualTo(100);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isZero();
    }
    @Test void maxCheckpointAcceptsEmptyScanWithoutOverflow() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),
                new Document("$set", new Document("checkpoint", Long.MAX_VALUE)));
        when(remote.feedPage(STREAM, Long.MAX_VALUE, null, null)).thenReturn(new FeedPage(List.of(), "max-cursor",
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, true,
                new Availability(STREAM, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, NOW)));
        assertThat(processor.tick()).isEqualTo(1);
        assertThat(checkpoint()).isEqualTo(Long.MAX_VALUE);
    }
    @Test void feedApplyRetriesTransientTransactionWithoutDoubleConsumption() {
        when(remote.feedPage(STREAM, 100, null, null)).thenAnswer(inv -> {
            mongo.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                    .append("data", new Document("failCommands", List.of("insert")).append("errorCode", 112)
                            .append("errorLabels", List.of("TransientTransactionError"))));
            return page(100, 101);
        });
        assertThat(processor.tick()).isEqualTo(1);
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection(WithdrawalSnapshotProcessor.PAGES).countDocuments()).isEqualTo(1);
    }
    @Test void sourceLossThenVerifiedSnapshotResumesOnlyAfterNewBaseline() {
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(RecoveryRemoteException.application(
                new RemoteError(ErrorCode.REPLAY_UNAVAILABLE, new SourceFailure(STREAM, 105, 106, 101, 1L, 105L, NOW)), null));
        processor.tick(); assertThat(job().getString("state")).isEqualTo("SNAPSHOT_REQUIRED");
        String snapshotId = UUID.randomUUID().toString(), operation = UUID.randomUUID().toString();
        when(remote.createSnapshot(STREAM, operation)).thenReturn(new SnapshotStatus(snapshotId, "READY",
                new Manifest(snapshotId, STREAM, 20, 1, 105, 0, 1,
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        "UNKNOWN", List.of(), NOW.plus(Duration.ofDays(7))), null));
        when(remote.snapshotPage(snapshotId, null)).thenReturn(new SnapshotPage(List.of(), null, true));
        var snapshot = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        snapshot.start(STREAM, operation, true); snapshot.step(); snapshot.step();
        assertThat(processor.tick()).isZero(); assertThat(checkpoint()).isEqualTo(100);
        snapshot.step(); assertThat(checkpoint()).isEqualTo(105);
        when(remote.feedPage(STREAM, 105, null, null)).thenReturn(page(105, 106));
        assertThat(processor.tick()).isEqualTo(1); assertThat(checkpoint()).isEqualTo(106);
        assertThat(control().get("baseline", Document.class).getLong("highWater")).isEqualTo(105);
        assertThat(control().getBoolean("historicalUncertainty")).isTrue();
        assertThat(db.getCollection("payment_withdrawal_inbox").countDocuments()).isEqualTo(1);
    }
    @Test void automaticSourceRecoveryCompletesButRepeatedGapStops() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("schemaVersion", 4L)));
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(RecoveryRemoteException.application(
                new RemoteError(ErrorCode.REPLAY_UNAVAILABLE, new SourceFailure(STREAM, 105, 106, 101, 1L, 105L, NOW)), null));
        processor.tick();
        String id = UUID.randomUUID().toString();
        when(remote.createSnapshot(eq(STREAM), anyString())).thenReturn(new SnapshotStatus(id, "READY",
                new Manifest(id, STREAM, 20, 1, 105, 0, 1, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        "UNKNOWN", List.of(), NOW.plus(Duration.ofDays(7))), null));
        when(remote.snapshotPage(id, null)).thenReturn(new SnapshotPage(List.of(), null, true));
        var snapshot = new WithdrawalSnapshotProcessor(store, props, clock, remote);
        snapshot.tick(); clock.now = clock.now.plusSeconds(1); snapshot.tick(); clock.now = clock.now.plusSeconds(1); snapshot.tick();
        assertThat(checkpoint()).isEqualTo(105);
        when(remote.feedPage(STREAM, 105, null, null)).thenThrow(RecoveryRemoteException.application(
                new RemoteError(ErrorCode.REPLAY_UNAVAILABLE, new SourceFailure(STREAM, 106, 106, 106, 1L, 106L, NOW)), null));
        processor.tick(); snapshot.tick();
        assertThat(job().getString("state")).isEqualTo("BLOCKED_CONTRACT");
        assertThat(control().get("snapshotJob", Document.class).getString("failureCode")).isEqualTo("REPEATED_GAP");
        snapshot.tick(); verify(remote, times(1)).createSnapshot(eq(STREAM), anyString());
    }
    @Test void retentionClearsExpiredCursorWithoutResettingRetryBudget() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("schemaVersion", 4L)));
        when(remote.feedPage(STREAM, 100, null, null)).thenThrow(RecoveryRemoteException.transientFailure(NOW.plusSeconds(172800)));
        processor.tick();
        Document before = job();
        clock.now = NOW.plusSeconds(3600);
        new PaymentRecoveryRetention(store, props, clock).batch();
        assertThat(job().get("cursorExpiresAt")).isNull();
        assertThat(job().getLong("failures")).isEqualTo(before.getLong("failures"));
        assertThat(job().getDate("nextAttemptAt")).isEqualTo(before.getDate("nextAttemptAt"));
        assertThat(job().getString("runId")).isNotEqualTo(before.getString("runId"));
    }
}
