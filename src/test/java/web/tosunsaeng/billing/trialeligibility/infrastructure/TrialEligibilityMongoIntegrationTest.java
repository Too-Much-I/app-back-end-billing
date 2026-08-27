package web.tosunsaeng.billing.trialeligibility.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import web.tosunsaeng.billing.config.BillingMongoProperties;
import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.global.mongodb.BillingMongoIndexInitializer;
import web.tosunsaeng.billing.global.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.trialeligibility.api.TrialEligibilityEventDecoder;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityEventOutcome;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityMetrics;
import web.tosunsaeng.billing.trialeligibility.domain.InboundEventInbox;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibility;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityEvent;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityState;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class TrialEligibilityMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0.14")
    );

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("billing.mongodb.initialize-indexes", () -> "true");
        registry.add("billing.mongodb.require-transactions", () -> "true");
        registry.add("billing.mongodb.schema-version", () -> "1");
        registry.add("billing.trial-eligibility.expected-consumer-scope-id",
                () -> "opaque-scope-v1");
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TrialEligibilityEventDecoder decoder;

    @Autowired
    private TrialEligibilityEventService service;

    @Autowired
    private InboundEventInboxRepository inboxRepository;

    @Autowired
    private MongoTransactionExecutor transactionExecutor;

    @Autowired
    private BillingMongoIndexInitializer indexInitializer;

    private ExecutorService executor;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.remove(new Query(), InboundEventInbox.class);
        mongoTemplate.remove(new Query(), TrialEligibility.class);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void createsApprovedIndexesAndRerunsIdempotently() throws Exception {
        indexInitializer.run(new DefaultApplicationArguments(new String[0]));

        Map<String, Document> inboxIndexes = indexes("inbound_event_inbox");
        Map<String, Document> eligibilityIndexes = indexes("trial_eligibility");

        assertThat(inboxIndexes).containsKeys(
                "ux_inbox_event_id",
                "ux_inbox_identity_scope_user_revision",
                "ttl_inbox_purge_at"
        );
        assertThat(inboxIndexes.get("ux_inbox_event_id").getBoolean("unique")).isTrue();
        assertThat(inboxIndexes.get("ux_inbox_identity_scope_user_revision")
                .get("partialFilterExpression", Document.class))
                .containsEntry("producer", "identity");
        assertThat(inboxIndexes.get("ttl_inbox_purge_at")
                .get("expireAfterSeconds", Number.class).longValue()).isZero();
        assertThat(eligibilityIndexes).containsKeys("ux_trial_scope_user", "ix_trial_key_version");
        assertThat(new ArrayList<>(eligibilityIndexes.get("ux_trial_scope_user")
                .get("key", Document.class).keySet()))
                .containsExactly("consumerScopeId", "userId");
    }

    @Test
    void failsFastWhenNamedIndexHasDifferentOptions() {
        String databaseName = "billing_index_mismatch";
        try (MongoClient client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            MongoTemplate isolated = new MongoTemplate(client, databaseName);
            isolated.createCollection(BillingMongoIndexInitializer.INBOX_COLLECTION);
            isolated.getCollection(BillingMongoIndexInitializer.INBOX_COLLECTION).createIndex(
                    new Document("eventId", -1),
                    new IndexOptions().name("ux_inbox_event_id").unique(true)
            );
            BillingMongoProperties properties = new BillingMongoProperties();
            properties.setInitializeIndexes(true);
            properties.setSchemaVersion(1);
            BillingMongoIndexInitializer initializer = new BillingMongoIndexInitializer(
                    isolated, properties
            );

            assertThatThrownBy(() -> initializer.run(
                    new DefaultApplicationArguments(new String[0])
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ux_inbox_event_id")
                    .hasMessageNotContaining("e8b37a41");
            isolated.getDb().drop();
        }
    }

    @Test
    void appliesVerifiedThenRevokedAndKeepsRevisionTombstone() {
        assertThat(service.process(decodeVerified(1, eventId(1), "A")))
                .isEqualTo(TrialEligibilityEventOutcome.APPLIED);
        assertThat(service.process(decodeRevoked(2, eventId(2))))
                .isEqualTo(TrialEligibilityEventOutcome.APPLIED);

        TrialEligibility current = current();
        assertThat(current.getBindingRevision()).isEqualTo(2);
        assertThat(current.getState()).isEqualTo(TrialEligibilityState.REVOKED);
        assertThat(current.getCandidates()).isEmpty();
        assertThat(current.getVerifiedAt()).isNull();
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(mongoTemplate.getCollection("trial_eligibility").find().first())
                .doesNotContainKeys("candidates", "verifiedAt");
    }

    @Test
    void staleEventWritesInboxOnlyAndDoesNotRollbackProjection() {
        service.process(decodeRevoked(2, eventId(2)));

        assertThat(service.process(decodeVerified(1, eventId(1), "A")))
                .isEqualTo(TrialEligibilityEventOutcome.STALE);

        assertThat(current().getBindingRevision()).isEqualTo(2);
        assertThat(current().getState()).isEqualTo(TrialEligibilityState.REVOKED);
        assertThat(mongoTemplate.findAll(InboundEventInbox.class))
                .extracting(InboundEventInbox::getDisposition)
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("APPLIED", "STALE");
    }

    @Test
    void transactionFailureRollsBackInboxAndProjection() {
        TrialEligibilityRepository failingRepository = new TrialEligibilityRepository(mongoTemplate) {
            @Override
            public TrialEligibility replace(TrialEligibility eligibility) {
                super.replace(eligibility);
                throw new DataAccessResourceFailureException("simulated transaction failure");
            }
        };
        TrialEligibilityEventService failingService = new TrialEligibilityEventService(
                inboxRepository,
                failingRepository,
                transactionExecutor,
                new TrialEligibilityMetrics(new SimpleMeterRegistry()),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> failingService.process(decodeVerified(1, eventId(1), "A")))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(mongoTemplate.count(new Query(), InboundEventInbox.class)).isZero();
        assertThat(mongoTemplate.count(new Query(), TrialEligibility.class)).isZero();
    }

    @Test
    void concurrentSameEventConvergesToOneInboxAndTwo204Outcomes() throws Exception {
        TrialEligibilityEvent event = decodeVerified(1, eventId(1), "A");

        List<Object> results = race(() -> service.process(event), () -> service.process(event));

        assertThat(results).allMatch(value -> value instanceof TrialEligibilityEventOutcome);
        assertThat(results).contains(TrialEligibilityEventOutcome.APPLIED);
        assertThat(results).anyMatch(value -> value == TrialEligibilityEventOutcome.DUPLICATE);
        assertThat(mongoTemplate.count(new Query(), InboundEventInbox.class)).isEqualTo(1);
        assertThat(mongoTemplate.count(new Query(), TrialEligibility.class)).isEqualTo(1);
    }

    @Test
    void concurrentDifferentEventForSameRevisionConvergesToConflict() throws Exception {
        TrialEligibilityEvent first = decodeVerified(1, eventId(1), "A");
        TrialEligibilityEvent second = decodeVerified(1, eventId(2), "B");

        List<Object> results = race(() -> service.process(first), () -> service.process(second));

        assertThat(results).anyMatch(value -> value == TrialEligibilityEventOutcome.APPLIED);
        assertThat(results).anyMatch(value -> value instanceof InternalApiException exception
                && "EVENT_ID_CONFLICT".equals(exception.code()));
        assertThat(mongoTemplate.count(new Query(), InboundEventInbox.class)).isEqualTo(1);
        assertThat(mongoTemplate.count(new Query(), TrialEligibility.class)).isEqualTo(1);
    }

    @Test
    void concurrentSameEventIdWithDifferentPayloadConvergesToConflict() throws Exception {
        TrialEligibilityEvent first = decodeVerified(1, eventId(1), "A");
        TrialEligibilityEvent second = decodeVerified(1, eventId(1), "B");

        List<Object> results = race(() -> service.process(first), () -> service.process(second));

        assertThat(results).anyMatch(value -> value == TrialEligibilityEventOutcome.APPLIED);
        assertThat(results).anyMatch(value -> value instanceof InternalApiException exception
                && "EVENT_ID_CONFLICT".equals(exception.code()));
        assertThat(mongoTemplate.count(new Query(), InboundEventInbox.class)).isEqualTo(1);
        assertThat(mongoTemplate.count(new Query(), TrialEligibility.class)).isEqualTo(1);
    }

    @Test
    void reverseArrivalLeavesHighestRevisionCurrent() throws Exception {
        TrialEligibilityEvent revisionOne = decodeVerified(1, eventId(1), "A");
        TrialEligibilityEvent revisionTwo = decodeRevoked(2, eventId(2));

        List<Object> results = race(
                () -> service.process(revisionOne),
                () -> service.process(revisionTwo)
        );

        assertThat(results).allMatch(value -> value instanceof TrialEligibilityEventOutcome);
        assertThat(current().getBindingRevision()).isEqualTo(2);
        assertThat(current().getState()).isEqualTo(TrialEligibilityState.REVOKED);
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> wrapFirst = racingCall(first, ready, start);
        Callable<Object> wrapSecond = racingCall(second, ready, start);
        Future<Object> firstResult = executor.submit(wrapFirst);
        Future<Object> secondResult = executor.submit(wrapSecond);
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

    private Map<String, Document> indexes(String collection) {
        java.util.HashMap<String, Document> indexes = new java.util.HashMap<>();
        for (Document index : mongoTemplate.getCollection(collection).listIndexes()) {
            indexes.put(index.getString("name"), index);
        }
        return indexes;
    }

    private TrialEligibility current() {
        return mongoTemplate.findAll(TrialEligibility.class).getFirst();
    }

    private TrialEligibilityEvent decodeVerified(long revision, String eventId, String marker) {
        String value = marker.repeat(43);
        String json = """
                {
                  "eventId":"%s",
                  "eventType":"PhoneEligibilityBindingVerified",
                  "schemaVersion":1,
                  "producer":"identity",
                  "occurredAt":"2026-08-27T00:00:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                  "verifiedAt":"2026-08-26T23:59:59Z",
                  "bindingRevision":%d,
                  "fingerprintCandidates":[{"keyVersion":"v1","value":"%s"}]
                }
                """.formatted(eventId, revision, value);
        return decoder.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private TrialEligibilityEvent decodeRevoked(long revision, String eventId) {
        String json = """
                {
                  "eventId":"%s",
                  "eventType":"PhoneEligibilityBindingRevoked",
                  "schemaVersion":1,
                  "producer":"identity",
                  "occurredAt":"2026-08-27T00:00:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                  "revokedAt":"2026-08-27T00:00:00Z",
                  "bindingRevision":%d
                }
                """.formatted(eventId, revision);
        return decoder.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String eventId(int suffix) {
        return "00000000-0000-4000-8000-%012d".formatted(suffix);
    }
}
