package web.tosunsaeng.billing.domain.attempt.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import web.tosunsaeng.billing.domain.attempt.api.AttemptGroupEventDecoder;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventService;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementLedgerEntry;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.global.exception.InternalApiException;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class AttemptGroupEventMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0.14")
    );

    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:00:00Z");
    private static final String GROUP_ID = "be07ae1d-f877-4ae4-82df-c5f442e9bb8e";
    private static final String SESSION_ID = "ex_a1b2c3d4e5_0826_1530";
    private static final String SUBJECT_ID = "subject-ref-1";
    private static final String CLAIM_ID = "claim-1";
    private static final String USER_ID = "e8b37a41-bae6-47f1-a770-052e6c5786d4";

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("billing.mongodb.initialize-indexes", () -> "true");
        registry.add("billing.mongodb.require-transactions", () -> "true");
        registry.add("billing.mongodb.schema-version", () -> "3");
        registry.add("billing.trial-eligibility.expected-consumer-scope-id",
                () -> "opaque-scope-v1");
        registry.add("billing.attempt-group-events.enabled", () -> "true");
    }

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private AttemptGroupEventDecoder decoder;
    @Autowired
    private AttemptGroupEventService service;
    @Autowired
    private AttemptGroupRepository groupRepository;
    @Autowired
    private AttemptSessionRepository sessionRepository;
    @Autowired
    private BillingSubjectLinkRepository subjectLinkRepository;

    private ExecutorService executor;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.getCollection("inbound_event_inbox").deleteMany(new Document());
        mongoTemplate.remove(new Query(), AttemptGroup.class);
        mongoTemplate.remove(new Query(), AttemptSession.class);
        mongoTemplate.remove(new Query(), BillingSubjectLink.class);
        mongoTemplate.remove(new Query(), EntitlementGrant.class);
        mongoTemplate.remove(new Query(), EntitlementLedgerEntry.class);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void gradingThenCompletionCommitsInboxGroupAndSession() {
        createActiveAttempt();

        assertThat(service.process(event("GRADING", 1)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);
        AttemptGroup grading = groupRepository.findById(GROUP_ID).orElseThrow();
        AttemptSession stillActive = sessionRepository.findBySessionId(SESSION_ID).orElseThrow();
        assertThat(grading.getStatus()).isEqualTo(AttemptGroup.Status.GRADING);
        assertThat(stillActive.getState()).isEqualTo(AttemptSession.State.ACTIVE);

        assertThat(service.process(event("COMPLETED", 2)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);

        AttemptGroup completed = groupRepository.findById(GROUP_ID).orElseThrow();
        AttemptSession terminal = sessionRepository.findBySessionId(SESSION_ID).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(AttemptGroup.Status.COMPLETED);
        assertThat(completed.getOpenGuard()).isNull();
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(terminal.getState()).isEqualTo(AttemptSession.State.COMPLETED);
        assertThat(terminal.getActiveGuard()).isNull();
        assertThat(terminal.getTerminalAt()).isNotNull();

        List<Document> inbox = mongoTemplate.getCollection("inbound_event_inbox")
                .find(new Document("producer", "learning-core"))
                .into(new java.util.ArrayList<>());
        assertThat(inbox).hasSize(2).allSatisfy(document -> {
            assertThat(document).doesNotContainKeys(
                    "userId", "attemptGroupId", "sessionId", "evidence", "failureCode", "_class"
            );
            assertThat(document.getDate("purgeAt")).isAfter(document.getDate("receivedAt"));
        });
    }

    @Test
    void retakeClosesOnlyAttemptProjectionAndDoesNotCreateEntitlementWrites() {
        createActiveAttempt();
        long grantsBefore = mongoTemplate.count(new Query(), EntitlementGrant.class);
        long ledgerBefore = mongoTemplate.count(new Query(), EntitlementLedgerEntry.class);

        assertThat(service.process(event("RETAKE_AVAILABLE", 3)))
                .isEqualTo(AttemptGroupEventOutcome.APPLIED);

        AttemptGroup group = groupRepository.findById(GROUP_ID).orElseThrow();
        AttemptSession session = sessionRepository.findBySessionId(SESSION_ID).orElseThrow();
        assertThat(group.getStatus()).isEqualTo(AttemptGroup.Status.RETAKE_AVAILABLE);
        assertThat(group.getActiveSessionId()).isNull();
        assertThat(group.getOpenGuard()).isTrue();
        assertThat(session.getState()).isEqualTo(AttemptSession.State.FAILED);
        assertThat(session.getActiveGuard()).isNull();
        assertThat(mongoTemplate.count(new Query(), EntitlementGrant.class))
                .isEqualTo(grantsBefore);
        assertThat(mongoTemplate.count(new Query(), EntitlementLedgerEntry.class))
                .isEqualTo(ledgerBefore);
    }

    @Test
    void sameEventConcurrentDeliveryConvergesToAppliedAndDuplicate() throws Exception {
        createActiveAttempt();
        AttemptGroupStatusEvent event = event("GRADING", 4);

        List<Object> outcomes = race(() -> service.process(event), () -> service.process(event));

        assertThat(outcomes).contains(AttemptGroupEventOutcome.APPLIED);
        assertThat(outcomes).contains(AttemptGroupEventOutcome.DUPLICATE);
        assertThat(mongoTemplate.getCollection("inbound_event_inbox")
                .countDocuments(new Document("eventId", event.eventId()))).isEqualTo(1);
        assertThat(groupRepository.findById(GROUP_ID).orElseThrow().getVersion()).isEqualTo(2);
    }

    @Test
    void competingTerminalEventsHaveOneAppliedWinnerAndOneStaleLoser() throws Exception {
        createActiveAttempt();
        AttemptGroupStatusEvent completed = event("COMPLETED", 5);
        AttemptGroupStatusEvent retake = event("RETAKE_AVAILABLE", 6);

        List<Object> outcomes = race(
                () -> service.process(completed),
                () -> service.process(retake)
        );

        assertThat(outcomes).contains(AttemptGroupEventOutcome.APPLIED);
        assertThat(outcomes).contains(AttemptGroupEventOutcome.STALE);
        AttemptGroup group = groupRepository.findById(GROUP_ID).orElseThrow();
        AttemptSession session = sessionRepository.findBySessionId(SESSION_ID).orElseThrow();
        assertThat(List.of(AttemptGroup.Status.COMPLETED, AttemptGroup.Status.RETAKE_AVAILABLE))
                .contains(group.getStatus());
        assertThat(List.of(AttemptSession.State.COMPLETED, AttemptSession.State.FAILED))
                .contains(session.getState());
    }

    @Test
    void missingProjectionAndTargetConflictDoNotWriteInbox() {
        AttemptGroupStatusEvent missing = event("GRADING", 7);
        assertThatThrownBy(() -> service.process(missing))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("ATTEMPT_PROJECTION_NOT_READY");
        assertThat(mongoTemplate.getCollection("inbound_event_inbox").countDocuments()).isZero();

        createActiveAttempt();
        AttemptGroupStatusEvent wrongUser = event("GRADING", 8, "00000000-0000-4000-8000-000000000099");
        assertThatThrownBy(() -> service.process(wrongUser))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_TARGET_CONFLICT");
        assertThat(mongoTemplate.getCollection("inbound_event_inbox").countDocuments()).isZero();
    }

    private void createActiveAttempt() {
        subjectLinkRepository.insert(BillingSubjectLink.active(
                SUBJECT_ID, CLAIM_ID, "opaque-scope-v1", USER_ID,
                CREATED_AT, Instant.parse("2029-08-30T10:00:00Z")
        ));
        groupRepository.insert(AttemptGroup.open(
                GROUP_ID, SUBJECT_ID, CLAIM_ID, "ledger-1", "mock-exam-01",
                SESSION_ID, CREATED_AT
        ));
        sessionRepository.insert(AttemptSession.proposed(
                SESSION_ID, GROUP_ID, SUBJECT_ID,
                "00000000-0000-4000-8000-000000000010", CREATED_AT
        ));
        sessionRepository.activateProposed(
                SESSION_ID, GROUP_ID, SUBJECT_ID,
                "00000000-0000-4000-8000-000000000010",
                1, CREATED_AT.plusSeconds(1)
        ).orElseThrow();
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

    private AttemptGroupStatusEvent event(String target, int suffix) {
        return event(target, suffix, USER_ID);
    }

    private AttemptGroupStatusEvent event(String target, int suffix, String userId) {
        String targetFields = switch (target) {
            case "COMPLETED" -> """
                    "targetStatus":"COMPLETED",
                    "evidence":{
                      "requiredFeedbackQueryable":true,
                      "validScoreQueryable":true,
                      "summaryQueryable":true,
                      "evidenceVersion":1
                    }
                    """;
            case "RETAKE_AVAILABLE" -> """
                    "targetStatus":"RETAKE_AVAILABLE",
                    "failureCode":"SUMMARY_UNAVAILABLE"
                    """;
            default -> "\"targetStatus\":\"GRADING\"";
        };
        String json = """
                {
                  "eventId":"%s",
                  "eventType":"AttemptGroupStatusChanged",
                  "schemaVersion":1,
                  "producer":"learning-core",
                  "occurredAt":"2026-08-30T10:01:00.000Z",
                  "userId":"%s",
                  "attemptGroupId":"%s",
                  "sessionId":"%s",
                  %s
                }
                """.formatted(eventId(suffix), userId, GROUP_ID, SESSION_ID, targetFields);
        return decoder.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String eventId(int suffix) {
        return "00000000-0000-4000-8000-%012d".formatted(suffix);
    }
}
