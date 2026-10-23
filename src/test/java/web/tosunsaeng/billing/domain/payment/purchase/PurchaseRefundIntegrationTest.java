package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import web.tosunsaeng.billing.domain.payment.application.*;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.domain.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.billing.domain.payment.purchase.PurchaseCore.Outcome.*;

@Testcontainers(disabledWithoutDocker = false)
class PurchaseRefundIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    static final String USER = "11111111-1111-4111-8111-111111111111";
    final PurchaseLookupKey lookup = new PurchaseLookupKey(new byte[32], "fake-v1");
    MongoClient client; MongoDatabase db; PaymentStore store; PaymentProperties properties;
    PurchaseCore core; String ref;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        store = new PaymentStore(client, new SimpleMongoClientDatabaseFactory(client, "purchase_" + UUID.randomUUID().toString().replace("-", "")));
        db = store.database();
        PaymentFoundationInitializer.initialize(db); PaymentFoundationInitializer.initializeWithdrawal(db);
        PaymentRecoverySchema.initializeEmpty(db);
        properties = new PaymentProperties(); properties.setEnvironment("SANDBOX"); properties.getRecovery().setFenceRequired(true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).insertOne(new Document("_id", "control").append("environment", "SANDBOX")
                .append("consumer", "billing").append("schemaVersion", 4L).append("epoch", UUID.randomUUID().toString())
                .append("version", 1L).append("barrier", "ALL_BLOCKED"));
        PurchaseCoreSchema.installUnderMaintenance(db, true); barrier("OPEN");
        ref = new PaymentAccountService(store, properties, Clock.fixed(NOW, ZoneOffset.UTC)).getOrCreate(USER, NOW.plusSeconds(60)).purchaseAccountRefId();
        for (PaymentOffer offer : PaymentOffer.values()) db.getCollection("payment_products").insertOne(new Document("environment", "SANDBOX")
                .append("store", "PLAY_STORE").append("appId", "fake.app").append("storeProductId", offer.name())
                .append("rcProductId", "fake-rc-" + offer.name()).append("offerCode", offer.name()).append("catalogVersion", 1L)
                .append("durationSeconds", offer.durationSeconds).append("saleEnabled", false));
        core = core(NOW);
    }
    @AfterEach void close() {
        if (client != null) {
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
            db.drop(); client.close(); // Disposable per-test database only.
        }
    }
    PurchaseCore core(Instant now) { return new PurchaseCore(store, properties, Clock.fixed(now, ZoneOffset.UTC), Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), lookup.version()); }
    PurchaseObservation observation(String event, String transaction, String owner, PurchaseObservation.Kind kind, PaymentOffer offer, Instant time) {
        return new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app", lookup.transaction("SANDBOX", "PLAY_STORE", "fake.app", transaction),
                lookup.version(), lookup.event("SANDBOX", "fake-project", event), kind, owner, offer.name(), time,
                new PurchaseObservation.Money(BigDecimal.valueOf(offer.referencePriceKrw), "KRW"));
    }
    PurchaseObservation purchase(String event, String tx) { return observation(event, tx, ref, PurchaseObservation.Kind.PURCHASE, PaymentOffer.PREMIUM_1D, NOW.minusSeconds(100)); }
    void barrier(String value) { db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", value))); }
    Document first(String collection) { return db.getCollection(collection).find().first(); }
    long count(String collection) { return db.getCollection(collection).countDocuments(); }
    long pending() { return first("payment_account_lifecycles").getLong("pendingEvidence"); }
    void withdraw() {
        var fence = new PaymentRecoveryFence(store, properties); var ticket = fence.capture();
        store.transaction(s -> { fence.acquire(s, ticket, PaymentRecoveryFence.Writer.PUSH);
            return new PaymentWithdrawalApplier(store, properties, Clock.fixed(NOW, ZoneOffset.UTC)).applyFact(s, new WithdrawalFact(USER, NOW)); });
    }

    void enable() {
        barrier("ALL_BLOCKED"); RefundCoreSchema.upgradeUnderMaintenance(db, true); barrier("OPEN");
        properties.getRefund().setProcessingEnabled(true); core = core(NOW);
    }
    PurchaseObservation refund(String event, String tx, String amount, RefundEvidence.Source source) {
        var p = purchase(event, tx);
        return new PurchaseObservation(p.environment(), p.store(), p.appId(), p.transactionKey(), p.lookupKeyVersion(),
                p.providerEventKey(), PurchaseObservation.Kind.REFUND, ref, p.storeProductId(), p.purchasedAt(), null,
                new RefundEvidence(source, "a".repeat(64), NOW.minusSeconds(3600),
                        amount == null ? null : new BigDecimal(amount), amount == null ? null : "KRW"));
    }
    PurchaseCore.Outcome apply(PurchaseObservation o) { core.receive(o); return core.apply(o.providerEventKey()); }
    long effects(String type) { return db.getCollection("payment_ledger").countDocuments(new Document("type", type)); }
    Document refunded() { return db.getCollection("purchases").find(new Document("refund", new Document("$exists", true))).first(); }
    void enableReviews() {
        enable(); barrier("ALL_BLOCKED");
        MixedStorePurchaseSchema.upgradeUnderMaintenance(db, true);
        PurchaseReviewResolutionSchema.upgradeUnderMaintenance(db, true); barrier("OPEN");
        properties.setReviewResolutionEnabled(true); core = core(NOW);
        // Reader prerequisites are metadata-only for this disposable fixture.
        barrier("ALL_BLOCKED");
        web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema.installUnderMaintenance(db, true);
        barrier("OPEN");
    }
    web.tosunsaeng.billing.domain.payment.query.PaidQueryResponse readPaid() {
        var factory = new SimpleMongoClientDatabaseFactory(client, db.getName());
        var reader = new web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader(
                new org.springframework.data.mongodb.core.MongoTemplate(factory), properties);
        var snapshots = new web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor(factory);
        reader.prepare();
        return snapshots.execute(() -> reader.read(USER, NOW));
    }
    PurchaseObservation decodedRefund(String tx) {
        var decoder = new web.tosunsaeng.billing.domain.payment.provider.RevenueCatPurchaseDecoder("fake-project", "SANDBOX", lookup,
                Map.of("rc-product", new web.tosunsaeng.billing.domain.payment.provider.RevenueCatPurchaseDecoder.Product(
                        "fake.app", PurchaseObservation.Store.PLAY_STORE, "PREMIUM_1D")), Clock.fixed(NOW, ZoneOffset.UTC));
        var row = new Document("object", "purchase").append("id", "rc-" + tx).append("customer_id", ref).append("original_customer_id", ref)
                .append("product_id", "rc-product").append("store_purchase_identifier", tx).append("environment", "sandbox")
                .append("store", "play_store").append("ownership", "purchased").append("status", "refunded").append("quantity", 1)
                .append("purchased_at", NOW.minusSeconds(100).toEpochMilli());
        return decoder.purchase(row.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8), ref).observation();
    }
    WebhookReceipt cancellation(String event, String tx) {
        var p = purchase(event, tx);
        return new WebhookReceipt(p.environment(), p.providerEventKey(), "a".repeat(64), lookup.version(), null, ref,
                WebhookReceipt.Disposition.REVIEW, "EVENT_REQUIRES_LOOKUP", new ReviewPurchaseCandidate(p.environment(), p.store(), p.appId(),
                p.transactionKey(), p.lookupKeyVersion(), ref, p.storeProductId(), p.purchasedAt()));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void decodedRefundAndCancellationConvergeInEitherOrderAndRepurchaseIsUsable(boolean refundFirst) {
        enableReviews(); apply(purchase("old", "old")); apply(purchase("new", "new"));
        var event = cancellation("cancel", "old"); var refund = decodedRefund("old");
        if (refundFirst) apply(refund);
        core.receiveWebhook(event);
        if (!refundFirst) {
            assertThat(readPaid().active()).isNull();
            apply(refund);
        }
        assertThat(readPaid().active()).isTrue();
        assertThat(pending()).isZero();
        var review = db.getCollection("payment_purchase_cases").find(new Document("reason", "EVENT_REQUIRES_LOOKUP")).first();
        assertThat(review.getString("reviewState")).isEqualTo("RESOLVED");
        var money = db.getCollection("payment_purchase_cases").find(new Document("reason", "REFUND_AMOUNT_UNKNOWN")).first();
        assertThat(money.getString("reviewState")).isEqualTo("OPEN");
        assertThat(money.getBoolean("counted")).isFalse();
        var slots = db.getCollection("subscription_entitlements").find().into(new ArrayList<>());
        core.receiveWebhook(event); apply(refund);
        assertThat(db.getCollection("subscription_entitlements").find().into(new ArrayList<>())).isEqualTo(slots);
        assertThat(effects("ACCESS_REVOKED")).isOne(); assertThat(pending()).isZero();
    }
    @Test void mismatchedPurchaseTimeCannotRevokeAnExistingPurchase() {
        enableReviews(); apply(purchase("old", "old"));
        var correct = decodedRefund("old");
        var wrong = new PurchaseObservation(correct.environment(), correct.store(), correct.appId(), correct.transactionKey(),
                correct.lookupKeyVersion(), lookup.event("SANDBOX", "fake-project", "bad-time"), correct.kind(), ref,
                correct.storeProductId(), NOW, null, correct.refundEvidence());
        assertThat(apply(wrong)).isEqualTo(REVIEW_REQUIRED);
        assertThat(first("payment_purchase_cases").getString("reason")).isEqualTo("REFUND_BINDING_UNRESOLVED");
        assertThat(first("purchases").getString("accessState")).isEqualTo("ALLOWED");
        assertThat(effects("ACCESS_REVOKED")).isZero();
    }
    @Test void legacyReversalIsResolvedWithoutChangingOriginalBytesAndOtherReviewsRemain() {
        enableReviews(); apply(purchase("old", "old")); apply(purchase("new", "new"));
        var legacy = observation("legacy", "old", ref, PurchaseObservation.Kind.REFUND, PaymentOffer.PREMIUM_1D, NOW.minusSeconds(100));
        apply(legacy);
        var original = db.getCollection("payment_purchase_cases").find(new Document("providerEventKey", legacy.providerEventKey())).first();
        core.receiveWebhook(cancellation("other", "unrelated"));
        apply(decodedRefund("old"));
        var resolved = db.getCollection("payment_purchase_cases").find(new Document("_id", original.get("_id"))).first();
        assertThat(resolved.get("normalizedObservation")).isEqualTo(original.get("normalizedObservation"));
        assertThat(resolved.get("digest")).isEqualTo(original.get("digest"));
        assertThat(resolved.getString("resolvedObservationKey")).isNotBlank();
        assertThat(pending()).isOne();
        assertThat(readPaid().status()).isEqualTo(web.tosunsaeng.billing.domain.payment.query.PaidQueryResponse.Status.PENDING);
    }
    @Test void cancellationAloneNeverRevokesOrResolvesAndMissingHintStaysManual() {
        enableReviews(); apply(purchase("old", "old"));
        var hint = cancellation("cancel", "old"); core.receiveWebhook(hint);
        apply(purchase("replay", "old"));
        assertThat(first("payment_purchase_cases").getString("resolvedObservationKey")).isNull();
        assertThat(effects("ACCESS_REVOKED")).isZero();
        core.receiveWebhook(new WebhookReceipt("SANDBOX", lookup.event("SANDBOX", "fake-project", "missing"), "b".repeat(64),
                lookup.version(), null, ref, WebhookReceipt.Disposition.REVIEW, "EVENT_REQUIRES_LOOKUP"));
        apply(decodedRefund("old"));
        assertThat(pending()).isOne(); // Missing transaction evidence is not inferred from account.
    }
    @Test void refundResolutionAndUnknownCommitAreAtomicUnderConcurrentDelivery() throws Exception {
        enableReviews(); apply(purchase("old", "old")); apply(purchase("new", "new"));
        core.receiveWebhook(cancellation("cancel", "old"));
        apply(observation("legacy", "old", ref, PurchaseObservation.Kind.REFUND, PaymentOffer.PREMIUM_1D, NOW.minusSeconds(100)));
        var a = decodedRefund("old"); var b = refund("second", "old", null, RefundEvidence.Source.REVENUECAT_API);
        core.receive(a); core.receive(b);
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(() -> core.apply(a.providerEventKey())); var y = pool.submit(() -> core.apply(b.providerEventKey()));
            assertThat(x.get()).isEqualTo(REVIEW_REQUIRED); assertThat(y.get()).isEqualTo(REVIEW_REQUIRED);
        }
        assertThat(pending()).isZero(); assertThat(readPaid().active()).isTrue();
        assertThat(effects("ACCESS_REVOKED")).isOne(); assertThat(effects("TIMELINE_REFLOWED")).isOne();
        assertThat(db.getCollection("payment_purchase_cases").countDocuments(new Document("reviewState", "RESOLVED"))).isEqualTo(2);
    }
    @Test void reviewUpdateFailureRollsBackRefundAndCounterTogether() {
        enableReviews(); apply(purchase("old", "old")); apply(purchase("new", "new"));
        core.receiveWebhook(cancellation("cancel", "old"));
        var refund = decodedRefund("old"); core.receive(refund);
        var before = db.getCollection("purchases").find().into(new ArrayList<>());
        var timeline = first("payment_timelines"); long responsibility = pending();
        db.runCommand(new Document("collMod", "payment_purchase_cases").append("validator", new Document("impossible", new Document("$exists", true))));
        assertThatThrownBy(() -> core.apply(refund.providerEventKey())).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("purchases").find().into(new ArrayList<>())).isEqualTo(before);
        assertThat(first("payment_timelines")).isEqualTo(timeline); assertThat(pending()).isEqualTo(responsibility);
        assertThat(effects("ACCESS_REVOKED")).isZero();
    }
    @Test void knownAmountCompletesAmountReviewWithoutSecondAccessEffect() {
        enableReviews(); apply(purchase("old", "old")); apply(decodedRefund("old"));
        apply(refund("amount", "old", "100", RefundEvidence.Source.REVENUECAT_API));
        var review = first("payment_purchase_cases");
        assertThat(review.getString("reviewState")).isEqualTo("RESOLVED");
        assertThat(review.getString("resolvedObservationKey")).isNotBlank();
        assertThat(effects("ACCESS_REVOKED")).isOne(); assertThat(pending()).isZero();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"ledger", "effect", "account", "transaction"})
    void amountReviewDoesNotBypassMissingOrMismatchedAccessProof(String broken) {
        enableReviews(); apply(purchase("old", "old")); apply(decodedRefund("old")); apply(purchase("new", "new"));
        assertThat(readPaid().active()).isTrue();
        switch (broken) {
            case "ledger" -> db.getCollection("payment_ledger").deleteOne(new Document("type", "ACCESS_REVOKED"));
            case "effect" -> db.getCollection("payment_ledger").updateOne(new Document("type", "ACCESS_REVOKED"),
                    new Document("$set", new Document("detail.effectId", UUID.randomUUID().toString())));
            case "account" -> db.getCollection("payment_purchase_cases").updateOne(new Document(),
                    new Document("$set", new Document("purchaseId", UUID.randomUUID().toString())));
            case "transaction" -> db.getCollection("payment_purchase_cases").updateOne(new Document(),
                    new Document("$set", new Document("normalizedObservation.transactionKey", "f".repeat(64))));
        }
        assertThat(readPaid().status()).isEqualTo(web.tosunsaeng.billing.domain.payment.query.PaidQueryResponse.Status.PENDING);
    }
    @Test void replayAfterManualCompensationDoesNotChangeExtendedRepurchasePeriod() {
        enableReviews(); apply(purchase("old", "old")); apply(purchase("new", "new")); apply(refund("manual", "old", null, RefundEvidence.Source.REVENUECAT_API));
        var slot = db.getCollection("subscription_entitlements").find(new Document("revokedAt", null)).first();
        var extended = Date.from(slot.getDate("endsAt").toInstant().plus(Duration.ofDays(4)));
        db.getCollection("subscription_entitlements").updateOne(new Document("_id", slot.get("_id")), new Document("$set", new Document("endsAt", extended)));
        db.getCollection("payment_timelines").updateOne(new Document(), new Document("$set", new Document("timelineEnd", extended)));
        var before = db.getCollection("subscription_entitlements").find().into(new ArrayList<>());
        var timeline = first("payment_timelines"); var effect = refunded().get("refund", Document.class);
        apply(decodedRefund("old"));
        assertThat(db.getCollection("subscription_entitlements").find().into(new ArrayList<>())).isEqualTo(before);
        assertThat(first("payment_timelines")).isEqualTo(timeline);
        assertThat(refunded().get("refund", Document.class)).isEqualTo(effect);
        assertThat(effects("ACCESS_REVOKED")).isOne();
    }
    @Test void unknownThenCumulativeAndAdditionalReturnsEndAccessOnlyOnce() {
        enable(); apply(purchase("p", "tx"));
        assertThat(apply(refund("r0", "tx", null, RefundEvidence.Source.REVENUECAT_API))).isEqualTo(REVIEW_REQUIRED);
        var effect = refunded().get("refund", Document.class);
        assertThat(effect.get("refundedTotal")).isNull();
        assertThat(first("payment_purchase_cases").getString("reason")).isEqualTo("REFUND_AMOUNT_UNKNOWN");
        assertThat(first("payment_purchase_cases").getString("reviewState")).isEqualTo("OPEN");
        assertThat(refunded().get("refundWork", Document.class).getString("phase")).isEqualTo("RESERVATIONS");
        var timeline = first("payment_timelines");
        assertThat(apply(refund("r1", "tx", "1000", RefundEvidence.Source.REVENUECAT_API))).isEqualTo(APPLIED);
        assertThat(apply(refund("r2", "tx", "2000", RefundEvidence.Source.REVENUECAT_WEBHOOK))).isEqualTo(APPLIED);
        assertThat(apply(refund("r3", "tx", "2000.0", RefundEvidence.Source.REVENUECAT_API))).isEqualTo(APPLIED);
        var after = refunded().get("refund", Document.class);
        assertThat(after.get("effectId")).isEqualTo(effect.get("effectId"));
        assertThat(after.get("appliedAt")).isEqualTo(Date.from(NOW));
        assertThat(after.getLong("amountVersion")).isEqualTo(2);
        assertThat(first("payment_timelines")).isEqualTo(timeline);
        assertThat(effects("ACCESS_REVOKED")).isOne(); assertThat(effects("REFUND_AMOUNT_CONFIRMED")).isEqualTo(2);
        assertThat(refunded().get("money", Document.class).get("amount", org.bson.types.Decimal128.class).bigDecimalValue()).isEqualByComparingTo("9000");
        assertThat(pending()).isZero(); // Amount review remains visible without blocking access.
    }
    @Test void noDoubleSumForGoogleAndRcAndNoNegativeOrOverRefundCorrection() {
        enable(); apply(purchase("p", "tx"));
        assertThat(apply(refund("r", "tx", "1000", RefundEvidence.Source.GOOGLE_ORDERS))).isEqualTo(APPLIED);
        for (var o : List.of(refund("rc", "tx", "2000", RefundEvidence.Source.REVENUECAT_API),
                refund("lower", "tx", "500", RefundEvidence.Source.GOOGLE_ORDERS),
                refund("tooMuch", "tx", "10000", RefundEvidence.Source.GOOGLE_ORDERS))) {
            assertThat(apply(o)).isEqualTo(REVIEW_REQUIRED);
        }
        assertThat(effects("REFUND_AMOUNT_CONFIRMED")).isOne();
        assertThat(refunded().get("refund", Document.class).get("refundedTotal", org.bson.types.Decimal128.class).bigDecimalValue()).isEqualByComparingTo("1000");
    }
    @Test void middleRefundReflowsOnlyFutureSlotsAndAppendUsesNewEnd() {
        enable(); for (int i=0;i<3;i++) apply(purchase("p"+i, "tx"+i));
        var slots = db.getCollection("subscription_entitlements").find().sort(new Document("sequence",1)).into(new ArrayList<>());
        var active = slots.get(0); var future = slots.get(2);
        apply(refund("r", "tx1", "100", RefundEvidence.Source.REVENUECAT_API));
        assertThat(db.getCollection("subscription_entitlements").find(new Document("_id",active.get("_id"))).first()).isEqualTo(active);
        var shifted = db.getCollection("subscription_entitlements").find(new Document("_id",future.get("_id"))).first();
        assertThat(shifted.getDate("startsAt")).isEqualTo(active.getDate("endsAt"));
        assertThat(shifted.getDate("originalStartsAt")).isEqualTo(future.getDate("originalStartsAt"));
        assertThat(effects("TIMELINE_REFLOWED")).isOne();
        apply(purchase("p3","tx3"));
        assertThat(db.getCollection("subscription_entitlements").find(new Document("sequence",4L)).first().getDate("startsAt")).isEqualTo(shifted.getDate("endsAt"));
    }
    @Test void delayedRefundUsesAppliedAtAndNeverConsumesFutureSlotRetroactively() {
        enable(); apply(purchase("p0","tx0")); apply(purchase("p1","tx1"));
        var future = db.getCollection("subscription_entitlements").find(new Document("sequence",2L)).first();
        core = core(NOW.plusSeconds(4000)); apply(refund("r","tx0","100",RefundEvidence.Source.REVENUECAT_API));
        var shifted = db.getCollection("subscription_entitlements").find(new Document("sequence",2L)).first();
        assertThat(shifted.getDate("startsAt")).isEqualTo(Date.from(NOW.plusSeconds(4000)));
        assertThat(shifted.getDate("endsAt").getTime()-shifted.getDate("startsAt").getTime()).isEqualTo(86400000);
        assertThat(shifted.getDate("originalStartsAt")).isEqualTo(future.getDate("startsAt"));
    }
    @Test void refundAfterWithdrawalDoesNotReactivateAccount() {
        enable(); apply(purchase("p","tx")); withdraw();
        assertThat(apply(refund("r","tx","100",RefundEvidence.Source.REVENUECAT_API))).isEqualTo(APPLIED);
        assertThat(first("payment_accounts").getString("state")).isEqualTo("WITHDRAWN");
        assertThat(refunded().getString("accessState")).isEqualTo("REVOKED");
    }
    @Test void refundFirstNeverGrantsAndGoogleCannotCreatePurchase() {
        enable();
        assertThat(apply(refund("google","absent",null,RefundEvidence.Source.GOOGLE_ORDERS))).isEqualTo(REVIEW_REQUIRED);
        assertThat(count("purchases")).isZero();
        apply(refund("rc","tx",null,RefundEvidence.Source.REVENUECAT_API));
        assertThat(apply(purchase("p","tx"))).isEqualTo(REVIEW_REQUIRED);
        assertThat(count("subscription_entitlements")).isZero();
        assertThat(effects("ACCESS_REVOKED")).isOne();
    }
    @Test void duplicateAndConcurrentRefundUnknownCommitHaveOneEffect() throws Exception {
        enable(); apply(purchase("p","tx"));
        var a=refund("r1","tx","100",RefundEvidence.Source.REVENUECAT_API);
        var b=refund("r2","tx","100",RefundEvidence.Source.REVENUECAT_API);
        core.receive(a);core.receive(b);
        client.getDatabase("admin").runCommand(new Document("configureFailPoint","failCommand").append("mode",new Document("times",1))
                .append("data",new Document("failCommands",List.of("commitTransaction")).append("errorCode",1)
                        .append("errorLabels",List.of("UnknownTransactionCommitResult"))));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(()->core.apply(a.providerEventKey()));var y=pool.submit(()->core.apply(b.providerEventKey()));
            assertThat(x.get()).isEqualTo(APPLIED); assertThat(y.get()).isEqualTo(APPLIED);
        }
        assertThat(core.receive(a)).isEqualTo(APPLIED);
        assertThat(effects("ACCESS_REVOKED")).isOne();assertThat(effects("REFUND_AMOUNT_CONFIRMED")).isOne();
    }
    @Test void concurrentAppendAndRefundUseOneTimeline() throws Exception {
        enable(); apply(purchase("p","tx"));
        var r=refund("r","tx","100",RefundEvidence.Source.REVENUECAT_API);var p=purchase("new","new");
        core.receive(r);core.receive(p);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->core.apply(r.providerEventKey()));var b=pool.submit(()->core.apply(p.providerEventKey()));
            assertThat(a.get()).isEqualTo(APPLIED);assertThat(b.get()).isEqualTo(APPLIED);
        }
        var survivor=db.getCollection("subscription_entitlements").find(new Document("revokedAt",null)).first();
        assertThat(first("payment_timelines").getDate("timelineEnd")).isEqualTo(survivor.getDate("endsAt"));
        assertThat(effects("ACCESS_REVOKED")).isOne();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"purchases","subscription_entitlements","payment_timelines","payment_ledger","payment_purchase_cases"})
    void failureRollsBackRootLedgerTimelineAndWork(String collection) {
        enable();apply(purchase("p","tx"));apply(purchase("next","next"));
        var r=refund("r","tx",null,RefundEvidence.Source.REVENUECAT_API);core.receive(r);
        var before=db.getCollection("purchases").find().into(new ArrayList<>());
        var slots=db.getCollection("subscription_entitlements").find().into(new ArrayList<>());
        var timeline=first("payment_timelines");
        db.runCommand(new Document("collMod",collection).append("validator",new Document("impossible",new Document("$exists",true))));
        assertThatThrownBy(()->core.apply(r.providerEventKey())).isInstanceOf(PaymentException.class);
        assertThat(db.getCollection("purchases").find().into(new ArrayList<>())).isEqualTo(before);
        assertThat(db.getCollection("subscription_entitlements").find().into(new ArrayList<>())).isEqualTo(slots);
        assertThat(first("payment_timelines")).isEqualTo(timeline);
        assertThat(effects("ACCESS_REVOKED")).isZero();
    }
    @Test void legacyDigestRetainsOriginalReceiptAndNewEvidenceRequiresNewEvent() {
        enable();var old=observation("r","tx",ref,PurchaseObservation.Kind.REFUND,PaymentOffer.PREMIUM_1D,NOW.minusSeconds(100));
        core.receive(old);
        var enriched=new PurchaseObservation(old.environment(),old.store(),old.appId(),old.transactionKey(),old.lookupKeyVersion(),
                old.providerEventKey(),old.kind(),old.ownerRefId(),old.storeProductId(),old.purchasedAt(),old.money(),
                new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"a".repeat(64),NOW,null,null));
        assertThat(core.receive(enriched)).isEqualTo(RECEIVED);
        assertThat(first("payment_provider_inbox").getLong("digestVersion")).isEqualTo(1);
        assertThat(first("payment_provider_inbox").get("normalizedObservation",Document.class)).doesNotContainKey("refundEvidence");
        var fresh=refund("fresh","tx","100",RefundEvidence.Source.REVENUECAT_API);core.receive(fresh);
        assertThatThrownBy(()->core.receive(refund("fresh","tx","101",RefundEvidence.Source.REVENUECAT_API)))
                .isInstanceOfSatisfying(PaymentException.class,e->assertThat(e.code()).isEqualTo("EVENT_ID_CONFLICT"));
        var removed=new PurchaseObservation(fresh.environment(),fresh.store(),fresh.appId(),fresh.transactionKey(),fresh.lookupKeyVersion(),fresh.providerEventKey(),
                fresh.kind(),fresh.ownerRefId(),fresh.storeProductId(),fresh.purchasedAt(),fresh.money());
        assertThatThrownBy(()->core.receive(removed)).isInstanceOfSatisfying(PaymentException.class,e->assertThat(e.code()).isEqualTo("EVENT_ID_CONFLICT"));
    }
    @Test void migrationIsExplicitResumableAndRejectsDrift() {
        assertThatThrownBy(()->RefundCoreSchema.upgradeUnderMaintenance(db,true)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(()->RefundCoreSchema.upgradeUnderMaintenance(db,false)).isInstanceOf(IllegalStateException.class);
        // interrupted after validator, before index
        db.runCommand(new Document("collMod","purchases").append("validator",RefundCoreSchema.manifest().get("purchases").validator()));
        RefundCoreSchema.upgradeUnderMaintenance(db,true);RefundCoreSchema.upgradeUnderMaintenance(db,true);
        RefundCoreSchema.verify(db);PurchaseCoreSchema.verify(db);
        db.getCollection("purchases").dropIndex("ix_purchase_refund_due");
        db.getCollection("purchases").createIndex(new Document("_id",1).append("environment",1),new IndexOptions().name("ix_purchase_refund_due"));
        assertThatThrownBy(()->RefundCoreSchema.upgradeUnderMaintenance(db,true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void offKeepsExistingEffectsAndDoesNotApplyNewRefunds() {
        enable();apply(purchase("p","tx"));apply(refund("r","tx","100",RefundEvidence.Source.REVENUECAT_API));
        var before=refunded();var ledger=count("payment_ledger");
        properties.getRefund().setProcessingEnabled(false);core=core(NOW);
        assertThat(apply(refund("late","tx","200",RefundEvidence.Source.REVENUECAT_API))).isEqualTo(REVIEW_REQUIRED);
        assertThat(refunded()).isEqualTo(before);assertThat(count("payment_ledger")).isEqualTo(ledger);
    }
    @Test void schemaRejectsMixedUnknownMoneyAndNonLongVersion() {
        enable();apply(purchase("p","tx"));apply(refund("r","tx",null,RefundEvidence.Source.REVENUECAT_API));
        for (Document invalid : List.of(new Document("refund.currency","KRW"),new Document("refund.amountVersion",1),
                new Document("refundWork.leaseVersion",0L)))
            assertThatThrownBy(()->db.getCollection("purchases").updateOne(new Document(),new Document("$set",invalid))).isInstanceOf(com.mongodb.MongoException.class);
        assertThatThrownBy(()->db.getCollection("payment_provider_inbox").updateOne(new Document("digestVersion",2L),
                new Document("$set",new Document("normalizedObservation.refundEvidence.currency","KRW")))).isInstanceOf(com.mongodb.MongoException.class);
    }
    @Test void missingPurchaseTotalOrDifferentCurrencyCannotInventRefundAmount() {
        enable();var p=purchase("p","tx");
        apply(new PurchaseObservation(p.environment(),p.store(),p.appId(),p.transactionKey(),p.lookupKeyVersion(),p.providerEventKey(),p.kind(),
                ref,p.storeProductId(),p.purchasedAt(),null));
        assertThat(apply(refund("r","tx","100",RefundEvidence.Source.REVENUECAT_API))).isEqualTo(REVIEW_REQUIRED);
        assertThat(effects("REFUND_AMOUNT_CONFIRMED")).isZero();assertThat(refunded().getString("accessState")).isEqualTo("REVOKED");
        apply(purchase("p2","tx2"));var r=refund("r2","tx2","100",RefundEvidence.Source.REVENUECAT_API);
        var usd=new PurchaseObservation(r.environment(),r.store(),r.appId(),r.transactionKey(),r.lookupKeyVersion(),r.providerEventKey(),r.kind(),
                ref,r.storeProductId(),r.purchasedAt(),null,new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"b".repeat(64),null,BigDecimal.ONE,"USD"));
        assertThat(apply(usd)).isEqualTo(REVIEW_REQUIRED);assertThat(effects("REFUND_AMOUNT_CONFIRMED")).isZero();
    }
}
