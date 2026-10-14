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
abstract class PaidReservationTestSupport {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    static final String USER = "11111111-1111-4111-8111-111111111111", OP = "22222222-2222-4222-8222-222222222222";
    static final String ISSUER = "https://identity.test";
    MongoTemplate mongo;
    web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor snapshots;
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
        mongo = new MongoTemplate(factory);
        snapshots = new web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor(factory);
        tx = new PaidExamTransactions(payment, mongo);
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

}
