package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
class MemberOperationRecoveryIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    static final String USER = "11111111-1111-4111-8111-111111111111", OP = "22222222-2222-4222-8222-222222222222";
    static final String ISSUER = "https://identity.test";
    MongoClient client; MongoDatabase db; PaymentStore payment; PaymentProperties properties;
    PaidExamTransactions tx; MemberOperationRecovery service; PaymentAccountService accounts;
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "member_recovery_" + UUID.randomUUID().toString().replace("-", ""));
        payment = new PaymentStore(client, factory); db = payment.database();
        PaymentFoundationInitializer.initialize(db); PaymentFoundationInitializer.initializeWithdrawal(db);
        PaymentRecoverySchema.initializeEmpty(db);
        db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", UUID.randomUUID().toString())
                .append("environment", "SANDBOX").append("consumer", "billing").append("schemaVersion", 1L)
                .append("epoch", UUID.randomUUID().toString()).append("version", 1L).append("barrier", "OPEN"));
        for (String name : List.of("reservations", "entitlement_grants", "reservation_allocations", "entitlement_ledger", "attempt_groups", "attempt_sessions")) db.createCollection(name);
        PaidExamSchema.installUnderMaintenance(db, true);
        properties = new PaymentProperties(); properties.setEnvironment("SANDBOX"); properties.setMemberEvidenceEnabled(true);
        properties.getRecovery().setFenceRequired(true);
        tx = new PaidExamTransactions(payment, new MongoTemplate(factory));
        accounts = new PaymentAccountService(payment, properties, clock);
        service = service(clock);
        accounts.getOrCreate(USER, NOW.plusSeconds(60)); // Legacy account has no MEMBER proof.
    }
    MemberOperationRecovery service(Clock time) { return new MemberOperationRecovery(tx, payment, properties, "test", ISSUER, time); }
    @AfterEach void close() {
        if (client != null) {
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); client.close(); // Random database inside disposable replica-set only.
        }
    }
    VerifiedMemberEvidence evidence() {
        Jwt jwt = Jwt.withTokenValue("fake-not-to-be-stored").header("alg", "RS256").subject(USER).issuer(ISSUER)
                .claim("account_type", "MEMBER").claim("scope", "billing:purchase").audience(List.of("tosunsaeng-billing"))
                .issuedAt(NOW.minusSeconds(10)).expiresAt(NOW.plusSeconds(60)).build();
        return VerifiedMemberEvidence.fromAuthenticated(jwt, ISSUER, "SANDBOX", Duration.ofSeconds(60), NOW);
    }
    ReserveCommand command(String session) {
        var request = new ReserveRequest(USER, session, "mock");
        return new ReserveCommand(OP, USER, session, "mock", new ReservePayloadHasher().hash(request));
    }
    ReserveCommand command() { return command("session"); }
    Document account() { return db.getCollection("payment_accounts").find(new Document("userId", USER)).first(); }
    Document receipt() { return db.getCollection(PaidExamStore.RECEIPTS).find().first(); }
    Document control() { return db.getCollection(PaymentRecoveryFence.COLLECTION).find().first(); }
    MemberOperationRecovery.Admission admission(AtomicInteger calls) {
        return (context, command) -> {
            calls.incrementAndGet();
            Document account = context.operations().findOne(new org.springframework.data.mongodb.core.query.BasicQuery(new Document("userId", USER).append("environment", "SANDBOX")), Document.class, "payment_accounts");
            var source = new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.SUBSCRIPTION, null, null, null,
                    account.getString("_id"), "purchase", "entitlement", NOW, NOW.minusSeconds(30), NOW.plusSeconds(3600));
            var reservation = Reservation.reserved("reservation", account.getString("paidSubjectRefId"), command.operationId(), command.payloadHash(),
                    Reservation.Kind.INITIAL, "group", command.sessionId(), command.mockExamId(), null, null, NOW, NOW.plusSeconds(300)).withSourceSnapshot(source);
            context.reservations().insert(reservation);
            return Optional.of(reservation.getReservationId());
        };
    }
    void error(Runnable work, String code) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(InternalApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    @Test void pendingCommitsBeforeErrorAndLostResponseIsRecoveredReadOnly() {
        AtomicInteger calls = new AtomicInteger();
        error(() -> service.reserve(command(), admission(calls)), "MEMBER_EVIDENCE_PENDING");
        Document before = receipt(), account = account(), control = control();
        assertThat(before.getString("state")).isEqualTo("WAITING_MEMBER_EVIDENCE");
        assertThat(before.getLong("schemaVersion")).isEqualTo(2L);
        assertThat(before.get("requestSnapshot", Document.class).getString("sessionId")).isEqualTo("session");
        error(() -> service.status(USER, OP), "MEMBER_EVIDENCE_PENDING");
        assertThat(receipt()).isEqualTo(before); assertThat(account()).isEqualTo(account); assertThat(control()).isEqualTo(control);
        for (String collection : List.of("reservations", "entitlement_grants", "reservation_allocations", "entitlement_ledger", "attempt_groups", "attempt_sessions"))
            assertThat(db.getCollection(collection).countDocuments()).as(collection).isZero();
        assertThat(calls).hasValue(0);
    }
    @Test void evidencePreservesReferenceAndStatusWaitsUntilSameKeyReserveResumes() {
        AtomicInteger calls = new AtomicInteger();
        String reference = account().getString("activeRefId");
        error(() -> service.reserve(command(), admission(calls)), "MEMBER_EVIDENCE_PENDING");
        Document pending = receipt();
        assertThat(accounts.getOrCreate(evidence()).purchaseAccountRefId()).isEqualTo(reference);
        Document evidence = account().get("memberEvidence", Document.class);
        assertThat(evidence.getString("tokenIssuedAt")).isEqualTo(NOW.minusSeconds(10).toString());
        assertThat(account().toJson()).doesNotContain("fake-not-to-be-stored", "scope", "signature");
        error(() -> service.status(USER, OP), "MEMBER_EVIDENCE_PENDING");
        assertThat(receipt()).isEqualTo(pending);
        var first = service.reserve(command(), admission(calls));
        assertThat(calls).hasValue(1);
        assertThat(receipt().getDate("waitingSince")).isEqualTo(pending.getDate("waitingSince"));
        accounts.getOrCreate(evidence()); // Natural account version increment is not an owner change.
        assertThat(service.reserve(command(), admission(calls))).isEqualTo(first);
        assertThat(calls).hasValue(1);
        assertThat(service.status(USER, OP).orElseThrow().entitlementSource()).isEqualTo(EntitlementSourceSnapshot.Type.SUBSCRIPTION);
    }
    @Test void successReplayDoesNotRequireFreshEvidenceOrCommandCacheAndDoesNotReselectSource() {
        accounts.getOrCreate(evidence());
        AtomicInteger calls = new AtomicInteger();
        var first = service.reserve(command(), admission(calls));
        db.getCollection("payment_accounts").updateOne(new Document("userId", USER), new Document("$unset", new Document("memberEvidence", "")));
        var later = service(Clock.fixed(NOW.plus(Duration.ofDays(121)), ZoneOffset.UTC));
        assertThat(later.reserve(command(), admission(calls))).isEqualTo(first);
        assertThat(later.status(USER, OP)).isPresent();
        assertThat(calls).hasValue(1);
        assertThat(db.getCollection("idempotency_commands").countDocuments()).isZero();
    }
    @Test void sameKeyDifferentPayloadConflictsWithoutChangingOriginal() {
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "MEMBER_EVIDENCE_PENDING");
        Document before = receipt();
        error(() -> service.reserve(command("other"), admission(new AtomicInteger())), "IDEMPOTENCY_KEY_CONFLICT");
        assertThat(receipt()).isEqualTo(before);
    }
    @Test void terminalFailureCommitsAndReplaysAfterSevenAnd120Days() {
        accounts.getOrCreate(evidence());
        error(() -> service.reserve(command(), (c, r) -> Optional.empty()), "TRANSITION_CONTEXT_CONFLICT");
        Document before = receipt();
        assertThat(before.getDate("detailEligibleAt").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(120)));
        for (int days : List.of(8, 120, 121)) {
            var later = service(Clock.fixed(NOW.plus(Duration.ofDays(days)), ZoneOffset.UTC));
            error(() -> later.status(USER, OP), "RESERVE_REJECTED_BEFORE_CREATION");
            error(() -> later.reserve(command(), (c, r) -> { throw new AssertionError("must replay"); }), "TRANSITION_CONTEXT_CONFLICT");
        }
        error(() -> service.reserve(command("other"), (c, r) -> Optional.empty()), "IDEMPOTENCY_KEY_CONFLICT");
        assertThat(receipt()).isEqualTo(before);
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
    }
    @Test void failedAdmissionRollsBackAllWritesAndKeepsWaitingEvidence() {
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "MEMBER_EVIDENCE_PENDING");
        accounts.getOrCreate(evidence());
        Document before = receipt();
        error(() -> service.reserve(command(), (context, command) -> {
            admission(new AtomicInteger()).reserve(context, command);
            throw new IllegalStateException("private-data-must-not-escape");
        }), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(receipt()).isEqualTo(before); assertThat(db.getCollection("reservations").countDocuments()).isZero();
    }
    @Test void competingSameKeyResumesMakeOneReservation() throws Exception {
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "MEMBER_EVIDENCE_PENDING");
        accounts.getOrCreate(evidence());
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return service.reserve(command(), admission(new AtomicInteger())); });
            var b = pool.submit(() -> { start.await(); return service.reserve(command(), admission(new AtomicInteger())); });
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        assertThat(db.getCollection("reservations").countDocuments()).isOne();
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isOne();
    }
    void withdraw() {
        var fence = new PaymentRecoveryFence(payment, properties); var ticket = fence.capture();
        payment.transaction(session -> {
            fence.acquire(session, ticket, PaymentRecoveryFence.Writer.PUSH);
            return new PaymentWithdrawalApplier(payment, properties, clock).applyFact(session, new WithdrawalFact(USER, NOW));
        });
    }
    @Test void withdrawalWinsOverEvidenceRefreshReplayAndPayloadConflict() {
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "MEMBER_EVIDENCE_PENDING");
        withdraw();
        assertThatThrownBy(() -> accounts.getOrCreate(evidence())).hasMessage("PAYMENT_ACCOUNT_WITHDRAWN");
        error(() -> service.reserve(command("different"), admission(new AtomicInteger())), "OPERATION_OWNER_MISMATCH");
        error(() -> service.status(USER, OP), "OPERATION_OWNER_MISMATCH");
        assertThat(account().getString("state")).isEqualTo("WITHDRAWN");
        assertThat(account().get("memberEvidence")).isNull();
    }
    @Test void withdrawalAndEvidenceRaceNeverResurrectAccount() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); withdraw(); return true; });
            var b = pool.submit(() -> { start.await(); try { accounts.getOrCreate(evidence()); } catch (web.tosunsaeng.billing.domain.payment.exception.PaymentException expected) {
                assertThat(expected.code()).isEqualTo("PAYMENT_ACCOUNT_WITHDRAWN");
            } return true; });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(account().getString("state")).isEqualTo("WITHDRAWN");
        assertThat(db.getCollection("purchase_account_refs").countDocuments(new Document("active", true))).isZero();
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "OPERATION_OWNER_MISMATCH");
    }
    @Test void withdrawalCompetesWithAdmissionThroughSameLifecycleWriteFence() throws Exception {
        accounts.getOrCreate(evidence());
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); withdraw(); return true; });
            var b = pool.submit(() -> {
                start.await();
                try { service.reserve(command(), admission(new AtomicInteger())); }
                catch (InternalApiException denied) { assertThat(denied.code()).isEqualTo("OPERATION_OWNER_MISMATCH"); }
                return true;
            });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(account().getString("state")).isEqualTo("WITHDRAWN");
        assertThat(db.getCollection("reservations").countDocuments()).isLessThanOrEqualTo(1);
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "OPERATION_OWNER_MISMATCH");
        if (receipt() != null) error(() -> service.status(USER, OP), "OPERATION_OWNER_MISMATCH");
    }
    @Test void unknownCommitRetriesCommitWithoutReexecutingAdmission() {
        accounts.getOrCreate(evidence()); AtomicInteger calls = new AtomicInteger();
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        service.reserve(command(), admission(calls));
        assertThat(calls).hasValue(1); assertThat(db.getCollection("reservations").countDocuments()).isOne();
    }
    @Test void contradictionAndMissingSourceFailClosedRatherThanFabricateTerminalOrNotFound() {
        accounts.getOrCreate(evidence()); service.reserve(command(), admission(new AtomicInteger()));
        db.getCollection("reservations").deleteMany(new Document());
        error(() -> service.status(USER, OP), "BILLING_TEMPORARILY_UNAVAILABLE");
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void wrongEvidenceIssuerAndRestoredAccountIdentityDoNotAuthorizeResume() {
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "MEMBER_EVIDENCE_PENDING");
        accounts.getOrCreate(evidence());
        db.getCollection("payment_accounts").updateOne(new Document(), new Document("$set", new Document("memberEvidence.issuer", "https://other.test")));
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "BILLING_TEMPORARILY_UNAVAILABLE");
        db.getCollection(PaidExamStore.RECEIPTS).updateOne(new Document(), new Document("$set", new Document("owner.reference", "other-account")));
        error(() -> service.status(USER, OP), "OPERATION_OWNER_MISMATCH");
    }
    @Test void missingReceiptIsAbsenceAndRecoveryBarrierDoesNotBecomeMemberPending() {
        assertThat(service.status(USER, OP)).isEmpty();
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "BILLING_TEMPORARILY_UNAVAILABLE");
        error(() -> service.status(USER, OP), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(receipt()).isNull();
    }
    @Test void evidenceAndLifecycleFenceRollbackTogetherWhenAccountWriteFails() {
        Document before = account();
        Document lifecycle = db.getCollection("payment_account_lifecycles").find().first();
        db.runCommand(new Document("collMod", "payment_accounts").append("validator", new Document("memberEvidence", new Document("$exists", false))));
        assertThatThrownBy(() -> accounts.getOrCreate(evidence())).isInstanceOf(RuntimeException.class);
        assertThat(account()).isEqualTo(before);
        assertThat(db.getCollection("payment_account_lifecycles").find().first()).isEqualTo(lifecycle);
    }
    @Test void pendingSurvives120DaysAndTerminalEligibilityNeverRoundsEarlier() {
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "MEMBER_EVIDENCE_PENDING");
        var later = service(Clock.fixed(NOW.plus(Duration.ofDays(121)).plusNanos(1), ZoneOffset.UTC));
        error(() -> later.status(USER, OP), "MEMBER_EVIDENCE_PENDING");
        accounts.getOrCreate(evidence());
        error(() -> later.reserve(command(), (c, r) -> Optional.empty()), "TRANSITION_CONTEXT_CONFLICT");
        assertThat(receipt().getDate("detailEligibleAt").toInstant())
                .isEqualTo(NOW.plus(Duration.ofDays(241)).plusMillis(1));
    }
    @Test void lifecycleCanAdvanceAfterReservedWithoutChangingFirstResponse() {
        accounts.getOrCreate(evidence());
        var first = service.reserve(command(), admission(new AtomicInteger()));
        db.getCollection("reservations").updateOne(new Document(), new Document("$set", new Document("status", "EXPIRED")
                .append("terminalAt", Date.from(NOW.plusSeconds(300))).append("activeGuard", false)));
        assertThat(service.reserve(command(), (c, r) -> { throw new AssertionError("no new reserve"); })).isEqualTo(first);
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.EXPIRED);
    }
    @Test void sourceCorruptionIsNotReplayedAsSuccess() {
        accounts.getOrCreate(evidence()); service.reserve(command(), admission(new AtomicInteger()));
        db.getCollection(PaidExamStore.RECEIPTS).updateOne(new Document(), new Document("$set", new Document("source.purchaseId", "other")));
        error(() -> service.status(USER, OP), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void explicitSchemaUpgradeKeepsOldReceiptAndDoesNotInventRequest() {
        db.getCollection(PaidExamStore.RECEIPTS).drop();
        var v1 = PaidExamSchema.foundationValidators().get(PaidExamStore.RECEIPTS);
        db.createCollection(PaidExamStore.RECEIPTS, new com.mongodb.client.model.CreateCollectionOptions()
                .validationOptions(new com.mongodb.client.model.ValidationOptions().validator(v1)
                        .validationAction(com.mongodb.client.model.ValidationAction.ERROR).validationLevel(com.mongodb.client.model.ValidationLevel.STRICT)));
        db.getCollection(PaidExamStore.RECEIPTS).createIndex(new Document("environment", 1).append("callerService", 1).append("userId", 1)
                .append("operationId", 1).append("commandType", 1), new com.mongodb.client.model.IndexOptions().name("ux_reservation_operation_receipt").unique(true));
        Document owner = new Document("kind", "MEMBER_ACCOUNT").append("reference", account().getString("_id")).append("version", 1L).append("epoch", null);
        tx.execute(c -> c.paid().insertWaiting(new PaidExamStore.Key("test", USER, OP, "RESERVE"), command().payloadHash(), owner, NOW));
        Document before = receipt();
        assertThatThrownBy(() -> PaidExamSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaidExamSchema.upgradeRecoveryUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        PaidExamSchema.upgradeRecoveryUnderMaintenance(db, true);
        assertThat(receipt()).isEqualTo(before);
        error(() -> service.reserve(command(), admission(new AtomicInteger())), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
}
