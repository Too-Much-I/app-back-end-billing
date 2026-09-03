package web.tosunsaeng.billing.domain.ownerrebind.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityCandidate;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityEventType;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialCandidateAlias;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindOutcome;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindService;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.repository.ReservationRepository;
import web.tosunsaeng.billing.global.exception.InternalApiException;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class OwnerRebindMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0.14")
    );

    private static final String SUBJECT = "subject-ref-owner-rebind";
    private static final String CLAIM = "claim-owner-rebind";
    private static final String SOURCE = "00000000-0000-4000-8000-000000000001";
    private static final String TARGET = "00000000-0000-4000-8000-000000000002";
    private static final String SCOPE = "opaque-scope-v1";

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("billing.mongodb.initialize-indexes", () -> "true");
        registry.add("billing.mongodb.require-transactions", () -> "true");
        registry.add("billing.mongodb.schema-version", () -> "4");
        registry.add("billing.trial-eligibility.expected-consumer-scope-id", () -> SCOPE);
    }

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private OwnerRebindService service;
    @Autowired private ReservationRepository reservationRepository;

    private ExecutorService executor;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.getCollection("owner_rebind_inbox").deleteMany(new Document());
        mongoTemplate.getCollection("subject_owner_rebinds").deleteMany(new Document());
        mongoTemplate.getCollection("billing_subject_links").deleteMany(new Document());
        mongoTemplate.getCollection("trial_claims").deleteMany(new Document());
        mongoTemplate.getCollection("reservations").deleteMany(new Document());
        mongoTemplate.getCollection("entitlement_grants").deleteMany(new Document());
        mongoTemplate.getCollection("entitlement_ledger").deleteMany(new Document());
        mongoTemplate.getCollection("trial_eligibility").deleteMany(new Document());
        mongoTemplate.getCollection("trial_candidate_aliases").deleteMany(new Document());
        mongoTemplate.getCollection("attempt_groups").deleteMany(new Document());
        mongoTemplate.getCollection("attempt_sessions").deleteMany(new Document());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void legacyMissingOwnerVersionMovesOnlyOwnerAndConvergesToVersionTwo() {
        Instant now = Instant.now();
        insertClaimAndLegacyLink(now);
        mongoTemplate.getCollection("entitlement_grants").insertOne(
                new Document("_id", "grant-sentinel").append("units", 1)
        );
        mongoTemplate.getCollection("entitlement_ledger").insertOne(
                new Document("_id", "ledger-sentinel").append("units", 1)
        );
        Document claimBefore = mongoTemplate.getCollection("trial_claims")
                .find(new Document("_id", CLAIM)).first();

        OwnerRebindCommand command = command(1);
        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.APPLIED);
        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.DUPLICATE);

        Document link = mongoTemplate.getCollection("billing_subject_links")
                .find(new Document("_id", SUBJECT)).first();
        assertThat(link).isNotNull();
        assertThat(link.getString("userId")).isEqualTo(TARGET);
        assertThat(link.getLong("ownerVersion")).isEqualTo(2L);
        assertThat(link.getDate("ownerUpdatedAt")).isNotNull();
        assertThat(mongoTemplate.getCollection("trial_claims")
                .find(new Document("_id", CLAIM)).first()).isEqualTo(claimBefore);
        assertThat(mongoTemplate.getCollection("entitlement_grants").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("entitlement_ledger").countDocuments())
                .isEqualTo(1);
        assertThat(mongoTemplate.getCollection("owner_rebind_inbox")
                .countDocuments(new Document("eventId", command.eventId()))).isEqualTo(1);
        Document inbox = mongoTemplate.getCollection("owner_rebind_inbox")
                .find(new Document("eventId", command.eventId())).first();
        assertThat(inbox).doesNotContainKeys(
                "sourceUserId", "targetUserId", "subjectRefId", "trialClaimId", "payload"
        );
    }

    @Test
    void concurrentExactDeliveryCommitsOneMoveAndOneInbox() throws Exception {
        insertClaimAndLegacyLink(Instant.now());
        OwnerRebindCommand command = command(2);

        List<Object> outcomes = race(
                () -> service.process(command),
                () -> service.process(command)
        );

        assertThat(outcomes).contains(OwnerRebindOutcome.APPLIED);
        assertThat(outcomes).contains(OwnerRebindOutcome.DUPLICATE);
        Document link = mongoTemplate.getCollection("billing_subject_links")
                .find(new Document("_id", SUBJECT)).first();
        assertThat(link.getString("userId")).isEqualTo(TARGET);
        assertThat(link.getLong("ownerVersion")).isEqualTo(2L);
        assertThat(mongoTemplate.getCollection("owner_rebind_inbox")
                .countDocuments(new Document("eventId", command.eventId()))).isEqualTo(1);
    }

    @Test
    void activeReservationLeavesOwnerAndInboxUntouchedForRetry() {
        Instant now = Instant.now();
        insertClaimAndLegacyLink(now);
        reservationRepository.insert(Reservation.reserved(
                "reservation-owner-rebind", SUBJECT, "operation-owner-rebind", "hash",
                Reservation.Kind.INITIAL, "attempt-group", "session", "mock-exam",
                now, now.plusSeconds(300)
        ));

        OwnerRebindCommand command = command(3);
        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("OWNER_REBIND_PENDING");

        Document link = mongoTemplate.getCollection("billing_subject_links")
                .find(new Document("_id", SUBJECT)).first();
        assertThat(link.getString("userId")).isEqualTo(SOURCE);
        assertThat(link).doesNotContainKey("ownerVersion");
        assertThat(mongoTemplate.getCollection("owner_rebind_inbox")
                .countDocuments(new Document("eventId", command.eventId()))).isZero();
    }

    @Test
    void phoneRejoinOpenAttemptMovesOnlyOwnerAndKeepsSameGroup() {
        Instant now = Instant.now();
        insertClaimAndLegacyLink(now);
        insertPhonePrerequisites(now);
        mongoTemplate.insert(AttemptGroup.open(
                "attempt-group-open", SUBJECT, CLAIM, "consumption-ledger",
                "mock-exam", "old-session", now.minusSeconds(60)
        ));
        Document groupBefore = mongoTemplate.getCollection("attempt_groups")
                .find(new Document("_id", "attempt-group-open")).first();

        OwnerRebindCommand command = phoneCommand(4);
        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.APPLIED);
        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.DUPLICATE);

        Document link = mongoTemplate.getCollection("billing_subject_links")
                .find(new Document("_id", SUBJECT)).first();
        assertThat(link).isNotNull();
        assertThat(link.getString("userId")).isEqualTo(TARGET);
        assertThat(link.getString("ownerTransitionReason")).isEqualTo("PHONE_REJOIN");
        assertThat(link.getString("ownerTransitionId")).isEqualTo(command.eventId());
        assertThat(mongoTemplate.getCollection("attempt_groups")
                .find(new Document("_id", "attempt-group-open")).first()).isEqualTo(groupBefore);
    }

    @Test
    void phoneRejoinCompletedAttemptCommitsNoopWithoutChangingEntitlement() {
        Instant now = Instant.now();
        insertClaimAndLegacyLink(now);
        insertPhonePrerequisites(now);
        mongoTemplate.insert(AttemptGroup.projection(
                "attempt-group-completed", SUBJECT, CLAIM, "consumption-ledger",
                "mock-exam", AttemptGroup.Status.COMPLETED, now.minusSeconds(60)
        ));
        mongoTemplate.getCollection("entitlement_grants").insertOne(
                new Document("_id", "grant-sentinel").append("consumedUnits", 1)
        );
        mongoTemplate.getCollection("entitlement_ledger").insertOne(
                new Document("_id", "ledger-sentinel").append("eventType", "CONSUMED")
        );
        Document claimBefore = mongoTemplate.getCollection("trial_claims")
                .find(new Document("_id", CLAIM)).first();
        Document groupBefore = mongoTemplate.getCollection("attempt_groups")
                .find(new Document("_id", "attempt-group-completed")).first();

        OwnerRebindCommand command = phoneCommand(5);
        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.NOOP);
        assertThat(service.process(command)).isEqualTo(OwnerRebindOutcome.DUPLICATE);

        Document link = mongoTemplate.getCollection("billing_subject_links")
                .find(new Document("_id", SUBJECT)).first();
        assertThat(link).isNotNull();
        assertThat(link.getString("userId")).isEqualTo(SOURCE);
        assertThat(link).doesNotContainKey("ownerVersion");
        assertThat(mongoTemplate.getCollection("trial_claims")
                .find(new Document("_id", CLAIM)).first()).isEqualTo(claimBefore);
        assertThat(mongoTemplate.getCollection("attempt_groups")
                .find(new Document("_id", "attempt-group-completed")).first())
                .isEqualTo(groupBefore);
        assertThat(mongoTemplate.getCollection("entitlement_grants").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("entitlement_ledger").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("subject_owner_rebinds").countDocuments()).isZero();
        Document inbox = mongoTemplate.getCollection("owner_rebind_inbox")
                .find(new Document("eventId", command.eventId())).first();
        assertThat(inbox).isNotNull();
        assertThat(inbox.getString("disposition")).isEqualTo("NOOP");
        assertThat(inbox.getInteger("affectedSubjectCount")).isZero();
    }

    private void insertClaimAndLegacyLink(Instant now) {
        mongoTemplate.insert(TrialClaim.active(
                CLAIM, BenefitDefinition.FREE_EXAM_ONCE, SUBJECT, "source-event",
                now.minusSeconds(60), now.plusSeconds(3600)
        ));
        mongoTemplate.getCollection("billing_subject_links").insertOne(new Document()
                .append("_id", SUBJECT)
                .append("subjectRefId", SUBJECT)
                .append("trialClaimId", CLAIM)
                .append("consumerScopeId", SCOPE)
                .append("userId", SOURCE)
                .append("active", true)
                .append("createdAt", java.util.Date.from(now.minusSeconds(60)))
                .append("retentionExpiresAt", java.util.Date.from(now.plusSeconds(3600))));
    }

    private void insertPhonePrerequisites(Instant now) {
        TrialEligibilityEvent sourceEvent = new TrialEligibilityEvent(
                "00000000-0000-4000-8000-000000000101",
                TrialEligibilityEventType.REVOKED,
                1,
                "identity",
                now.minusSeconds(30),
                SCOPE,
                SOURCE,
                null,
                now.minusSeconds(30),
                2,
                List.of(),
                "source-digest"
        );
        TrialEligibilityEvent targetEvent = new TrialEligibilityEvent(
                "00000000-0000-4000-8000-000000000102",
                TrialEligibilityEventType.VERIFIED,
                1,
                "identity",
                now.minusSeconds(20),
                SCOPE,
                TARGET,
                now.minusSeconds(20),
                null,
                1,
                List.of(new TrialEligibilityCandidate("v1", "candidate-value")),
                "target-digest"
        );
        mongoTemplate.insert(TrialEligibility.applied(null, sourceEvent, now.minusSeconds(30)));
        mongoTemplate.insert(TrialEligibility.applied(null, targetEvent, now.minusSeconds(20)));
        mongoTemplate.insert(TrialCandidateAlias.active(
                "alias-phone-rejoin", BenefitDefinition.FREE_EXAM_ONCE,
                "v1", "candidate-value", CLAIM,
                now.minusSeconds(60), now.plusSeconds(3600)
        ));
    }

    private static OwnerRebindCommand command(int suffix) {
        return new OwnerRebindCommand(
                "00000000-0000-4000-8000-%012d".formatted(suffix),
                OwnerRebindEventKind.USER_MERGED,
                1,
                Instant.now().minusSeconds(1),
                SOURCE,
                TARGET,
                null,
                null,
                null,
                "digest-" + suffix
        );
    }

    private static OwnerRebindCommand phoneCommand(int suffix) {
        return new OwnerRebindCommand(
                "00000000-0000-4000-8000-%012d".formatted(suffix),
                OwnerRebindEventKind.PHONE_REJOIN,
                1,
                Instant.now().minusSeconds(1),
                SOURCE,
                TARGET,
                SCOPE,
                2L,
                1L,
                "phone-digest-" + suffix
        );
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Object> firstResult = executor.submit(racingCall(first, ready, start));
        Future<Object> secondResult = executor.submit(racingCall(second, ready, start));
        ready.await();
        start.countDown();
        return List.of(firstResult.get(), secondResult.get());
    }

    private static Callable<Object> racingCall(
            Callable<?> delegate,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return delegate.call();
            } catch (Exception exception) {
                return exception;
            }
        };
    }
}
