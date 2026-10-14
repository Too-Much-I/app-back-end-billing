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
class PaidReservationIntegrationTest {
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
        for (String collection : List.of("reservations", "attempt_sessions", "attempt_groups")) {
            String field = collection.equals("attempt_groups") ? "openGuard" : "activeGuard";
            db.getCollection(collection).createIndex(new Document("subjectRefId", 1), new com.mongodb.client.model.IndexOptions()
                    .unique(true).partialFilterExpression(new Document(field, true)));
        }
        db.getCollection("reservations").createIndex(new Document("subjectRefId", 1).append("operationId", 1), new com.mongodb.client.model.IndexOptions().unique(true));
        db.getCollection("entitlement_ledger").createIndex(new Document("dedupeKey", 1), new com.mongodb.client.model.IndexOptions().unique(true));
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
    void error(Runnable work, String code) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(InternalApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    PaidReservationService runtime(Clock time) { return new PaidReservationService(tx, service(time), payment, properties, "test", "scope", time); }
    PaidReservationService runtime() { return runtime(clock); }
    void purchase() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema.installUnderMaintenance(db, true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "OPEN")));
        accounts.getOrCreate(evidence());
        db.getCollection("payment_products").insertOne(new Document("environment", "SANDBOX").append("store", "PLAY_STORE")
                .append("appId", "fake.app").append("storeProductId", "PREMIUM_1D").append("rcProductId", "fake-rc")
                .append("offerCode", "PREMIUM_1D").append("catalogVersion", 1L).append("durationSeconds", 86400L).append("saleEnabled", false));
        var lookup = new web.tosunsaeng.billing.domain.payment.purchase.PurchaseLookupKey(new byte[32], "fake-v1");
        var core = new web.tosunsaeng.billing.domain.payment.purchase.PurchaseCore(payment, properties, clock,
                Map.of(web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version());
        var observation = new web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation("SANDBOX",
                web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Store.PLAY_STORE, "fake.app",
                lookup.transaction("SANDBOX", "PLAY_STORE", "fake.app", "tx"), lookup.version(), lookup.event("SANDBOX", "project", "event"),
                web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Kind.PURCHASE, account().getString("activeRefId"), "PREMIUM_1D", NOW.minusSeconds(10), null);
        core.receive(observation); core.apply(observation.providerEventKey());
    }
    ConfirmCommand confirm(ReserveCommand command, MemberOperationRecovery.Result result) {
        String id = result.responseSnapshot().getString("reservationId");
        var body = new web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest(USER, command.sessionId(), NOW);
        return new ConfirmCommand(command.operationId(), id, USER, command.sessionId(), NOW, new LifecyclePayloadHasher().hashConfirm(id, body));
    }
    @Test void paidReserveConfirmReplayPreservesSourceAndWritesUsageAtomically() {
        purchase(); var command = command(); var runtime = runtime(); var first = runtime.reserve(command);
        assertThat(first.source().getString("type")).isEqualTo("SUBSCRIPTION");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isZero();
        var confirmed = runtime.confirm(confirm(command, first));
        assertThat(confirmed.snapshot().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(runtime.confirm(confirm(command, first)).snapshot()).isEqualTo(confirmed.snapshot());
        assertThat(runtime.reserve(command)).isEqualTo(first);
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("sourceKind", "EXAM_OPERATION"))).isEqualTo(2);
    }
    @Test void revokedBeforeConfirmCommitsRejectionButConfirmedReplaySurvivesLaterRevoke() {
        purchase(); var runtime = runtime(); var first = runtime.reserve(command());
        db.getCollection("purchases").updateOne(new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")));
        error(() -> runtime.confirm(confirm(command(), first)), "ENTITLEMENT_REVOKED");
        error(() -> runtime.confirm(confirm(command(), first)), "ENTITLEMENT_REVOKED");
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CANCELED);
        assertThat(db.getCollection("attempt_groups").countDocuments()).isZero();
    }
    @Test void unknownCommitAndParallelConfirmProduceOneGroup() throws Exception {
        purchase(); var runtime = runtime(); var first = runtime.reserve(command());
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> runtime.confirm(confirm(command(), first)));
            var b = pool.submit(() -> runtime.confirm(confirm(command(), first)));
            assertThat(a.get(10, TimeUnit.SECONDS).snapshot()).isEqualTo(b.get(10, TimeUnit.SECONDS).snapshot());
        }
        assertThat(db.getCollection("attempt_groups").countDocuments()).isOne();
    }
    String oldTrial() {
        String group = UUID.randomUUID().toString();
        db.getCollection("trial_eligibility").insertOne(new Document("consumerScopeId", "scope").append("userId", USER)
                .append("state", "VERIFIED").append("candidates", List.of(new Document("keyVersion", "fake-v1").append("value", "fake-candidate"))));
        tx.execute(c -> {
            c.operations().insert(web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition.freeExamOnce(NOW));
            c.operations().insert(web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim.active("claim", "FREE_EXAM_ONCE", "subject", "fake-event", NOW.minusSeconds(100), NOW.plus(Duration.ofDays(100))));
            c.operations().insert(web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialCandidateAlias.active("alias", "FREE_EXAM_ONCE", "fake-v1", "fake-candidate", "claim", NOW.minusSeconds(100), NOW.plus(Duration.ofDays(100))));
            var grant = web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant.unitGrant("trial-grant", "FREE_EXAM_ONCE", "TRIAL_CLAIM", "claim", "subject", 1, NOW.minusSeconds(100));
            c.grants().insert(grant); var held = c.grants().holdOne("trial-grant", NOW).orElseThrow();
            c.grants().consumeHeldOne("trial-grant", held.getVersion(), NOW).orElseThrow();
            c.ledger().insert(web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementLedgerEntry.consumed("consumption", "trial-grant", 1L, "subject", "claim", "old-reservation", "old-allocation", NOW));
            c.operations().insert(web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink.active("subject", "claim", "scope", USER, NOW.minusSeconds(100), NOW.plus(Duration.ofDays(100))));
            var source = new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.EXAM_PASS, "trial-grant", "consumption", "claim", null, null, null, NOW.minusSeconds(100), null, null);
            c.groups().insert(web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup.open(group, "subject", "claim", "consumption", "mock", "old", NOW).withSourceSnapshot(source));
            var session = web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession.proposed("old", group, "subject", UUID.randomUUID().toString(), NOW).withSessionOwnerEpoch(1L).withSourceSnapshot(source);
            c.sessions().insert(session); c.sessions().activateProposed("old", group, "subject", session.getOperationId(), session.getVersion(), NOW).orElseThrow();
            return true;
        });
        return group;
    }
    ReserveCommand transition(String group) {
        var restart = new RestartContext(group, "old", RestartContext.StartReason.RESTART_INCOMPLETE);
        var request = new ReserveRequest(USER, "session", "mock", null, null, null, restart);
        return new ReserveCommand(OP, USER, "session", "mock", null, null, null, new ReservePayloadHasher().hash(request), restart);
    }
    @Test void trialPaidConfirmRestoresOnceAndLateCompletionKeepsNewGuard() {
        purchase(); String old = oldTrial(); var command = transition(old); var runtime = runtime();
        var reserved = runtime.reserve(command);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getBoolean("replacementBlocked", false)).isFalse();
        runtime.confirm(confirm(command, reserved)); runtime.confirm(confirm(command, reserved));
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isOne();
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        tx.execute(c -> { var group = c.groups().findById(old).orElseThrow();
            c.groups().markCompleted(old, "old", group.getVersion(), NOW, NOW).orElseThrow();
            var session = c.sessions().findBySessionId("old").orElseThrow();
            c.sessions().completeActive("old", old, "subject", session.getVersion(), NOW).orElseThrow();
            return true;
        });
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(runtime.reserve(command)).isEqualTo(reserved);
    }
    @Test void completionBeforeConfirmPreventsRestoration() {
        purchase(); String old = oldTrial(); var command = transition(old); var runtime = runtime(); var reserved = runtime.reserve(command);
        tx.execute(c -> { var group = c.groups().findById(old).orElseThrow(); c.groups().markCompleted(old, "old", group.getVersion(), NOW, NOW).orElseThrow(); return true; });
        runtime.confirm(confirm(command, reserved));
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isZero();
    }
    @Test void cancelAndExpiryNeverTerminateOldTrialOrRestore() {
        purchase(); String old = oldTrial(); var command = transition(old); var runtime = runtime(); var reserved = runtime.reserve(command);
        assertThat(runtime(Clock.fixed(NOW.plusSeconds(300), ZoneOffset.UTC)).expire(USER, OP, reserved.responseSnapshot().getString("reservationId"))).isTrue();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getBoolean("replacementBlocked", false)).isFalse();
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
    }
    @Test void sameSourceRestartIsReplacementAndNewAfterSubmissionIsInitial() {
        purchase(); var runtime = runtime(); var initial = runtime.reserve(command()); runtime.confirm(confirm(command(), initial));
        String group = initial.responseSnapshot().getString("attemptGroupId");
        var restart = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var req = new ReserveRequest(USER, "next", "mock", null, null, null, restart);
        var cmd = new ReserveCommand(UUID.randomUUID().toString(), USER, "next", "mock", null, null, null, new ReservePayloadHasher().hash(req), restart);
        var replacement = runtime.reserve(cmd);
        assertThat(replacement.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        assertThat(replacement.responseSnapshot()).doesNotContainKey("transition");
        runtime.confirm(confirm(cmd, replacement));
        var fresh = new RestartContext(group, "next", RestartContext.StartReason.NEW_AFTER_SUBMISSION);
        var req2 = new ReserveRequest(USER, "third", "next-mock", null, null, null, fresh);
        var cmd2 = new ReserveCommand(UUID.randomUUID().toString(), USER, "third", "next-mock", null, null, null, new ReservePayloadHasher().hash(req2), fresh);
        var result = runtime.reserve(cmd2); assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("INITIAL");
        runtime.confirm(confirm(cmd2, result));
    }

    @Test void phoneTargetPaidStartsInitialAndRestoresTargetWithoutTransition() {
        purchase(); String old = oldTrial(); String continuation = UUID.randomUUID().toString();
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set",
                new Document("ownerTransitionReason", "PHONE_REJOIN").append("ownerTransitionId", continuation).append("sessionOwnerEpoch", 2L)));
        var req = new ReserveRequest(USER, "session", "mock", Reservation.ContinuationReason.PHONE_REJOIN, continuation, old);
        var cmd = new ReserveCommand(OP, USER, "session", "mock", req.continuationReason(), continuation, old, new ReservePayloadHasher().hash(req));
        var runtime = runtime(); var result = runtime.reserve(cmd);
        assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("INITIAL");
        assertThat(result.responseSnapshot().getString("attemptGroupId")).isNotEqualTo(old);
        assertThat(result.responseSnapshot().getString("continuationId")).isEqualTo(continuation);
        assertThat(result.responseSnapshot()).doesNotContainKey("transition");
        runtime.confirm(confirm(cmd, result));
        assertThat(db.getCollection("entitlement_grants").find(new Document("sourceType", "TRIAL_PAID_RESTORATION")).first().getString("ownerUserId")).isEqualTo(USER);
    }
    @Test void oldOwnerEpochChangeBeforeConfirmDeniesAndKeepsPreviousGroup() {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$inc", new Document("sessionOwnerEpoch", 1L)));
        error(() -> runtime().confirm(confirm(cmd, result)), "OPERATION_OWNER_MISMATCH");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getBoolean("replacementBlocked", false)).isFalse();
    }
    @Test void rollbackAtReceiptWriteLeavesNoPartialRestorationOrGroupClosure() {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        db.runCommand(new Document("collMod", PaidExamStore.RECEIPTS).append("validator", new Document("commandType", "RESERVE").append("state", "RESERVED")));
        error(() -> runtime().confirm(confirm(cmd, result)), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").countDocuments()).isOne();
        assertThat(db.getCollection("attempt_groups").find().first().getBoolean("replacementBlocked", false)).isFalse();
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first()).isEqualTo(guard);
        assertThat(db.getCollection("reservations").find().first().getString("status")).isEqualTo("RESERVED");
    }
    @Test void missingContextIsDurableFailureAndDifferentPayloadCannotOverwriteIt() {
        purchase(); oldTrial(); error(() -> runtime().reserve(command()), "TRANSITION_CONTEXT_CONFLICT");
        error(() -> service.status(USER, OP), "RESERVE_REJECTED_BEFORE_CREATION");
        error(() -> runtime().reserve(command("different")), "IDEMPOTENCY_KEY_CONFLICT");
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
    }
    @Test void noRightsPreservesOldTrialAndDoesNotInventTerminalContextFailure() {
        accounts.getOrCreate(evidence()); String old = oldTrial();
        var restart = new RestartContext(old, "old", RestartContext.StartReason.NEW_AFTER_SUBMISSION);
        var req = new ReserveRequest(USER, "session", "mock-next", null, null, null, restart);
        var cmd = new ReserveCommand(OP, USER, "session", "mock-next", null, null, null, new ReservePayloadHasher().hash(req), restart);
        error(() -> runtime().reserve(cmd), "ENTITLEMENT_INSUFFICIENT");
        assertThat(db.getCollection("attempt_groups").find().first().getBoolean("replacementBlocked", false)).isFalse();
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isZero();
    }
    @Test void freeGrantFifoCancelReleaseAndNoRestorationOfRestoredPass() {
        accounts.getOrCreate(evidence());
        tx.execute(c -> {
            c.grants().insert(web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant.restoredTrial("first", "test", USER, "consumed-one", 1L, NOW.minusSeconds(20)));
            c.grants().insert(web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant.restoredTrial("second", "test", USER, "consumed-two", 2L, NOW.minusSeconds(10)));
            return true;
        });
        var runtime = runtime(); var result = runtime.reserve(command()); assertThat(result.source().getString("grantId")).isEqualTo("first");
        var request = new web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest(USER, web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.SESSION_COMMIT_FAILED);
        var cancel = new CancelCommand(OP, result.responseSnapshot().getString("reservationId"), USER, request.reason(), new LifecyclePayloadHasher().hashCancel(result.responseSnapshot().getString("reservationId"), request));
        runtime.cancel(cancel); runtime.cancel(cancel);
        assertThat(db.getCollection("entitlement_grants").find(new Document("_id", "first")).first().getInteger("availableUnits")).isEqualTo(1);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(2);
    }

    @Test void confirmedReplayRemainsSuccessfulAfterRefundAndNormalExpiryDoesNotRejectHeldReservation() {
        purchase(); var runtime = runtime(); var result = runtime.reserve(command());
        db.getCollection("subscription_entitlements").updateOne(new Document(), new Document("$set", new Document("endsAt", Date.from(NOW.plusSeconds(1)))));
        var later = runtime(Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
        var confirmed = later.confirm(confirm(command(), result));
        payment.transaction(session -> {
            db.getCollection("purchases").updateOne(session, new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")));
            db.getCollection("subscription_entitlements").updateOne(session, new Document(), new Document("$set", new Document("revokedAt", Date.from(NOW.plusSeconds(3)))));
            return true;
        });
        assertThat(later.confirm(confirm(command(), result)).snapshot()).isEqualTo(confirmed.snapshot());
    }
    @Test void admissionSchemaUpgradeIsExplicitAndRetainsOriginalReceipt() {
        purchase(); runtime().reserve(command());
        Document before = receipt();
        db.runCommand(new Document("collMod", PaidExamStore.RECEIPTS).append("validator", PaidExamSchema.recoveryValidators().get(PaidExamStore.RECEIPTS)));
        assertThatThrownBy(() -> PaidExamSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaidExamSchema.upgradeAdmissionUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        PaidExamSchema.upgradeAdmissionUnderMaintenance(db, true);
        assertThat(receipt()).isEqualTo(before);
    }

    @Test void confirmAndRefundRaceConvergeThroughPurchaseWriteConflict() throws Exception {
        purchase(); var runtime = runtime(); var result = runtime.reserve(command());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await();
                try { runtime.confirm(confirm(command(), result)); return "CONFIRMED"; }
                catch (InternalApiException e) { assertThat(e.code()).isEqualTo("ENTITLEMENT_REVOKED"); return "CANCELED"; }
            });
            var b = pool.submit(() -> { start.await(); return payment.transaction(session -> {
                db.getCollection("purchases").updateOne(session, new Document(), new Document("$set", new Document("accessState", "REVOKED").append("purchaseState", "REFUNDED")).append("$inc", new Document("version", 1L)));
                db.getCollection("subscription_entitlements").updateOne(session, new Document(), new Document("$set", new Document("revokedAt", Date.from(NOW))).append("$inc", new Document("version", 1L)));
                return true;
            }); });
            start.countDown(); String outcome = a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertThat(service.status(USER, OP).orElseThrow().reservationStatus().name()).isEqualTo(outcome);
            if (outcome.equals("CONFIRMED")) assertThat(runtime.confirm(confirm(command(), result)).snapshot().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
            else error(() -> runtime.confirm(confirm(command(), result)), "ENTITLEMENT_REVOKED");
        }
    }
    @Test void confirmAndPreviousCompletionRaceNeverDuplicatesRestorationOrReleasesNewGuard() throws Exception {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return runtime().confirm(confirm(cmd, result)); });
            var b = pool.submit(() -> { start.await(); return tx.execute(c -> {
                var group = c.groups().findById(old).orElseThrow();
                c.groups().markCompleted(old, "old", group.getVersion(), NOW, NOW).orElseThrow();
                return true;
            }); });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        long restorations = db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"));
        assertThat(restorations).isBetween(0L, 1L);
        assertThat(db.getCollection(PaidExamStore.GUARDS).find().first().getString("currentGroupId")).isEqualTo(result.responseSnapshot().getString("attemptGroupId"));
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", old)).first().getString("status")).isEqualTo("COMPLETED");
    }
    @Test void sameKeyConcurrentTrialPaidConfirmRestoresExactlyOnce() throws Exception {
        purchase(); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> runtime().confirm(confirm(cmd, result)));
            var b = pool.submit(() -> runtime().confirm(confirm(cmd, result)));
            assertThat(a.get(10, TimeUnit.SECONDS).snapshot()).isEqualTo(b.get(10, TimeUnit.SECONDS).snapshot());
        }
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isOne();
        assertThat(db.getCollection("entitlement_ledger").countDocuments(new Document("dedupeKey", "TRIAL_PAID_RESTORATION:test:consumption"))).isOne();
    }

    @Test void trialOwnerFencePrecedesPayloadConflictOnReplay() {
        accounts.getOrCreate(evidence()); String old = oldTrial(); var cmd = transition(old); var result = runtime().reserve(cmd);
        assertThat(result.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        db.getCollection("billing_subject_links").updateOne(new Document("_id", "subject"), new Document("$set", new Document("userId", "99999999-9999-4999-8999-999999999999")));
        error(() -> runtime().reserve(command("changed")), "OPERATION_OWNER_MISMATCH");
        error(() -> service.status(USER, OP), "OPERATION_OWNER_MISMATCH");
    }
    @Test void retainedLegacyTrialOperationCannotBeRecreatedAsPaid() {
        purchase(); oldTrial();
        tx.execute(c -> { c.reservations().insert(Reservation.reserved(UUID.randomUUID().toString(), "subject", OP, command().payloadHash(),
                Reservation.Kind.REPLACEMENT, UUID.randomUUID().toString(), "legacy", "mock", null, null, NOW, NOW.plusSeconds(300))); return true; });
        error(() -> runtime().reserve(command()), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments()).isZero();
        assertThat(db.getCollection("reservations").countDocuments()).isOne();
    }

    @Test void revokedPhoneBindingCannotUseExistingFreeReplacement() {
        accounts.getOrCreate(evidence()); String old = oldTrial();
        db.getCollection("trial_eligibility").updateOne(new Document(), new Document("$set", new Document("state", "REVOKED")));
        error(() -> runtime().reserve(transition(old)), "ENTITLEMENT_INSUFFICIENT");
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
        assertThat(db.getCollection("attempt_groups").find().first().getBoolean("replacementBlocked", false)).isFalse();
    }

    @Test void firstTrialIsIssuedHeldAndConsumedInSameTransactionWithoutPaid() {
        db.getCollection("trial_eligibility").insertOne(new Document("consumerScopeId", "scope").append("userId", USER)
                .append("state", "VERIFIED").append("lastEventId", "fake-event")
                .append("candidates", List.of(new Document("keyVersion", "fake-v1").append("value", "fake-candidate"))));
        tx.execute(c -> { c.operations().insert(web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition.freeExamOnce(NOW)); return true; });
        var result = runtime().reserve(command());
        assertThat(result.source().getString("type")).isEqualTo("EXAM_PASS");
        assertThat(result.source().getString("trialClaimId")).isNotBlank();
        runtime().confirm(confirm(command(), result));
        assertThat(db.getCollection("trial_claims").countDocuments()).isOne();
        assertThat(db.getCollection("entitlement_grants").find().first().getInteger("consumedUnits")).isEqualTo(1);
        assertThat(service.status(USER, OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(runtime().reserve(command())).isEqualTo(result);
    }

}
