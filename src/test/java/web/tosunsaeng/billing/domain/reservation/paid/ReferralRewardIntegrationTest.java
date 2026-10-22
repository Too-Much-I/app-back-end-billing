package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import web.tosunsaeng.billing.domain.reward.*;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.domain.WithdrawalFact;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import static org.assertj.core.api.Assertions.*;

class ReferralRewardIntegrationTest extends PaidReservationTestSupport {
    static final String OWNER = "33333333-3333-4333-8333-333333333333";
    ReferralRewardService referrals;
    @BeforeEach void referralSetup() {
        ReferralRewardSchema.installUnderMaintenance(db, true);
        referrals = new ReferralRewardService(tx, payment, properties, "test", ISSUER, clock);
    }
    String key() { return UUID.randomUUID().toString(); }
    VerifiedMemberEvidence member(String user) {
        return VerifiedMemberEvidence.fromAuthenticated(Jwt.withTokenValue("fake").header("alg", "RS256").subject(user).issuer(ISSUER)
                .claim("account_type", "MEMBER").claim("scope", "billing:purchase").audience(List.of("tosunsaeng-billing"))
                .issuedAt(NOW.minusSeconds(10)).expiresAt(NOW.plusSeconds(60)).build(), ISSUER, "SANDBOX", Duration.ofSeconds(60), NOW);
    }
    String prepare() {
        accounts.getOrCreate(evidence()); accounts.getOrCreate(member(OWNER));
        return referrals.issue(member(OWNER)).code();
    }
    void failure(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    <T> T retry(java.util.function.Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try { return action.get(); }
            catch (PaymentException e) {
                if (e.status() != 503 || attempt >= 4) throw e;
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20L * (attempt + 1)));
            }
        }
    }
    long count(String collection) { return db.getCollection(collection).countDocuments(); }
    void withdraw(String user) {
        payment.transaction(s -> new PaymentWithdrawalApplier(payment, properties, clock).applyFact(s, new WithdrawalFact(user, NOW)));
    }
    @Test void stableCodeAndNoIssuanceUntilApplication() {
        String code = prepare();
        assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}");
        assertThat(referrals.issue(member(OWNER)).code()).isEqualTo(code);
        assertThat(count(ReferralRewardService.CODES)).isEqualTo(1);
        assertThat(count("entitlement_grants")).isZero();
        var result = referrals.apply(evidence(), OP, code.toLowerCase(Locale.ROOT));
        assertThat(result.applied()).isTrue();
        assertThat(count("entitlement_grants")).isEqualTo(2);
        assertThat(count("entitlement_ledger")).isEqualTo(2);
        assertThat(count(ReferralRewardService.APPLICATIONS)).isEqualTo(1);
        assertThat(count("purchases")).isZero();
        for (String user : List.of(USER, OWNER)) {
            var grant = db.getCollection("entitlement_grants").find(new Document("ownerUserId", user)).first();
            assertThat(grant.getString("sourceType")).isEqualTo("REFERRAL");
            assertThat(grant.getInteger("availableUnits")).isEqualTo(1);
            assertThat(grant.getLong("grantOrder")).isEqualTo(1);
        }
    }
    @Test void replayRulesAndFirstSuccessfulKeyOnly() {
        String code = prepare();
        var result = referrals.apply(evidence(), OP, code);
        assertThat(referrals.apply(evidence(), OP, code)).isEqualTo(result);
        String otherKey = key();
        assertThat(referrals.apply(evidence(), otherKey, code)).isEqualTo(result);
        failure(() -> referrals.apply(evidence(), OP, "ABCDEFGHJK".equals(code) ? "BCDEFGHJKL" : "ABCDEFGHJK"), "IDEMPOTENCY_KEY_CONFLICT");
        failure(() -> referrals.apply(evidence(), otherKey, "ABCDEFGHJK".equals(code) ? "BCDEFGHJKL" : "ABCDEFGHJK"), "REFERRAL_ALREADY_APPLIED");
        assertThat(count("entitlement_grants")).isEqualTo(2);
        assertThat(count(ReferralRewardService.APPLICATIONS)).isEqualTo(1);
        assertThat(db.getCollection(ReferralRewardService.APPLICATIONS).find().first().getString("operationId")).isEqualTo(OP);
    }
    @Test void unavailableSelfAndUnpreparedDoNotReserveKey() {
        failure(() -> referrals.issue(evidence()), "PURCHASE_ACCOUNT_REQUIRED");
        String code = prepare();
        failure(() -> referrals.apply(evidence(), OP, referrals.issue(evidence()).code()), "SELF_REFERRAL_NOT_ALLOWED");
        String missing = "ABCDEFGHJK".equals(code) ? "BCDEFGHJKL" : "ABCDEFGHJK";
        failure(() -> referrals.apply(evidence(), OP, missing), "REFERRAL_CODE_UNAVAILABLE");
        assertThat(count(ReferralRewardService.APPLICATIONS)).isZero();
        assertThat(referrals.apply(evidence(), OP, code).applied()).isTrue();
    }
    @Test void sameCodeManyApplicantsNoRewardCap() {
        String code = prepare();
        for (int i = 0; i < 5; i++) {
            var evidence = member(key()); accounts.getOrCreate(evidence);
            referrals.apply(evidence, key(), code);
        }
        assertThat(count("entitlement_grants")).isEqualTo(10);
        var grants = db.getCollection("entitlement_grants").find(new Document("ownerUserId", OWNER)).sort(new Document("grantOrder", 1)).into(new ArrayList<>());
        assertThat(grants).extracting(d -> d.getLong("grantOrder")).containsExactly(1L, 2L, 3L, 4L, 5L);
    }
    @Test void sameKeyAndIssuanceConcurrentRequestsConverge() throws Exception {
        String code = prepare();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<ReferralRewardService.Applied>>();
            for (int i = 0; i < 12; i++) tasks.add(() -> retry(() -> referrals.apply(evidence(), OP, code)));
            var results = pool.invokeAll(tasks);
            String id = results.getFirst().get().applicationId();
            for (var result : results) assertThat(result.get().applicationId()).isEqualTo(id);
            var issuance = new ArrayList<Callable<String>>();
            for (int i = 0; i < 8; i++) issuance.add(() -> retry(() -> referrals.issue(evidence()).code()));
            var codes = pool.invokeAll(issuance);
            for (var result : codes) assertThat(result.get()).isEqualTo(codes.getFirst().get());
        }
        assertThat(count("entitlement_grants")).isEqualTo(2);
        assertThat(count(ReferralRewardService.CODES)).isEqualTo(2);
    }
    @Test void concurrentDifferentCodesOnlyOneWins() throws Exception {
        String code = prepare(), other = key(); accounts.getOrCreate(member(other));
        String otherCode = referrals.issue(member(other)).code();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Callable<String>>();
            for (String candidate : List.of(code, otherCode)) tasks.add(() -> {
                String operation = key();
                try { return retry(() -> referrals.apply(evidence(), operation, candidate)).applicationId(); }
                catch (PaymentException e) { return e.code(); }
            });
            var result = pool.invokeAll(tasks);
            assertThat(List.of(result.get(0).get(), result.get(1).get())).contains("REFERRAL_ALREADY_APPLIED");
        }
        assertThat(count("entitlement_grants")).isEqualTo(2);
        assertThat(count(ReferralRewardService.APPLICATIONS)).isEqualTo(1);
    }
    @Test void concurrentApplicantsShareReferrerGrantOrder() throws Exception {
        String code = prepare();
        var members = new ArrayList<VerifiedMemberEvidence>();
        for (int i = 0; i < 6; i++) { var e = member(key()); accounts.getOrCreate(e); members.add(e); }
        try (var pool = Executors.newFixedThreadPool(3)) {
            var tasks = new ArrayList<Callable<ReferralRewardService.Applied>>();
            for (var e : members) { String op = key(); tasks.add(() -> retry(() -> referrals.apply(e, op, code))); }
            for (var result : pool.invokeAll(tasks)) assertThat(result.get().applied()).isTrue();
        }
        assertThat(count("entitlement_grants")).isEqualTo(12);
        assertThat(db.getCollection("entitlement_grants").find(new Document("ownerUserId", OWNER)).into(new ArrayList<>()))
                .extracting(d -> d.getLong("grantOrder")).doesNotHaveDuplicates();
    }
    @Test void applicationWriteFailureRollsBackBothGrantsAndGuards() {
        String code = prepare();
        db.runCommand(new Document("collMod", ReferralRewardService.APPLICATIONS).append("validator", new Document("impossible", true)));
        assertThatThrownBy(() -> referrals.apply(evidence(), OP, code)).isInstanceOf(RuntimeException.class);
        for (String name : List.of("entitlement_grants", "entitlement_ledger", PaidExamStore.GUARDS, ReferralRewardService.APPLICATIONS))
            assertThat(count(name)).as(name).isZero();
    }
    @Test void unknownCommitRetriesSameTransaction() {
        String code = prepare();
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        var result = referrals.apply(evidence(), OP, code);
        assertThat(referrals.apply(evidence(), OP, code)).isEqualTo(result);
        assertThat(count("entitlement_grants")).isEqualTo(2);
    }
    @Test void secondRecipientsLedgerFailureRollsBackFirstRecipientToo() {
        String code = prepare();
        db.runCommand(new Document("collMod", "entitlement_ledger").append("validator",
                new Document("ownerUserId", new Document("$ne", OWNER))));
        assertThatThrownBy(() -> referrals.apply(evidence(), OP, code)).isInstanceOf(RuntimeException.class);
        assertThat(count("entitlement_grants")).isZero(); assertThat(count("entitlement_ledger")).isZero();
        assertThat(count(ReferralRewardService.APPLICATIONS)).isZero(); assertThat(count(PaidExamStore.GUARDS)).isZero();
    }
    @Test void uniqueIndexesPreventDuplicateOwnerApplicationAndGrantSource() {
        String code = prepare(); referrals.apply(evidence(), OP, code);
        for (String collection : List.of(ReferralRewardService.CODES, ReferralRewardService.APPLICATIONS, "entitlement_grants", "entitlement_ledger")) {
            Document duplicate = new Document(db.getCollection(collection).find().first()).append("_id", key());
            assertThatThrownBy(() -> db.getCollection(collection).insertOne(duplicate)).as(collection)
                    .isInstanceOf(com.mongodb.MongoWriteException.class);
        }
    }
    @Test void missingRuntimeCollectionIsNotAnEmptyAccount() {
        prepare(); db.getCollection(ReferralRewardService.APPLICATIONS).drop();
        failure(() -> referrals.issue(evidence()), "BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void withdrawalPriorityAndReferrerWithdrawalDoesNotUndoReplay() {
        String code = prepare(); var result = referrals.apply(evidence(), OP, code);
        withdraw(OWNER);
        assertThat(referrals.apply(evidence(), OP, code)).isEqualTo(result);
        var another = member(key()); accounts.getOrCreate(another);
        failure(() -> referrals.apply(another, key(), code), "REFERRAL_CODE_UNAVAILABLE");
        withdraw(USER);
        failure(() -> referrals.apply(evidence(), OP, code), "PAYMENT_ACCOUNT_WITHDRAWN");
        assertThat(count("entitlement_grants")).isEqualTo(2);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {OWNER, USER})
    void bothWithdrawalRacesNeverLeaveHalfAward(String withdrawing) throws Exception {
            String code = prepare();
            try (var pool = Executors.newFixedThreadPool(2)) {
                var award = pool.submit(() -> {
                    try { return retry(() -> referrals.apply(evidence(), OP, code)).applied(); }
                    catch (PaymentException e) { assertThat(e.code()).isIn("PAYMENT_ACCOUNT_WITHDRAWN", "REFERRAL_CODE_UNAVAILABLE"); return false; }
                });
                var withdrawal = pool.submit(() -> retry(() -> { withdraw(withdrawing); return true; }));
                boolean success = award.get(); withdrawal.get();
                assertThat(count("entitlement_grants")).isEqualTo(success ? 2 : 0);
                assertThat(count(ReferralRewardService.APPLICATIONS)).isEqualTo(success ? 1 : 0);
            }
    }
    @Test void detailRetentionKeepsOnceAndReplayThenWithdrawnCleanupPreservesAwards() {
        String code = prepare(); var result = referrals.apply(evidence(), OP, code);
        Instant boundary = NOW.plus(Duration.ofDays(120));
        assertThat(ReferralRewardSchema.purgeDetailsUnderMaintenance(db, true, boundary.minusNanos(1), 100)).isZero();
        assertThat(ReferralRewardSchema.purgeDetailsUnderMaintenance(db, true, boundary, 100)).isEqualTo(1);
        assertThat(referrals.apply(evidence(), OP, code)).isEqualTo(result);
        withdraw(USER);
        assertThat(ReferralRewardSchema.cleanupWithdrawnUnderMaintenance(db, true, "test", "SANDBOX", USER, boundary.minusMillis(1))).isZero();
        assertThat(ReferralRewardSchema.cleanupWithdrawnUnderMaintenance(db, true, "test", "SANDBOX", USER, boundary)).isEqualTo(1);
        withdraw(OWNER);
        ReferralRewardSchema.cleanupWithdrawnUnderMaintenance(db, true, "test", "SANDBOX", OWNER, boundary);
        var tombstone = db.getCollection(ReferralRewardService.CODES).find(new Document("code", code)).first();
        assertThat(tombstone.get("ownerUserId")).isNull(); assertThat(tombstone.get("accountId")).isNull();
        assertThat(count("entitlement_grants")).isEqualTo(2); assertThat(count("entitlement_ledger")).isEqualTo(2);
        var another = member(key()); accounts.getOrCreate(another);
        failure(() -> referrals.apply(another, key(), code), "REFERRAL_CODE_UNAVAILABLE");
    }
    @Test void schemaIndexesStrictnessAndNoTtl() {
        assertThatThrownBy(() -> ReferralRewardSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        ReferralRewardSchema.verify(db);
        assertThatThrownBy(() -> db.getCollection(ReferralRewardService.APPLICATIONS).insertOne(new Document("_id", key())))
                .isInstanceOf(com.mongodb.MongoWriteException.class);
        db.getCollection(ReferralRewardService.APPLICATIONS).createIndex(new Document("appliedAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(120L, TimeUnit.DAYS));
        assertThatThrownBy(() -> ReferralRewardSchema.verify(db)).isInstanceOf(IllegalStateException.class);
    }
    @Test void authBarrierEnvironmentAndMissingProofFailClosed() {
        String code = prepare();
        var late = new ReferralRewardService(tx, payment, properties, "test", ISSUER, Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
        failure(() -> late.apply(evidence(), OP, code), "UNAUTHENTICATED");
        var staging = new ReferralRewardService(tx, payment, properties, "staging", ISSUER, clock);
        failure(() -> staging.apply(evidence(), OP, code), "REFERRAL_CODE_UNAVAILABLE");
        db.getCollection("payment_accounts").updateOne(new Document("userId", OWNER), new Document("$unset", new Document("memberEvidence", "")));
        failure(() -> referrals.apply(evidence(), OP, code), "BILLING_TEMPORARILY_UNAVAILABLE");
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        failure(() -> referrals.issue(evidence()), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(count("entitlement_grants")).isZero();
    }
    @Test void paidFirstAndIntegratedReaderPreserveReferral() {
        String code = prepare(); purchase(); referrals.apply(evidence(), OP, code);
        assertThat(runtime().reserve(command()).source().getString("type")).isEqualTo("SUBSCRIPTION");
        assertThat(db.getCollection("entitlement_grants").find(new Document("ownerUserId", USER)).first().getInteger("availableUnits")).isEqualTo(1);
        var benefit = new web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Benefit(
                "FREE_EXAM_ONCE", "test", "EXAM", 0,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Eligibility.VERIFIED,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Usage.NOT_STARTED,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Action.BLOCKED, false,
                web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Action.BLOCKED, List.of(), List.of());
        snapshots.execute(() -> {
            assertThat(new PaidExamQueryReader(mongo, "test", "SANDBOX", ISSUER).augment(USER, List.of(benefit)).getFirst().availableQuantity()).isEqualTo(1);
            return true;
        });
    }
    @Test void pendingGuardAndExistingInviteQuotaArePreservedAndShareFifo() {
        String code = prepare(); InviteRewardSchema.installUnderMaintenance(db, true);
        var invites = new InviteRewardService(tx, payment, properties, "test", ISSUER, clock);
        invites.award(evidence(), key()); invites.award(evidence(), key()); invites.award(evidence(), key());
        tx.execute(c -> {
            Document guard = c.operations().findOne(new org.springframework.data.mongodb.core.query.BasicQuery(
                    new Document("environment", "test").append("userId", USER)), Document.class, PaidExamStore.GUARDS);
            c.paid().acquire("test", USER, guard.getLong("version"), key(), "reservation", "group"); return true;
        });
        Document before = db.getCollection(PaidExamStore.GUARDS).find(new Document("userId", USER)).first();
        referrals.apply(evidence(), OP, code);
        Document after = db.getCollection(PaidExamStore.GUARDS).find(new Document("userId", USER)).first();
        for (String field : List.of("pendingOperationId", "pendingReservationId", "pendingGroupId", "currentSessionId", "currentGroupId"))
            assertThat(after.get(field)).isEqualTo(before.get(field));
        assertThat(db.getCollection("entitlement_grants").find(new Document("ownerUserId", USER).append("sourceType", "REFERRAL")).first()
                .getLong("grantOrder")).isEqualTo(4L);
        assertThat(invites.read(USER).awardedCount()).isEqualTo(3);
        failure(() -> invites.award(evidence(), key()), "INVITE_REWARD_LIMIT_REACHED");
    }
    @Test void cancelAndExpiryReturnTheSameReferralUnit() {
        String code = prepare(); referrals.apply(evidence(), OP, code);
        var first = runtime().reserve(command());
        String id = first.responseSnapshot().getString("reservationId");
        var body = new web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest(USER,
                web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.CALLER_ABORTED);
        runtime().cancel(new web.tosunsaeng.billing.domain.reservation.application.CancelCommand(OP, id, USER, body.reason(),
                new web.tosunsaeng.billing.domain.reservation.application.LifecyclePayloadHasher().hashCancel(id, body)));
        var cmd = new web.tosunsaeng.billing.domain.reservation.application.ReserveCommand(key(), USER, "again", "mock",
                new web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher().hash(
                        new web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest(USER, "again", "mock")));
        var second = runtime().reserve(cmd);
        runtime(Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC)).expire(USER, cmd.operationId(), second.responseSnapshot().getString("reservationId"));
        assertThat(db.getCollection("entitlement_grants").find(new Document("ownerUserId", USER)).first().getInteger("availableUnits")).isEqualTo(1);
        assertThat(count(ReferralRewardService.APPLICATIONS)).isEqualTo(1);
    }
    @Test void replacementUsesSameConsumptionAndPaidSwitchDoesNotRestoreReferral() {
        String code = prepare(); referrals.apply(evidence(), OP, code);
        var initialCommand = command(); var initial = runtime().reserve(initialCommand); runtime().confirm(confirm(initialCommand, initial));
        String group = initial.responseSnapshot().getString("attemptGroupId");
        var restart = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var body = new web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest(USER, "replacement", "mock", null, null, null, restart);
        var cmd = new web.tosunsaeng.billing.domain.reservation.application.ReserveCommand(key(), USER, "replacement", "mock", null, null, null,
                new web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher().hash(body), restart);
        var replacement = runtime().reserve(cmd);
        assertThat(replacement.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        runtime().confirm(confirm(cmd, replacement)); purchase();
        var nextContext = new RestartContext(group, "replacement", RestartContext.StartReason.RESTART_INCOMPLETE);
        var nextBody = new web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest(USER, "paid", "mock", null, null, null, nextContext);
        var next = new web.tosunsaeng.billing.domain.reservation.application.ReserveCommand(key(), USER, "paid", "mock", null, null, null,
                new web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher().hash(nextBody), nextContext);
        var paid = runtime().reserve(next); runtime().confirm(confirm(next, paid));
        assertThat(paid.source().getString("type")).isEqualTo("SUBSCRIPTION");
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType", "TRIAL_PAID_RESTORATION"))).isZero();
        assertThat(count(ReferralRewardService.APPLICATIONS)).isEqualTo(1);
    }
}
