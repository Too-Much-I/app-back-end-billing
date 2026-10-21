package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import web.tosunsaeng.billing.domain.reward.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.*;
import static org.assertj.core.api.Assertions.*;

class InviteRewardIntegrationTest extends PaidReservationTestSupport {
    InviteRewardService rewards;
    @BeforeEach void rewardSetup() {
        InviteRewardSchema.installUnderMaintenance(db, true);
        rewards = new InviteRewardService(tx, payment, properties, "test", ISSUER, clock);
    }
    String key() { return UUID.randomUUID().toString(); }
    void prepare() { accounts.getOrCreate(evidence()); }
    void failure(Runnable work, String code) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    // Contention may exhaust the shared bounded transaction retry. Transport retries retain the original key.
    InviteRewardService.Award retryAward(String key) {
        for (int attempt = 0; ; attempt++) {
            try { return rewards.award(evidence(), key); }
            catch (PaymentException e) {
                if (e.status() != 503 || attempt >= 4) throw e;
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20L * (attempt + 1)));
            }
        }
    }
    @Test void readIsReadOnlyAndPreparationCanResumeSameKey() {
        var before = control();
        assertThat(rewards.read(USER)).isEqualTo(new InviteRewardService.Quota(0, 3, 3));
        assertThat(control()).isEqualTo(before);
        assertThat(db.getCollection(InviteRewardService.ACCOUNTS).countDocuments()).isZero();
        failure(() -> rewards.award(evidence(), OP), "PURCHASE_ACCOUNT_REQUIRED");
        assertThat(db.getCollection(InviteRewardService.RECEIPTS).countDocuments()).isZero();
        prepare();
        assertThat(rewards.award(evidence(), OP).awardedCount()).isEqualTo(1);
        assertThat(db.getCollection("purchases").countDocuments()).isZero();
    }
    @Test void thirdSuccessReplaysBeforeLimitAndLimitReceiptCommits() {
        prepare();
        var first = rewards.award(evidence(), key());
        rewards.award(evidence(), key());
        var third = rewards.award(evidence(), OP);
        assertThat(rewards.award(evidence(), OP)).isEqualTo(third);
        assertThat(rewards.award(evidence(), first.operationId())).isEqualTo(first);
        String denied = key();
        failure(() -> rewards.award(evidence(), denied), "INVITE_REWARD_LIMIT_REACHED");
        failure(() -> rewards.award(evidence(), denied), "INVITE_REWARD_LIMIT_REACHED");
        assertThat(db.getCollection(InviteRewardService.RECEIPTS).countDocuments()).isEqualTo(4);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(3);
        assertThat(db.getCollection("entitlement_ledger").countDocuments()).isEqualTo(3);
        assertThat(rewards.read(USER).remainingAwards()).isZero();
    }
    @Test void sameKeyTwentyConcurrentRequestsAwardOnce() throws Exception {
        prepare();
        try (var pool = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Callable<InviteRewardService.Award>>();
            for (int i=0; i<20; i++) tasks.add(() -> retryAward(OP));
            for (var future : pool.invokeAll(tasks)) assertThat(future.get().awardedCount()).isEqualTo(1);
        }
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(1);
    }
    @Test void concurrentDifferentKeysAtTwoNeverExceedThree() throws Exception {
        prepare(); rewards.award(evidence(), key()); rewards.award(evidence(), key());
        try (var pool = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Callable<String>>();
            for (int i=0; i<12; i++) tasks.add(() -> {
                try { retryAward(key()); return "AWARDED"; }
                catch (PaymentException e) { return e.code(); }
            });
            var results = new ArrayList<String>();
            for (var future : pool.invokeAll(tasks)) results.add(future.get());
            assertThat(results).containsOnly("AWARDED", "INVITE_REWARD_LIMIT_REACHED");
            assertThat(Collections.frequency(results, "AWARDED")).isEqualTo(1);
        }
        assertThat(rewards.read(USER).awardedCount()).isEqualTo(3);
    }
    @Test void pendingExamGuardIsPreservedAndRestorationSharesFifo() {
        prepare();
        tx.execute(c -> {
            var guard = c.paid().createGuard("test", USER, new Document("kind", "MEMBER_ACCOUNT").append("reference", account().getString("_id"))
                    .append("version", account().getLong("version")).append("epoch", null));
            long order = c.paid().nextGrantOrder("test", USER, 1);
            var grant = EntitlementGrant.restoredTrial("restored", "test", USER, "original-consumption", order, NOW);
            c.grants().insert(grant); c.ledger().insert(EntitlementLedgerEntry.restoredTrial("restored-ledger", grant, NOW));
            c.paid().acquire("test", USER, 2, key(), "reservation", "group"); return true;
        });
        var before = db.getCollection(PaidExamStore.GUARDS).find().first();
        rewards.award(evidence(), OP);
        var after = db.getCollection(PaidExamStore.GUARDS).find().first();
        for (String f : List.of("pendingOperationId", "pendingReservationId", "pendingGroupId", "currentSessionId", "currentGroupId"))
            assertThat(after.get(f)).isEqualTo(before.get(f));
        assertThat(db.getCollection("entitlement_grants").find(new Document("sourceType", "INVITE")).first().getLong("grantOrder")).isEqualTo(2L);
    }
    @Test void withdrawnDeniesReadAndSuccessfulReplay() {
        prepare(); rewards.award(evidence(), OP);
        db.getCollection("payment_account_lifecycles").updateOne(new Document("userId", USER), new Document("$set", new Document("state", "WITHDRAWN")));
        failure(() -> rewards.read(USER), "PAYMENT_ACCOUNT_WITHDRAWN");
        failure(() -> rewards.award(evidence(), OP), "PAYMENT_ACCOUNT_WITHDRAWN");
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(1);
    }
    @Test void missingCounterAndMissingGrantNeverResetQuota() {
        prepare(); rewards.award(evidence(), OP);
        var counter = db.getCollection(InviteRewardService.ACCOUNTS).find().first();
        db.getCollection(InviteRewardService.ACCOUNTS).deleteMany(new Document());
        failure(() -> rewards.read(USER), "BILLING_TEMPORARILY_UNAVAILABLE");
        failure(() -> rewards.award(evidence(), key()), "BILLING_TEMPORARILY_UNAVAILABLE");
        db.getCollection(InviteRewardService.ACCOUNTS).insertOne(counter);
        db.getCollection("entitlement_grants").deleteMany(new Document());
        failure(() -> rewards.read(USER), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void exactRetentionBoundaryAndOldKeyCanIssueAgainWithoutResettingQuota() {
        prepare(); rewards.award(evidence(), OP);
        Instant boundary = NOW.plus(Duration.ofDays(120));
        assertThat(InviteRewardSchema.purgeDetailsUnderMaintenance(db, true, boundary.minusNanos(1), 100)).isZero();
        assertThat(InviteRewardSchema.purgeDetailsUnderMaintenance(db, true, boundary, 100)).isEqualTo(1);
        assertThat(rewards.award(evidence(), OP).awardedCount()).isEqualTo(2);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(2);
    }
    @Test void receiptWriteFailureRollsBackAllRewardWrites() {
        prepare();
        // A validator failure at the last write proves all prior writes use this same session.
        db.runCommand(new Document("collMod", InviteRewardService.RECEIPTS).append("validator", new Document("impossible", true)));
        assertThatThrownBy(() -> rewards.award(evidence(), OP)).isInstanceOf(RuntimeException.class);
        for (String name : List.of(InviteRewardService.ACCOUNTS, InviteRewardService.RECEIPTS, "entitlement_grants", "entitlement_ledger", PaidExamStore.GUARDS))
            assertThat(db.getCollection(name).countDocuments()).as(name).isZero();
    }
    @Test void unknownCommitRetriesSameTransaction() {
        prepare();
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        rewards.award(evidence(), OP);
        assertThat(rewards.award(evidence(), OP).awardedCount()).isEqualTo(1);
        assertThat(db.getCollection("entitlement_grants").countDocuments()).isEqualTo(1);
    }
    @Test void schemaRequiresExplicitMaintenanceAndForbidsAutomaticTtl() {
        assertThatThrownBy(() -> InviteRewardSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        InviteRewardSchema.verify(db);
        assertThatThrownBy(() -> db.getCollection(InviteRewardService.ACCOUNTS).insertOne(new Document("_id", key())))
                .isInstanceOf(com.mongodb.MongoWriteException.class);
        db.getCollection(InviteRewardService.RECEIPTS).createIndex(new Document("terminalAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(120L, TimeUnit.DAYS));
        assertThatThrownBy(() -> InviteRewardSchema.verify(db)).isInstanceOf(IllegalStateException.class);
    }
    @Test void retainedIssuanceLedgerDetectsMissingCounterAndGrantAfterReceiptPurge() {
        prepare(); rewards.award(evidence(), OP);
        InviteRewardSchema.purgeDetailsUnderMaintenance(db, true, NOW.plus(Duration.ofDays(120)), 100);
        db.getCollection(InviteRewardService.ACCOUNTS).deleteMany(new Document());
        db.getCollection("entitlement_grants").deleteMany(new Document());
        failure(() -> rewards.read(USER), "BILLING_TEMPORARILY_UNAVAILABLE");
        failure(() -> rewards.award(evidence(), key()), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void expiredAuthorizationAndRecoveryBarrierCannotWrite() {
        prepare();
        var late = new InviteRewardService(tx, payment, properties, "test", ISSUER, Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
        failure(() -> late.award(evidence(), OP), "UNAUTHENTICATED");
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        failure(() -> rewards.award(evidence(), OP), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection(InviteRewardService.RECEIPTS).countDocuments()).isZero();
    }
    @Test void missingCollectionIsNotAnEmptyNewAccount() {
        db.getCollection(InviteRewardService.ACCOUNTS).drop();
        failure(() -> rewards.read(USER), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void inviteCanBeConsumedReplacedAndPaidSwitchNeverRestoresIt() {
        prepare(); rewards.award(evidence(), OP);
        var runtime = runtime(); var initialCommand = command(); var initial = runtime.reserve(initialCommand);
        assertThat(initial.source().getString("type")).isEqualTo("EXAM_PASS");
        runtime.confirm(confirm(initialCommand, initial));
        String group = initial.responseSnapshot().getString("attemptGroupId");
        var restart = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var req = new web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest(USER, "replacement", "mock", null, null, null, restart);
        var cmd = new web.tosunsaeng.billing.domain.reservation.application.ReserveCommand(key(), USER, "replacement", "mock", null, null, null,
                new web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher().hash(req), restart);
        var replacement = runtime.reserve(cmd);
        assertThat(replacement.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        runtime.confirm(confirm(cmd, replacement));
        purchase();
        var nextContext = new RestartContext(group, "replacement", RestartContext.StartReason.RESTART_INCOMPLETE);
        var nextBody = new web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest(USER, "paid", "mock", null, null, null, nextContext);
        var next = new web.tosunsaeng.billing.domain.reservation.application.ReserveCommand(key(), USER, "paid", "mock", null, null, null,
                new web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher().hash(nextBody), nextContext);
        var paid = runtime.reserve(next); runtime.confirm(confirm(next, paid));
        assertThat(paid.source().getString("type")).isEqualTo("SUBSCRIPTION");
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isZero();
        assertThat(rewards.read(USER).awardedCount()).isEqualTo(1);
    }
    @Test void cancelledAndExpiredReservationRestoreUnitsButNotRewardQuota() {
        prepare(); rewards.award(evidence(), OP);
        var first = runtime().reserve(command());
        String id = first.responseSnapshot().getString("reservationId");
        var body = new web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest(USER, web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.CALLER_ABORTED);
        var cancel = new web.tosunsaeng.billing.domain.reservation.application.CancelCommand(OP, id, USER, body.reason(),
                new web.tosunsaeng.billing.domain.reservation.application.LifecyclePayloadHasher().hashCancel(id, body));
        runtime().cancel(cancel);
        assertThat(db.getCollection("entitlement_grants").find().first().getInteger("availableUnits")).isEqualTo(1);
        var cmd = new web.tosunsaeng.billing.domain.reservation.application.ReserveCommand(key(), USER, "again", "mock", new web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher().hash(
                new web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest(USER, "again", "mock")));
        var second = runtime().reserve(cmd);
        runtime(Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC)).expire(USER, cmd.operationId(), second.responseSnapshot().getString("reservationId"));
        assertThat(db.getCollection("entitlement_grants").find().first().getInteger("availableUnits")).isEqualTo(1);
        assertThat(rewards.read(USER).awardedCount()).isEqualTo(1);
    }
    @Test void paidFirstPreservesInviteAndIntegratedReaderCountsIt() {
        purchase(); rewards.award(evidence(), OP);
        assertThat(runtime().reserve(command()).source().getString("type")).isEqualTo("SUBSCRIPTION");
        assertThat(db.getCollection("entitlement_grants").find().first().getInteger("availableUnits")).isEqualTo(1);
        var benefit = new web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Benefit(
                "FREE_EXAM_ONCE", "test", "EXAM", 0,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Eligibility.VERIFIED,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Usage.NOT_STARTED,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Action.BLOCKED, false,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Action.BLOCKED, List.of(), List.of());
        snapshots.execute(() -> {
            var reader = new PaidExamQueryReader(mongo, "test", "SANDBOX", ISSUER);
            assertThat(reader.augment(USER, List.of(benefit)).getFirst().availableQuantity()).isEqualTo(1);
            return true;
        });
    }

}
