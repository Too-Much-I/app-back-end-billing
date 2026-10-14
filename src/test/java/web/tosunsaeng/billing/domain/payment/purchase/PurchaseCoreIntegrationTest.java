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
class PurchaseCoreIntegrationTest {
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
    @ParameterizedTest @EnumSource(PaymentOffer.class)
    void allDurationsHistoricalCatalogAndDelayedStart(PaymentOffer offer) {
        var o = observation("event", "tx", ref, PurchaseObservation.Kind.PURCHASE, offer, NOW.minus(Duration.ofDays(40)));
        assertThat(core.receive(o)).isEqualTo(RECEIVED); assertThat(pending()).isEqualTo(1);
        assertThat(core.apply(o.providerEventKey())).isEqualTo(APPLIED);
        var entitlement = first("subscription_entitlements");
        assertThat(entitlement.getDate("startsAt").toInstant()).isEqualTo(o.purchasedAt());
        assertThat(entitlement.getDate("endsAt").toInstant()).isEqualTo(o.purchasedAt().plusSeconds(offer.durationSeconds));
        assertThat(entitlement.getLong("sequence")).isEqualTo(1L);
        assertThat(first("purchases").get("productSnapshot", Document.class).getLong("catalogVersion")).isEqualTo(1L);
        assertThat(pending()).isZero(); assertThat(count("payment_ledger")).isEqualTo(2);
        assertThat(first("purchases").getDate("retentionExpiresAt").toInstant()).isEqualTo(NOW.atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
    }
    @Test void duplicateSourcesAndTransactionsGrantOnlyOnce() {
        var a = purchase("event1", "tx"); var b = purchase("event2", "tx");
        core.receive(a); core.receive(a); core.receive(b); assertThat(pending()).isEqualTo(2);
        core.apply(a.providerEventKey()); core.apply(a.providerEventKey()); core.apply(b.providerEventKey());
        assertThat(core.receive(a)).isEqualTo(APPLIED);
        assertThat(pending()).isZero(); assertThat(count("purchases")).isEqualTo(1);
        assertThat(count("subscription_entitlements")).isEqualTo(1); assertThat(count("payment_ledger")).isEqualTo(2);
    }
    PurchaseObservation withoutMoney(PurchaseObservation o, String event) {
        return new PurchaseObservation(o.environment(), o.store(), o.appId(), o.transactionKey(), o.lookupKeyVersion(),
                lookup.event("SANDBOX", "fake-project", event), o.kind(), o.ownerRefId(), o.storeProductId(), o.purchasedAt(), null);
    }
    @Test void unknownMoneyGrantsThenSupplementDoesNotChangePeriod() {
        var known = purchase("known", "tx"); var unknown = withoutMoney(known, "unknown");
        core.receive(unknown); core.apply(unknown.providerEventKey());
        Document entitlement = first("subscription_entitlements"), timeline = first("payment_timelines");
        assertThat(first("purchases").get("money")).isNull(); assertThat(first("purchases").getString("accessState")).isEqualTo("ALLOWED");
        core.receive(known); core.apply(known.providerEventKey()); core.apply(known.providerEventKey());
        assertThat(first("purchases").get("money")).isNotNull(); assertThat(first("subscription_entitlements")).isEqualTo(entitlement);
        assertThat(first("payment_timelines")).isEqualTo(timeline); assertThat(count("payment_ledger")).isEqualTo(3); assertThat(pending()).isZero();
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("type", "MONEY_CONFIRMED"))).isEqualTo(1);
    }
    @Test void laterUnknownMoneyNeverErasesKnownOrCreatesCase() {
        var known = purchase("known", "tx"); core.receive(known); core.apply(known.providerEventKey());
        Document money = first("purchases").get("money", Document.class);
        var unknown = withoutMoney(known, "unknown"); core.receive(unknown); core.apply(unknown.providerEventKey());
        assertThat(first("purchases").get("money")).isEqualTo(money); assertThat(count("payment_purchase_cases")).isZero();
        assertThat(count("payment_ledger")).isEqualTo(2); assertThat(pending()).isZero();
    }
    @Test void confirmedMoneyConflictIsReviewWithoutOverwriting() {
        var known = purchase("a", "tx"); core.receive(known); core.apply(known.providerEventKey());
        Document money = first("purchases").get("money", Document.class);
        var changed = new PurchaseObservation(known.environment(), known.store(), known.appId(), known.transactionKey(), known.lookupKeyVersion(),
                lookup.event("SANDBOX", "fake-project", "b"), known.kind(), ref, known.storeProductId(), known.purchasedAt(), new PurchaseObservation.Money(BigDecimal.ONE, "KRW"));
        core.receive(changed); assertThat(core.apply(changed.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(first("purchases").get("money")).isEqualTo(money); assertThat(count("subscription_entitlements")).isEqualTo(1);
    }
    @Test void concurrentMoneySupplementHasOneEffect() throws Exception {
        var a = purchase("a", "tx"); var b = purchase("b", "tx"); var unknown = withoutMoney(a, "unknown");
        core.receive(unknown); core.apply(unknown.providerEventKey()); core.receive(a); core.receive(b);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(() -> core.apply(a.providerEventKey())); var y = pool.submit(() -> core.apply(b.providerEventKey()));
            assertThat(x.get(10, TimeUnit.SECONDS)).isEqualTo(APPLIED); assertThat(y.get(10, TimeUnit.SECONDS)).isEqualTo(APPLIED);
        }
        assertThat(count("payment_ledger")).isEqualTo(3); assertThat(count("subscription_entitlements")).isEqualTo(1); assertThat(pending()).isZero();
    }
    @Test void lateMoneySupplementExtendsAuditRetentionButNotEntitlement() {
        var known = purchase("known", "tx"); var unknown = withoutMoney(known, "unknown"); core.receive(unknown); core.apply(unknown.providerEventKey());
        Document entitlement = first("subscription_entitlements"); Instant later = NOW.plus(Duration.ofDays(100));
        var delayed = core(later); delayed.receive(known); delayed.apply(known.providerEventKey());
        assertThat(first("purchases").getDate("retentionExpiresAt").toInstant()).isEqualTo(later.atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
        assertThat(first("subscription_entitlements")).isEqualTo(entitlement);
    }
    @Test void moneySupplementWhileBlockedThenGrantUsesUpdatedVersion() {
        barrier("PURCHASE_BLOCKED"); var known = purchase("known", "tx"); var unknown = withoutMoney(known, "unknown");
        core.receive(unknown); assertThat(core.apply(unknown.providerEventKey())).isEqualTo(PURCHASE_BLOCKED);
        core.receive(known); assertThat(core.apply(known.providerEventKey())).isEqualTo(PURCHASE_BLOCKED);
        barrier("OPEN"); assertThat(core.apply(known.providerEventKey())).isEqualTo(APPLIED); core.apply(unknown.providerEventKey());
        assertThat(count("payment_ledger")).isEqualTo(3); assertThat(pending()).isZero();
    }
    @Test void schemaR1NeedsExplicitIsolatedUpgradeAndIsRetrySafe() {
        db.getCollection("payment_provider_inbox").dropIndex("ix_payment_provider_work_due");
        PurchaseCoreSchema.manifest(false).forEach((name, d) -> db.runCommand(new Document("collMod", name).append("validator", d.validator())));
        assertThatThrownBy(() -> PurchaseCoreSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeMoneyEvidenceUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeMoneyEvidenceUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        PurchaseCoreSchema.upgradeMoneyEvidenceUnderMaintenance(db, true); PurchaseCoreSchema.upgradeMoneyEvidenceUnderMaintenance(db, true);
        assertThatThrownBy(() -> PurchaseCoreSchema.verify(db)).isInstanceOf(IllegalStateException.class);
        PurchaseCoreSchema.upgradeWebhookUnderMaintenance(db, true);
        PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true);
        PurchaseCoreSchema.verify(db); assertThat(count("purchases")).isZero();
    }
    WebhookReceipt webhook(String event, WebhookReceipt.Disposition disposition) {
        var o = purchase(event, "transaction-" + event);
        return new WebhookReceipt("SANDBOX", o.providerEventKey(), "a".repeat(64), lookup.version(),
                disposition == WebhookReceipt.Disposition.PURCHASE ? o : null, ref, disposition, "TEST_REASON");
    }
    @Test void webhookReceiptDoesNotGrantAndWorkerLaterAppliesOnce() {
        var r = webhook("webhook", WebhookReceipt.Disposition.PURCHASE);
        assertThat(core.receiveWebhook(r)).isEqualTo(RECEIVED); assertThat(core.receiveWebhook(r)).isEqualTo(RECEIVED);
        assertThat(count("purchases")).isZero(); assertThat(pending()).isEqualTo(1);
        assertThat(core.apply(r.eventKey())).isEqualTo(APPLIED); assertThat(core.receiveWebhook(r)).isEqualTo(APPLIED);
        assertThat(count("subscription_entitlements")).isEqualTo(1); assertThat(pending()).isZero();
        var conflict = new WebhookReceipt(r.environment(), r.eventKey(), "b".repeat(64), r.lookupKeyVersion(), r.observation(), r.ownerRefId(), r.disposition(), r.reason());
        assertThatThrownBy(() -> core.receiveWebhook(conflict)).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(409));
    }
    @Test void ignoredWebhookIsDurableHasTtlAndNeverTouchesAccount() {
        var r = webhook("ignored", WebhookReceipt.Disposition.IGNORED);
        assertThat(core.receiveWebhook(r)).isEqualTo(IGNORED); assertThat(core.receiveWebhook(r)).isEqualTo(IGNORED);
        assertThat(core.apply(r.eventKey())).isEqualTo(IGNORED); assertThat(pending()).isZero();
        assertThat(first("payment_provider_inbox").get("normalizedObservation")).isNull();
        assertThat(first("payment_provider_inbox").getDate("purgeAt").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(120)));
        assertThat(count("payment_purchase_cases")).isZero(); assertThat(count("purchases")).isZero();
        assertThatThrownBy(() -> new PurchaseCore(store, properties, Clock.fixed(NOW, ZoneOffset.UTC),
                Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), "different-version")).isInstanceOf(PaymentException.class);
    }
    @Test void reviewWebhookPreservesPendingInCaseAfterInboxRemoval() {
        var r = webhook("review", WebhookReceipt.Disposition.REVIEW);
        assertThat(core.receiveWebhook(r)).isEqualTo(REVIEW_REQUIRED); assertThat(pending()).isEqualTo(1);
        assertThat(first("payment_purchase_cases").containsKey("purgeAt")).isFalse();
        db.getCollection("payment_provider_inbox").deleteMany(new Document()); // Simulated source TTL in isolated test database.
        assertThat(core.receiveWebhook(r)).isEqualTo(REVIEW_REQUIRED); assertThat(pending()).isEqualTo(1);
        assertThat(count("payment_purchase_cases")).isEqualTo(1); assertThat(count("purchases")).isZero();
    }
    @Test void withdrawalAndReviewWebhookRetainFinancialPendingWithoutGrant() {
        withdraw(); var r = webhook("withdrawn", WebhookReceipt.Disposition.PURCHASE); core.receiveWebhook(r);
        assertThat(core.apply(r.eventKey())).isEqualTo(REVIEW_REQUIRED); assertThat(count("subscription_entitlements")).isZero();
        assertThat(pending()).isEqualTo(1);
    }
    @Test void webhookConcurrentDuplicateAndUnknownCommitConverge() throws Exception {
        var r = webhook("concurrent-webhook", WebhookReceipt.Disposition.REVIEW);
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of("commitTransaction")).append("errorCode", 1)
                        .append("errorLabels", List.of("UnknownTransactionCommitResult"))));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> core.receiveWebhook(r)); var b = pool.submit(() -> core.receiveWebhook(r));
            assertThat(a.get()).isEqualTo(REVIEW_REQUIRED); assertThat(b.get()).isEqualTo(REVIEW_REQUIRED);
        }
        assertThat(count("payment_provider_inbox")).isEqualTo(1); assertThat(count("payment_purchase_cases")).isEqualTo(1);
        assertThat(pending()).isEqualTo(1);
    }
    @Test void webhookSchemaMigrationResumesPartialAndRejectsUnisolatedOrDrift() {
        db.getCollection("payment_provider_inbox").dropIndex("ix_payment_provider_work_due");
        PurchaseCoreSchema.manifest(true).forEach((name, d) -> db.runCommand(new Document("collMod", name).append("validator", d.validator())));
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeWebhookUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeWebhookUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        db.runCommand(new Document("collMod", "payment_provider_inbox").append("validator", PurchaseCoreSchema.manifest(true, true).get("payment_provider_inbox").validator()));
        PurchaseCoreSchema.upgradeWebhookUnderMaintenance(db, true); PurchaseCoreSchema.upgradeWebhookUnderMaintenance(db, true);
        PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true);
        PurchaseCoreSchema.verify(db); barrier("OPEN");
        var r = webhook("invalid-source", WebhookReceipt.Disposition.IGNORED); core.receiveWebhook(r);
        assertThatThrownBy(() -> db.getCollection("payment_provider_inbox").updateOne(new Document(),
                new Document("$set", new Document("state", "RECEIVED")).append("$unset", new Document("purgeAt", ""))))
                .isInstanceOf(com.mongodb.MongoException.class);
    }
    @Test void workerSchemaMigrationResumesBothDdlBoundariesWithoutChangingData() {
        var o = purchase("legacy-r3", "tx"); core.receive(o);
        var inbox = db.getCollection("payment_provider_inbox");
        var before = inbox.find().first();
        inbox.dropIndex("ix_payment_provider_work_due");
        db.runCommand(new Document("collMod", "payment_provider_inbox").append("validator", PurchaseCoreSchema.manifest(true, true).get("payment_provider_inbox").validator()));
        assertThatThrownBy(() -> core(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        // Simulated interruption after collMod but before createIndex.
        db.runCommand(new Document("collMod", "payment_provider_inbox").append("validator", PurchaseCoreSchema.manifest().get("payment_provider_inbox").validator()));
        PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true); PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true);
        assertThat(inbox.find().first()).isEqualTo(before); PurchaseCoreSchema.verify(db);
        // Opposite exact intermediate is accepted, unknown metadata drift is not.
        db.runCommand(new Document("collMod", "payment_provider_inbox").append("validator", PurchaseCoreSchema.manifest(true, true).get("payment_provider_inbox").validator()));
        PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true);
        inbox.createIndex(new Document("unexpected", 1), new IndexOptions().name("unexpected"));
        assertThatThrownBy(() -> PurchaseCoreSchema.upgradeWorkerUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void moneySupplementUnknownCommitAndTransientFailureConverge() {
        for (String label : List.of("TransientTransactionError", "UnknownTransactionCommitResult")) {
            var known = purchase(label, label); var unknown = withoutMoney(known, "unknown-" + label);
            core.receive(unknown); core.apply(unknown.providerEventKey()); core.receive(known);
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                    .append("data", new Document("failCommands", List.of(label.equals("TransientTransactionError") ? "insert" : "commitTransaction"))
                            .append("errorCode", label.equals("TransientTransactionError") ? 112 : 1).append("errorLabels", List.of(label))));
            assertThat(core.apply(known.providerEventKey())).isEqualTo(APPLIED); core.apply(known.providerEventKey());
        }
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("type", "MONEY_CONFIRMED"))).isEqualTo(2);
        assertThat(count("subscription_entitlements")).isEqualTo(2); assertThat(pending()).isZero();
    }
    @Test void conflictingEventNeverMutatesPending() {
        core.receive(purchase("event", "tx"));
        assertThatThrownBy(() -> core.receive(purchase("event", "other"))).isInstanceOfSatisfying(PaymentException.class,
                e -> assertThat(e.code()).isEqualTo("EVENT_ID_CONFLICT"));
        assertThat(pending()).isEqualTo(1); assertThat(count("payment_provider_inbox")).isEqualTo(1);
    }
    @Test void timelineUsesApplyCommitOrderNotPurchaseChronology() {
        var first = purchase("newer", "newer");
        var older = observation("older", "older", ref, PurchaseObservation.Kind.PURCHASE, PaymentOffer.PREMIUM_3D, NOW.minusSeconds(1000));
        core.receive(older); core.receive(first); core.apply(first.providerEventKey()); core.apply(older.providerEventKey());
        var rows = db.getCollection("subscription_entitlements").find().sort(new Document("sequence", 1)).into(new ArrayList<>());
        assertThat(rows.get(1).getDate("startsAt")).isEqualTo(rows.get(0).getDate("endsAt"));
        assertThat(rows.get(1).getLong("durationSeconds")).isEqualTo(PaymentOffer.PREMIUM_3D.durationSeconds);
        assertThat(first("payment_timelines").getLong("nextSequence")).isEqualTo(3L);
    }
    @Test void purchaseBlockedRecordsFactsAndLaterGrantsWithOriginalTime() {
        var o = purchase("event", "tx"); barrier("PURCHASE_BLOCKED");
        core.receive(o); assertThat(core.apply(o.providerEventKey())).isEqualTo(PURCHASE_BLOCKED);
        assertThat(count("purchases")).isEqualTo(1); assertThat(count("subscription_entitlements")).isZero(); assertThat(pending()).isEqualTo(1);
        barrier("OPEN"); assertThat(core.apply(o.providerEventKey())).isEqualTo(APPLIED);
        assertThat(first("subscription_entitlements").getDate("startsAt").toInstant()).isEqualTo(o.purchasedAt());
    }
    @Test void withdrawBeforeApplyKeepsFinanceAndTransfersPendingToCase() {
        var o = purchase("event", "tx"); core.receive(o); withdraw();
        assertThat(core.apply(o.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(first("purchases").getString("accessState")).isEqualTo("NOT_GRANTED");
        assertThat(first("payment_provider_inbox").getBoolean("counted")).isFalse();
        assertThat(first("payment_purchase_cases").getBoolean("counted")).isTrue();
        assertThat(pending()).isEqualTo(1); assertThat(count("subscription_entitlements")).isZero();
    }
    @Test void duplicateAppliedPurchaseAfterWithdrawalNeverRegrants() {
        var a = purchase("a", "tx"); core.receive(a); core.apply(a.providerEventKey()); withdraw();
        var b = purchase("b", "tx"); core.receive(b); assertThat(core.apply(b.providerEventKey())).isEqualTo(APPLIED);
        assertThat(count("subscription_entitlements")).isEqualTo(1); assertThat(pending()).isZero();
    }
    @Test void withdrawnBeforeReceiveAndInactiveAliasNeverGrant() {
        withdraw(); var o = purchase("event", "tx"); core.receive(o); core.apply(o.providerEventKey());
        assertThat(count("purchases")).isEqualTo(1); assertThat(count("subscription_entitlements")).isZero();
        assertThat(first("payment_accounts").getString("state")).isEqualTo("WITHDRAWN");
    }
    @Test void missingOwnerIsQuarantinedWithoutCreatingAccountOrLifecycle() {
        var o = observation("event", "tx", UUID.randomUUID().toString(), PurchaseObservation.Kind.PURCHASE, PaymentOffer.PREMIUM_1D, NOW);
        core.receive(o); core.apply(o.providerEventKey());
        assertThat(count("payment_accounts")).isEqualTo(1); assertThat(count("payment_account_lifecycles")).isEqualTo(1);
        assertThat(count("purchases")).isZero(); assertThat(pending()).isZero();
        assertThat(first("payment_purchase_cases").getBoolean("counted")).isFalse();
    }
    @Test void firstRefundFencesLaterPurchaseAndUnmappedRefundAlsoFences() {
        var r = observation("refund", "tx", ref, PurchaseObservation.Kind.REFUND, PaymentOffer.PREMIUM_1D, NOW.minusSeconds(100));
        core.receive(r); core.apply(r.providerEventKey());
        var o = purchase("event", "tx"); core.receive(o); core.apply(o.providerEventKey());
        assertThat(count("subscription_entitlements")).isZero(); assertThat(first("purchases").getString("accessState")).isEqualTo("REVOKED");
        var unknown = observation("unknown-refund", "tx2", null, PurchaseObservation.Kind.REVOKE, PaymentOffer.PREMIUM_1D, NOW);
        core.receive(unknown); core.apply(unknown.providerEventKey());
        var later = purchase("later", "tx2"); core.receive(later); core.apply(later.providerEventKey());
        assertThat(count("subscription_entitlements")).isZero();
    }
    @Test void refundAfterGrantIsOnlyHandoffUntilPlan012() {
        var o = purchase("event", "tx"); core.receive(o); core.apply(o.providerEventKey());
        Document before = first("subscription_entitlements");
        var r = observation("refund", "tx", ref, PurchaseObservation.Kind.REFUND, PaymentOffer.PREMIUM_1D, NOW.minusSeconds(100));
        core.receive(r); assertThat(core.apply(r.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(first("subscription_entitlements")).isEqualTo(before);
        assertThat(first("payment_purchase_cases").getString("reason")).isEqualTo("REVERSAL_REQUIRES_PLAN_012");
    }
    @Test void immutableTimeConflictLeavesOriginalEntitlement() {
        var o = purchase("event", "tx"); core.receive(o); core.apply(o.providerEventKey());
        var conflict = observation("conflict", "tx", ref, PurchaseObservation.Kind.PURCHASE, PaymentOffer.PREMIUM_1D, NOW.minusSeconds(99));
        core.receive(conflict); assertThat(core.apply(conflict.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(count("subscription_entitlements")).isEqualTo(1); assertThat(pending()).isEqualTo(1);
    }
    @Test void ageHandoffSurvivesInboxRemovalAndKeepsPending() {
        var o = purchase("event", "tx"); core.receive(o);
        assertThatThrownBy(() -> core.handoffAged(o.providerEventKey())).isInstanceOf(PaymentException.class);
        assertThat(core(NOW.plus(Duration.ofDays(119))).handoffAged(o.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(pending()).isEqualTo(1);
        db.getCollection("payment_provider_inbox").deleteMany(new Document()); // Simulated post-handoff TTL in disposable DB.
        assertThat(core.receive(o)).isEqualTo(REVIEW_REQUIRED); assertThat(core.apply(o.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(count("payment_purchase_cases")).isEqualTo(1); assertThat(pending()).isEqualTo(1);
    }
    @Test void allBlockedMissingFenceAndStaleEpochFailClosed() {
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> core.receive(purchase("event", "tx"))).isInstanceOf(PaymentException.class);
        assertThat(count("payment_provider_inbox")).isZero(); assertThat(pending()).isZero();
        barrier("OPEN"); var fence = new PaymentRecoveryFence(store, properties); var old = fence.capture();
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("epoch", UUID.randomUUID().toString())));
        assertThatThrownBy(() -> store.transaction(s -> fence.acquire(s, old, PaymentRecoveryFence.Writer.PAYMENT_APPLY))).isInstanceOf(PaymentException.class);
        properties.getRecovery().setFenceRequired(false);
        assertThatThrownBy(() -> core(NOW)).isInstanceOf(PaymentException.class);
    }
    @Test void overflowAndRejectedLedgerRollBackAllEffects() {
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$set", new Document("pendingEvidence", Long.MAX_VALUE)));
        assertThatThrownBy(() -> core.receive(purchase("event", "tx"))).isInstanceOf(PaymentException.class);
        assertThat(count("payment_provider_inbox")).isZero();
        db.getCollection("payment_account_lifecycles").updateOne(new Document(), new Document("$set", new Document("pendingEvidence", 0L)));
        var o = purchase("event", "tx"); core.receive(o);
        db.runCommand(new Document("collMod", "payment_ledger").append("validator", new Document("impossible", new Document("$exists", true))));
        assertThatThrownBy(() -> core.apply(o.providerEventKey())).isInstanceOf(PaymentException.class);
        assertThat(count("purchases")).isZero(); assertThat(count("subscription_entitlements")).isZero();
        assertThat(count("payment_timelines")).isZero(); assertThat(pending()).isEqualTo(1);
    }
    @Test void parallelDistinctTransactionsAndDuplicateDeliveryConverge() throws Exception {
        List<Callable<PurchaseCore.Outcome>> receives = new ArrayList<>();
        List<PurchaseObservation> inputs = new ArrayList<>();
        for (int i = 0; i < 6; i++) { var o = purchase("event" + i, "tx" + i); inputs.add(o); receives.add(() -> core.receive(o)); receives.add(() -> core.receive(o)); }
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(receives)) result.get();
            List<Callable<PurchaseCore.Outcome>> applies = new ArrayList<>();
            for (var o : inputs) { applies.add(() -> core.apply(o.providerEventKey())); applies.add(() -> core.apply(o.providerEventKey())); }
            for (var result : pool.invokeAll(applies)) assertThat(result.get()).isEqualTo(APPLIED);
        }
        assertThat(count("purchases")).isEqualTo(6); assertThat(count("subscription_entitlements")).isEqualTo(6);
        assertThat(first("payment_timelines").getLong("nextSequence")).isEqualTo(7); assertThat(pending()).isZero();
    }
    @Test void unknownCommitAndTransientFailureConverge() {
        for (String label : List.of("TransientTransactionError", "UnknownTransactionCommitResult")) {
            var o = purchase(label, label); core.receive(o);
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                    .append("data", new Document("failCommands", List.of(label.equals("TransientTransactionError") ? "insert" : "commitTransaction"))
                            .append("errorCode", label.equals("TransientTransactionError") ? 112 : 1).append("errorLabels", List.of(label))));
            assertThat(core.apply(o.providerEventKey())).isEqualTo(APPLIED);
            assertThat(core.apply(o.providerEventKey())).isEqualTo(APPLIED);
        }
        assertThat(count("subscription_entitlements")).isEqualTo(2); assertThat(pending()).isZero();
    }
    @Test void schemaDriftNeverAutoRepairs() {
        assertThatThrownBy(() -> PurchaseCoreSchema.installUnderMaintenance(db, false)).isInstanceOf(IllegalStateException.class);
        var purchases = db.getCollection("purchases");
        purchases.dropIndex("ux_purchase_transaction");
        purchases.createIndex(new Document("environment", 1).append("store", 1).append("appId", 1).append("transactionKey", 1),
                new IndexOptions().name("ux_purchase_transaction").unique(false));
        assertThatThrownBy(() -> core(NOW)).isInstanceOf(IllegalStateException.class);
        barrier("ALL_BLOCKED");
        assertThatThrownBy(() -> PurchaseCoreSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void schemaRejectsIntVersionAndUnsafeTtlOnUnresolvedSource() {
        var o = purchase("event", "tx"); core.receive(o);
        assertThatThrownBy(() -> db.getCollection("payment_provider_inbox").updateOne(new Document(), new Document("$set", new Document("version", 2))))
                .isInstanceOf(com.mongodb.MongoException.class);
        assertThatThrownBy(() -> db.getCollection("payment_provider_inbox").updateOne(new Document(), new Document("$set", new Document("purgeAt", Date.from(NOW)))))
                .isInstanceOf(com.mongodb.MongoException.class);
    }
    @Test void foreignAccountTransactionConflictNeverTransfersEntitlement() {
        var o = purchase("event", "tx"); core.receive(o); core.apply(o.providerEventKey());
        String other = new PaymentAccountService(store, properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .getOrCreate(UUID.randomUUID().toString(), NOW.plusSeconds(60)).purchaseAccountRefId();
        var stolen = observation("other", "tx", other, PurchaseObservation.Kind.PURCHASE, PaymentOffer.PREMIUM_1D, o.purchasedAt());
        core.receive(stolen); assertThat(core.apply(stolen.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(count("subscription_entitlements")).isEqualTo(1);
        assertThat(first("purchases").getString("ownerRefId")).isEqualTo(ref);
        assertThat(first("payment_purchase_cases").getString("reason")).isEqualTo("OWNER_CONFLICT");
    }
    @Test void unknownAmountIsNotFilledWithCatalogPrice() {
        var p = purchase("event", "tx");
        var o = new PurchaseObservation(p.environment(), p.store(), p.appId(), p.transactionKey(), p.lookupKeyVersion(), p.providerEventKey(),
                p.kind(), p.ownerRefId(), p.storeProductId(), p.purchasedAt(), null);
        core.receive(o); assertThat(core.apply(o.providerEventKey())).isEqualTo(APPLIED);
        assertThat(first("purchases")).containsEntry("money", null);
    }
    @Test void missingCatalogOrFutureTimeAreReviewNotInventedPurchases() {
        var future = observation("future", "tx", ref, PurchaseObservation.Kind.PURCHASE, PaymentOffer.PREMIUM_1D, NOW.plusSeconds(1));
        core.receive(future); assertThat(core.apply(future.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        db.getCollection("payment_products").deleteMany(new Document());
        var missing = purchase("missing", "tx2"); core.receive(missing); core.apply(missing.providerEventKey());
        assertThat(count("purchases")).isZero(); assertThat(count("payment_purchase_cases")).isEqualTo(2); assertThat(pending()).isEqualTo(2);
    }
    @Test void activeAccountWithInactiveAliasDoesNotGrant() {
        db.getCollection("purchase_account_refs").updateOne(new Document("_id", ref), new Document("$set", new Document("active", false)));
        var o = purchase("event", "tx"); core.receive(o); assertThat(core.apply(o.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
        assertThat(count("purchases")).isEqualTo(1); assertThat(count("subscription_entitlements")).isZero();
    }
    @Test void inputEnvironmentApplicationAndKeyVersionAreNotTrusted() {
        var p = purchase("event", "tx");
        for (var o : List.of(
                new PurchaseObservation("PRODUCTION", p.store(), p.appId(), p.transactionKey(), p.lookupKeyVersion(), p.providerEventKey(), p.kind(), ref, p.storeProductId(), NOW, null),
                new PurchaseObservation(p.environment(), p.store(), "other.app", p.transactionKey(), p.lookupKeyVersion(), p.providerEventKey(), p.kind(), ref, p.storeProductId(), NOW, null),
                new PurchaseObservation(p.environment(), p.store(), p.appId(), p.transactionKey(), "fake-v2", p.providerEventKey(), p.kind(), ref, p.storeProductId(), NOW, null)))
            assertThatThrownBy(() -> core.receive(o)).isInstanceOf(PaymentException.class);
        assertThat(count("payment_provider_inbox")).isZero();
        core.receive(p);
        assertThatThrownBy(() -> new PurchaseCore(store, properties, Clock.fixed(NOW, ZoneOffset.UTC),
                Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), "fake-v2")).isInstanceOf(PaymentException.class);
    }
    @Test void timelineOverflowRollsBackFinancialAndInputChanges() {
        String account = first("payment_accounts").getString("_id");
        db.getCollection("payment_timelines").insertOne(new Document("_id", account).append("accountId", account).append("environment", "SANDBOX")
                .append("nextSequence", Long.MAX_VALUE).append("timelineEnd", Date.from(NOW)).append("version", 1L));
        var o = purchase("event", "tx"); core.receive(o);
        assertThatThrownBy(() -> core.apply(o.providerEventKey())).isInstanceOf(PaymentException.class);
        assertThat(count("purchases")).isZero(); assertThat(count("payment_ledger")).isZero(); assertThat(pending()).isEqualTo(1);
    }
    @Test void concurrentWithdrawalAndGrantConvergeByCommitOrder() throws Exception {
        var o = purchase("event", "tx"); core.receive(o);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return core.apply(o.providerEventKey()); });
            var b = pool.submit(() -> { start.await(); withdraw(); return true; });
            start.countDown(); assertThat(b.get()).isTrue();
            if (a.get() == APPLIED) { assertThat(count("subscription_entitlements")).isEqualTo(1); assertThat(pending()).isZero(); }
            else { assertThat(count("subscription_entitlements")).isZero(); assertThat(pending()).isEqualTo(1); }
        }
        assertThat(first("payment_accounts").getString("state")).isEqualTo("WITHDRAWN");
        var late = purchase("late", "new-transaction"); core.receive(late);
        assertThat(core.apply(late.providerEventKey())).isEqualTo(REVIEW_REQUIRED);
    }
    @Test void schemaRejectsUnknownFieldsAndBadIndexOrder() {
        var o = purchase("event", "tx"); core.receive(o);
        assertThatThrownBy(() -> db.getCollection("payment_provider_inbox").updateOne(new Document(), new Document("$set", new Document("rawReceipt", "fake"))))
                .isInstanceOf(com.mongodb.MongoException.class);
        db.getCollection("purchases").dropIndex("ux_purchase_transaction");
        db.getCollection("purchases").createIndex(new Document("store", 1).append("environment", 1).append("appId", 1).append("transactionKey", 1),
                new IndexOptions().name("ux_purchase_transaction").unique(true));
        assertThatThrownBy(() -> core(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
