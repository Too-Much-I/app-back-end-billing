package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import static org.assertj.core.api.Assertions.*;

class RefundFanoutIntegrationTest extends PaidReservationTestSupport {
    String refund() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        RefundCoreSchema.upgradeUnderMaintenance(db, true); RefundFanoutSchema.installUnderMaintenance(db, true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "OPEN")));
        properties.getRefund().setProcessingEnabled(true);
        var core = new PurchaseCore(payment, properties, clock, Map.of(PurchaseObservation.Store.PLAY_STORE, "fake.app"), "fake-v1");
        var p = db.getCollection("purchases").find().first();
        var observation = new PurchaseObservation("SANDBOX", PurchaseObservation.Store.PLAY_STORE, "fake.app", p.getString("transactionKey"), "fake-v1",
                "f".repeat(64), PurchaseObservation.Kind.REFUND, account().getString("activeRefId"), "PREMIUM_1D", NOW, null,
                new RefundEvidence(RefundEvidence.Source.REVENUECAT_API, "d".repeat(64), NOW, null, null));
        core.receive(observation); core.apply(observation.providerEventKey());
        return p.getString("_id");
    }
    RefundFanout fanout() { return fanout(clock); }
    RefundFanout fanout(Clock time) { return new RefundFanout(tx, mongo, runtime(time), payment, properties, "test", "scope", time); }
    Document row(String name, String id) { return db.getCollection(name).find(new Document("_id", id)).first(); }
    String confirmed() {
        purchase(); var first = runtime().reserve(command()); runtime().confirm(confirm(command(), first));
        return first.responseSnapshot().getString("attemptGroupId");
    }
    ReserveCommand restart(String group) {
        var context = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var request = new ReserveRequest(USER, "second-session", "mock", null, null, null, context);
        return new ReserveCommand(UUID.randomUUID().toString(), USER, "second-session", "mock", null, null, null, new ReservePayloadHasher().hash(request), context);
    }
    @Test void initialReservationWithoutGroupIsCanceledAndConfirmReplaysRealRejection() {
        purchase(); var cmd = command(); var reserved = runtime().reserve(cmd); String p = refund();
        fanout().tick();
        var status = service.status(USER, OP).orElseThrow();
        assertThat(status.reservationStatus()).isEqualTo(Reservation.Status.CANCELED);
        assertThat(status.confirmRejection()).isNotNull();
        assertThat(db.getCollection("attempt_groups").countDocuments()).isZero();
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isZero();
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments(new Document("commandType", "CONFIRM"))).isZero();
        error(() -> runtime().confirm(confirm(cmd, reserved)), "ENTITLEMENT_REVOKED");
        error(() -> runtime().confirm(confirm(cmd, reserved)), "ENTITLEMENT_REVOKED");
        assertThat(db.getCollection(PaidExamStore.RECEIPTS).countDocuments(new Document("commandType", "CONFIRM"))).isOne();
        assertThat(row("purchases", p).get("refundWork", Document.class).getString("phase")).isEqualTo("DONE");
    }
    @Test void pendingReplacementCanceledButConfirmedSessionAndCurrentGuardSurvive() {
        String group = confirmed(); var cmd = restart(group); var reserved = runtime().reserve(cmd); refund();
        var first = row("attempt_sessions", "session");
        fanout().tick();
        assertThat(row("attempt_sessions", "session")).isEqualTo(first);
        assertThat(row("attempt_sessions", "second-session").getString("state")).isEqualTo("FAILED");
        Document guard = db.getCollection(PaidExamStore.GUARDS).find().first();
        assertThat(guard.getString("currentSessionId")).isEqualTo("session");
        assertThat(guard.get("pendingOperationId")).isNull();
        error(() -> runtime().confirm(confirm(cmd, reserved)), "ENTITLEMENT_REVOKED");
        Document body = Document.parse(db.getCollection(RefundFanout.OUTBOX).find().first().getString("body"));
        assertThat(body.getString("confirmedSessionId")).isEqualTo("session");
    }
    @Test void lastSuccessfulConfirmIsDurableAndOutboxIsImmutableWithoutCommand() {
        String group = confirmed(); var cmd = restart(group); var reserved = runtime().reserve(cmd); runtime().confirm(confirm(cmd, reserved));
        assertThat(row("attempt_groups", group).getString("lastConfirmedSessionId")).isEqualTo("second-session");
        String p = refund();
        // Disposable DB: emulate command TTL. Durable confirm/Session evidence must suffice.
        db.getCollection("idempotency_commands").deleteMany(new Document());
        var before = row("attempt_sessions", "session"); var second = row("attempt_sessions", "second-session");
        fanout().tick(); Document outbox = db.getCollection(RefundFanout.OUTBOX).find().first();
        fanout().tick(); assertThat(db.getCollection(RefundFanout.OUTBOX).find().first()).isEqualTo(outbox);
        Document body = Document.parse(outbox.getString("body"));
        assertThat(body.getString("confirmedSessionId")).isEqualTo("second-session");
        assertThat(body.getString("occurredAt")).isEqualTo("2026-10-14T00:00:00.000Z");
        assertThat(body.get("schemaVersion")).isEqualTo(1);
        assertThat(outbox.getString("digest")).isEqualTo(RefundFanout.digest(outbox.getString("body")));
        assertThat(row("attempt_sessions", "session")).isEqualTo(before);
        assertThat(row("attempt_sessions", "second-session")).isEqualTo(second);
        assertThat(row("purchases", p).get("refundWork", Document.class).getString("phase")).isEqualTo("DONE");
    }
    @Test void missingPointerIsReviewNotGuessedAndRecheckDoesNotRewindCursor() {
        String group = confirmed(); String p = refund();
        db.getCollection("attempt_groups").updateOne(new Document("_id", group), new Document("$unset", new Document("lastConfirmedSessionId", "")));
        fanout().tick();
        assertThat(row("attempt_groups", group).get("refundReview", Document.class).getString("state")).isEqualTo("OPEN");
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isZero();
        assertThat(fanout().recheckGroup(p, group)).isTrue(); // Still unproven, still OPEN.
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isZero();
        // Test-only restoration of the originally observed durable pointer, not an operational guessing API.
        db.getCollection("attempt_groups").updateOne(new Document("_id", group), new Document("$set", new Document("lastConfirmedSessionId", "session")));
        assertThat(fanout().recheckGroup(p, group)).isTrue();
        assertThat(row("attempt_groups", group).get("refundReview", Document.class).getString("state")).isEqualTo("RESOLVED");
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isOne();
        assertThat(row("purchases", p).get("refundWork", Document.class).getString("phase")).isEqualTo("DONE");
    }
    @Test void outboxWriteFailureRollsBackGroupAndCursor() {
        String group = confirmed(); String p = refund();
        assertThat(fanout().advanceOne(p)).isTrue(); // RESERVATIONS -> GROUPS.
        Document before = row("attempt_groups", group);
        // Construct before deliberately damaging schema; no production bypass of schema verification.
        var worker = fanout();
        db.runCommand(new Document("collMod", RefundFanout.OUTBOX).append("validator", new Document("eventId", "reject-all")));
        assertThat(worker.advanceOne(p)).isFalse();
        assertThat(row("attempt_groups", group)).isEqualTo(before);
        assertThat(row("purchases", p).get("refundWork", Document.class).get("afterId")).isNull();
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isZero();
    }
    @Test void existingLeaseBlocksAndExpiredLeaseCanResume() {
        confirmed(); String p = refund();
        db.getCollection("purchases").updateOne(new Document("_id", p), new Document("$set", new Document("refundWork.owner", "old-worker")
                .append("refundWork.until", Date.from(NOW.plusSeconds(30)))));
        assertThat(fanout().advanceOne(p)).isFalse();
        assertThat(fanout(Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC)).advanceOne(p)).isTrue();
        assertThat(row("purchases", p).get("refundWork", Document.class).getLong("leaseVersion")).isEqualTo(2L);
    }
    @Test void concurrentWorkersCreateOneOutbox() throws Exception {
        confirmed(); refund(); var a = fanout(); var b = fanout();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(a::tick); var y = pool.submit(b::tick); x.get(15, TimeUnit.SECONDS); y.get(15, TimeUnit.SECONDS);
        }
        a.tick(); assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isOne();
    }
    @Test void processingOffPreservesWorkAndOutbox() {
        confirmed(); String p = refund(); var worker = fanout(); var before = row("purchases", p);
        properties.getRefund().setProcessingEnabled(false);
        assertThat(worker.tick()).isZero(); assertThat(row("purchases", p)).isEqualTo(before);
        properties.getRefund().setProcessingEnabled(true); worker.tick();
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isOne();
    }
    @Test void twentyStepLimitAndUnknownGroupDoNotBlockLaterGroups() {
        String original = confirmed();
        Document template = row("attempt_groups", original), sessionTemplate = row("attempt_sessions", "session");
        for (int i = 0; i < 24; i++) {
            String id = UUID.randomUUID().toString(), sessionId = "fixture-session-" + i;
            Document g = new Document(template); g.put("_id", id); g.put("attemptGroupId", id); g.remove("openGuard");
            g.put("activeSessionId", sessionId); g.put("lastConfirmedSessionId", sessionId);
            if (i == 0) g.remove("lastConfirmedSessionId");
            Document s = new Document(sessionTemplate); s.put("_id", sessionId); s.put("sessionId", sessionId);
            s.put("attemptGroupId", id); s.remove("activeGuard");
            db.getCollection("attempt_sessions").insertOne(s); db.getCollection("attempt_groups").insertOne(g);
        }
        String p = refund(); var worker = fanout();
        assertThat(worker.tick()).isEqualTo(20);
        assertThat(row("purchases", p).get("refundWork", Document.class).getString("phase")).isEqualTo("GROUPS");
        worker.tick();
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isEqualTo(24);
        assertThat(db.getCollection("attempt_groups").countDocuments(new Document("refundReview.state", "OPEN"))).isOne();
        assertThat(row("purchases", p).get("refundWork", Document.class).getString("phase")).isEqualTo("DONE");
    }
    @Test void leaseExpiryDuringTransactionRollsBackGroupAndOutbox() {
        String group = confirmed(); String p = refund(); fanout().advanceOne(p);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        Clock moving = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return calls.incrementAndGet() >= 4 ? NOW.plusSeconds(31) : NOW; }
        };
        var before = row("attempt_groups", group);
        assertThat(fanout(moving).advanceOne(p)).isFalse();
        assertThat(row("attempt_groups", group)).isEqualTo(before);
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isZero();
        fanout(Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC)).tick();
        assertThat(db.getCollection(RefundFanout.OUTBOX).countDocuments()).isOne();
    }
    @Test void schemaIsExplicitIdempotentAndRejectsDrift() {
        confirmed(); refund();
        assertThatThrownBy(() -> RefundFanoutSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(), new Document("$set", new Document("barrier", "ALL_BLOCKED")));
        RefundFanoutSchema.installUnderMaintenance(db, true); RefundFanoutSchema.verify(db);
        // This named test index belongs only to the disposable test database.
        db.getCollection(RefundFanout.OUTBOX).dropIndex("ix_access_outbox_due");
        db.getCollection(RefundFanout.OUTBOX).createIndex(new Document("work.state", 1), new com.mongodb.client.model.IndexOptions().name("ix_access_outbox_due"));
        assertThatThrownBy(() -> RefundFanoutSchema.installUnderMaintenance(db, true)).isInstanceOf(IllegalStateException.class);
    }
    com.nimbusds.jose.jwk.RSAKey key() throws Exception {
        return new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("fake-refund-test")
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                .keyOperations(Set.of(com.nimbusds.jose.jwk.KeyOperation.SIGN)).generate();
    }
    RefundAccessPublisher publisher(com.nimbusds.jose.jwk.RSAKey key, Clock time, RefundAccessPublisher.Transport transport) {
        return new RefundAccessPublisher(tx, mongo, payment, properties, "test", java.net.URI.create("https://lc.invalid"), key.toJSONString(), time, transport);
    }
    @Test void publisherUsesFreshJwtAndImmutableBodyAfterResponseLoss() throws Exception {
        confirmed(); refund(); fanout().tick(); properties.getRefund().setPublisherEnabled(true);
        var key = key(); var tokens = new ArrayList<String>(); var bodies = new ArrayList<String>();
        var first = publisher(key, clock, (uri, token, body) -> {
            assertThat(uri.getPath()).isEqualTo(RefundAccessPublisher.ROUTE);
            tokens.add(token); bodies.add(new String(body, java.nio.charset.StandardCharsets.UTF_8)); throw new java.io.IOException("fake response loss");
        });
        first.tick();
        var saved = db.getCollection(RefundFanout.OUTBOX).find().first();
        assertThat(saved.get("work", Document.class).getString("state")).isEqualTo("READY");
        Clock later = Clock.fixed(saved.get("work", Document.class).getDate("nextAttemptAt").toInstant(), ZoneOffset.UTC);
        publisher(key, later, (uri, token, body) -> { tokens.add(token); bodies.add(new String(body, java.nio.charset.StandardCharsets.UTF_8)); return new RefundAccessPublisher.Reply(204, Map.of(), new byte[0]); }).tick();
        assertThat(bodies).hasSize(2); assertThat(bodies.get(0)).isEqualTo(bodies.get(1)); assertThat(tokens.get(0)).isNotEqualTo(tokens.get(1));
        var jwt = com.nimbusds.jwt.SignedJWT.parse(tokens.get(1));
        assertThat(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(key.toRSAPublicKey()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("tosunsaeng-learning-core:entitlement-access-events:test");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("billing-service");
        assertThat(db.getCollection(RefundFanout.OUTBOX).find().first().get("work", Document.class).getString("state")).isEqualTo("DELIVERED");
    }
    @Test void publisherOffAndAuthenticationFailurePreserveBody() throws Exception {
        confirmed(); refund(); fanout().tick(); var before = db.getCollection(RefundFanout.OUTBOX).find().first();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var sender = publisher(key(), clock, (uri, token, body) -> { calls.incrementAndGet(); return new RefundAccessPublisher.Reply(401, Map.of(), new byte[0]); });
        assertThat(sender.tick()).isZero(); assertThat(calls).hasValue(0);
        properties.getRefund().setPublisherEnabled(true); sender.tick(); sender.tick();
        assertThat(calls).hasValue(1);
        var after = db.getCollection(RefundFanout.OUTBOX).find().first();
        assertThat(after.getString("body")).isEqualTo(before.getString("body"));
        assertThat(after.get("work", Document.class).getString("state")).isEqualTo("BLOCKED_AUTH");
    }
    @Test void latePublisherResponseCannotOverwriteNewGeneration() throws Exception {
        confirmed(); refund(); fanout().tick(); properties.getRefund().setPublisherEnabled(true);
        var row = db.getCollection(RefundFanout.OUTBOX).find().first();
        var sender = publisher(key(), clock, (uri, token, body) -> {
            db.getCollection(RefundFanout.OUTBOX).updateOne(new Document("_id", row.getString("_id")),
                    new Document("$inc", new Document("work.generation", 1L)).append("$set", new Document("work.owner", "new-worker")));
            return new RefundAccessPublisher.Reply(204, Map.of(), new byte[0]);
        });
        assertThat(sender.sendOne(row.getString("_id"))).isFalse();
        var saved = db.getCollection(RefundFanout.OUTBOX).find().first();
        assertThat(saved.get("deliveredAt")).isNull();
        assertThat(saved.get("work", Document.class).getString("owner")).isEqualTo("new-worker");
        assertThat(saved.get("work", Document.class).getLong("generation")).isEqualTo(2L);
    }
    @Test void publisherDoesNotDispatchAtTwentyFourHourBoundary() throws Exception {
        confirmed(); refund(); fanout().tick(); properties.getRefund().setPublisherEnabled(true);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.attempts", 1L)
                .append("work.retryStartedAt", Date.from(NOW.minusSeconds(86400)))));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        publisher(key(), clock, (uri, token, body) -> { calls.incrementAndGet(); return new RefundAccessPublisher.Reply(204, Map.of(), new byte[0]); }).tick();
        assertThat(calls).hasValue(0);
        assertThat(db.getCollection(RefundFanout.OUTBOX).find().first().get("work", Document.class).getString("state")).isEqualTo("REVIEW_REQUIRED");
    }
}
