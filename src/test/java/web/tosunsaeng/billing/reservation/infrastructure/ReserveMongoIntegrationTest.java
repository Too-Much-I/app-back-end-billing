package web.tosunsaeng.billing.reservation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.mongodb.MongoException;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import web.tosunsaeng.billing.config.ReservationProperties;
import web.tosunsaeng.billing.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.global.mongodb.BillingMongoIndexInitializer;
import web.tosunsaeng.billing.global.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.reservation.application.ReserveMetrics;
import web.tosunsaeng.billing.reservation.application.ReserveResult;
import web.tosunsaeng.billing.reservation.application.ReserveService;
import web.tosunsaeng.billing.reservation.domain.AttemptGroup;
import web.tosunsaeng.billing.reservation.domain.AttemptSession;
import web.tosunsaeng.billing.reservation.domain.BillingSubjectLink;
import web.tosunsaeng.billing.reservation.domain.EntitlementGrant;
import web.tosunsaeng.billing.reservation.domain.EntitlementLedgerEntry;
import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;
import web.tosunsaeng.billing.reservation.domain.ReservationAllocation;
import web.tosunsaeng.billing.reservation.domain.TrialCandidateAlias;
import web.tosunsaeng.billing.reservation.domain.TrialClaim;
import web.tosunsaeng.billing.trialeligibility.api.TrialEligibilityEventDecoder;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.trialeligibility.domain.InboundEventInbox;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibility;
import web.tosunsaeng.billing.trialeligibility.infrastructure.TrialEligibilityRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class ReserveMongoIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final String USER_ONE = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    private static final String USER_TWO = "2d823d8b-54f7-4ad6-b135-61f56da8b8c9";
    private static final String OP_ONE = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
    private static final String OP_TWO = "1b09da80-76bb-4ff4-9b9e-7dc2b0b0d83f";
    private static final String CANDIDATE = "A".repeat(43);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0.14")
    );

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("billing.mongodb.initialize-indexes", () -> "true");
        registry.add("billing.mongodb.require-transactions", () -> "true");
        registry.add("billing.mongodb.schema-version", () -> "2");
        registry.add("billing.trial-eligibility.expected-consumer-scope-id",
                () -> "opaque-scope-v1");
    }

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private TrialEligibilityEventDecoder eligibilityDecoder;
    @Autowired
    private TrialEligibilityEventService eligibilityService;
    @Autowired
    private ReserveService reserveService;
    @Autowired
    private BillingMongoIndexInitializer indexInitializer;
    @Autowired
    private MongoTransactionManager transactionManager;
    @Autowired
    private TrialEligibilityRepository eligibilityRepository;
    @Autowired
    private TrialClaimRepository claimRepository;
    @Autowired
    private TrialCandidateAliasRepository aliasRepository;
    @Autowired
    private BillingSubjectLinkRepository subjectLinkRepository;
    @Autowired
    private EntitlementGrantRepository grantRepository;
    @Autowired
    private EntitlementLedgerRepository ledgerRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationAllocationRepository allocationRepository;
    @Autowired
    private IdempotencyCommandRepository commandRepository;
    @Autowired
    private AttemptGroupRepository attemptGroupRepository;
    @Autowired
    private AttemptSessionRepository attemptSessionRepository;
    @Autowired
    private TrialEligibilityProperties eligibilityProperties;
    @Autowired
    private ReservationProperties reservationProperties;
    @Autowired
    private ReserveMetrics reserveMetrics;

    @MockitoBean
    private Clock clock;

    private ExecutorService executor;

    @BeforeEach
    void cleanCollections() {
        when(clock.instant()).thenReturn(NOW);
        for (String collection : List.of(
                "inbound_event_inbox", "trial_eligibility", "trial_claims",
                "trial_candidate_aliases", "billing_subject_links", "entitlement_grants",
                "entitlement_ledger", "reservations", "reservation_allocations",
                "idempotency_commands", "attempt_groups", "attempt_sessions"
        )) {
            mongoTemplate.getCollection(collection).deleteMany(new Document());
        }
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void createsApprovedReserveIndexes() throws Exception {
        indexInitializer.run(new DefaultApplicationArguments(new String[0]));

        assertThat(indexes("trial_candidate_aliases"))
                .containsKeys("ux_active_trial_candidate", "ix_alias_active_expiry", "ix_alias_claim");
        assertThat(indexes("billing_subject_links"))
                .containsKeys("ux_subject_link_claim", "ix_subject_link_user_active");
        assertThat(indexes("entitlement_grants")).containsKey("ux_grant_source_type");
        assertThat(indexes("entitlement_ledger"))
                .containsKeys("ux_ledger_dedupe", "ux_ledger_aggregate_sequence");
        assertThat(indexes("reservations"))
                .containsKeys("ux_reservation_subject_operation", "ux_active_reservation_subject");
        assertThat(indexes("idempotency_commands"))
                .containsKeys("ux_command_scope_operation_type", "ux_active_create_command_user");
        assertThat(indexes("attempt_sessions"))
                .containsKeys("ux_attempt_session_id", "ux_active_session_subject");
        assertThat(indexes("idempotency_commands").get("ttl_terminal_command_purge_at")
                .get("expireAfterSeconds", Number.class).longValue()).isZero();
        assertThat(indexes("trial_candidate_aliases").get("ux_active_trial_candidate")
                .get("partialFilterExpression", Document.class))
                .isEqualTo(new Document("active", true));
    }

    @Test
    void initialReserveAtomicallyCreatesClaimGrantHoldReservationAndSnapshot() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);

        ReserveResult result = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        ));

        assertThat(result.replayed()).isFalse();
        assertThat(result.snapshot().reservationKind()).isEqualTo(Reservation.Kind.INITIAL);
        assertThat(result.snapshot().reservationStatus()).isEqualTo(Reservation.Status.RESERVED);
        assertThat(result.snapshot().expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.toString()).doesNotContain(
                USER_ONE, OP_ONE, "session-1", "mock-1", result.snapshot().reservationId()
        );
        assertThat(result.snapshot().toString()).doesNotContain(
                USER_ONE, OP_ONE, "session-1", "mock-1", result.snapshot().reservationId()
        );
        assertThat(count(TrialClaim.class)).isEqualTo(1);
        assertThat(count(TrialCandidateAlias.class)).isEqualTo(1);
        assertThat(count(BillingSubjectLink.class)).isEqualTo(1);
        assertThat(count(EntitlementGrant.class)).isEqualTo(1);
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(2);
        assertThat(count(ReservationAllocation.class)).isEqualTo(1);
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(IdempotencyCommand.class)).isEqualTo(1);
        assertThat(count(AttemptSession.class)).isEqualTo(1);
        assertThat(count(AttemptGroup.class)).isZero();

        TrialClaim claim = mongoTemplate.findAll(TrialClaim.class).getFirst();
        assertThat(claim.getClaimedAt()).isEqualTo(NOW);
        assertThat(claim.getRetentionExpiresAt()).isEqualTo(
                Instant.parse("2029-08-28T00:00:00Z")
        );
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits()).isZero();
        assertThat(grant.getHeldUnits()).isEqualTo(1);
        assertThat(grant.getConsumedUnits()).isZero();
        assertThat(mongoTemplate.findAll(EntitlementLedgerEntry.class))
                .extracting(EntitlementLedgerEntry::getEventType)
                .containsExactlyInAnyOrder(
                        EntitlementLedgerEntry.EventType.GRANTED,
                        EntitlementLedgerEntry.EventType.RESERVED
                );
    }

    @Test
    void sameOperationReplaysAndDifferentPayloadConflicts() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveCommand original = command(USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1");

        ReserveResult first = reserveService.reserve(original);
        ReserveResult replay = reserveService.reserve(original);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.snapshot()).isEqualTo(first.snapshot());
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(2);
        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-2", "mock-1", "different-hash"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void revokedEligibilityCreatesNothing() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        applyRevoked(USER_ONE, 2, "00000000-0000-4000-8000-000000000002");

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("ENTITLEMENT_INSUFFICIENT");
        assertThat(count(TrialClaim.class)).isZero();
        assertThat(count(EntitlementGrant.class)).isZero();
        assertThat(count(IdempotencyCommand.class)).isZero();
    }

    @Test
    void concurrentDifferentUsersWithSameCandidateCreateOneClaimAndOneGrant() throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        applyVerified(USER_TWO, 1, "00000000-0000-4000-8000-000000000002", CANDIDATE);

        List<Object> results = race(
                () -> reserveService.reserve(command(
                        USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
                )),
                () -> reserveService.reserve(command(
                        USER_TWO, OP_TWO, "session-2", "mock-1", "hash-2"
                ))
        );

        assertThat(results).anyMatch(ReserveResult.class::isInstance);
        assertThat(results).anyMatch(value -> value instanceof InternalApiException exception
                && "ENTITLEMENT_INSUFFICIENT".equals(exception.code()));
        assertThat(count(TrialClaim.class)).isEqualTo(1);
        assertThat(count(EntitlementGrant.class)).isEqualTo(1);
        assertThat(count(Reservation.class)).isEqualTo(1);
    }

    @Test
    void concurrentDifferentOperationsForSameUserLeaveOneActiveCommandAndReservation()
            throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);

        List<Object> results = race(
                () -> reserveService.reserve(command(
                        USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
                )),
                () -> reserveService.reserve(command(
                        USER_ONE, OP_TWO, "session-2", "mock-1", "hash-2"
                ))
        );

        assertThat(results).anyMatch(ReserveResult.class::isInstance);
        assertThat(results).anyMatch(value -> value instanceof InternalApiException exception
                && "COMMAND_PROCESSING".equals(exception.code()));
        assertThat(count(IdempotencyCommand.class)).isEqualTo(1);
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(AttemptSession.class)).isEqualTo(1);
    }

    @Test
    void keyRotationOverlapAddsMissingAliasWithoutCreatingSecondClaim() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        reserveService.reserve(command(USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"));
        prepareCanceledInitial();
        applyVerifiedWithRotation(
                USER_ONE, 2, "00000000-0000-4000-8000-000000000002",
                CANDIDATE, "B".repeat(43)
        );

        ReserveResult result = reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-1", "hash-2"
        ));

        assertThat(result.snapshot().reservationKind()).isEqualTo(Reservation.Kind.INITIAL);
        assertThat(count(TrialClaim.class)).isEqualTo(1);
        assertThat(count(TrialCandidateAlias.class)).isEqualTo(2);
        assertThat(mongoTemplate.findAll(TrialCandidateAlias.class))
                .extracting(TrialCandidateAlias::getKeyVersion)
                .containsExactlyInAnyOrder("v1", "v2");
    }

    @Test
    void expiredAliasIsFencedBeforeCreatingNewClaim() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        aliasRepository.insert(TrialCandidateAlias.active(
                "alias-expired", "v1", CANDIDATE, "claim-expired",
                NOW.minusSeconds(100), NOW.minusSeconds(1)
        ));

        reserveService.reserve(command(USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"));

        assertThat(count(TrialClaim.class)).isEqualTo(1);
        assertThat(count(TrialCandidateAlias.class)).isEqualTo(2);
        Query active = Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("active").is(true));
        assertThat(mongoTemplate.count(active, TrialCandidateAlias.class)).isEqualTo(1);
    }

    @Test
    void replacementReusesGroupWithoutAdditionalAllocationOrLedger() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult initial = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        ));
        prepareConfirmedOpenGroup(initial);

        ReserveResult replacement = reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-1", "hash-2"
        ));

        assertThat(replacement.snapshot().reservationKind())
                .isEqualTo(Reservation.Kind.REPLACEMENT);
        assertThat(replacement.snapshot().attemptGroupId())
                .isEqualTo(initial.snapshot().attemptGroupId());
        assertThat(count(TrialClaim.class)).isEqualTo(1);
        assertThat(count(EntitlementGrant.class)).isEqualTo(1);
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(2);
        assertThat(count(ReservationAllocation.class)).isEqualTo(1);
        assertThat(count(Reservation.class)).isEqualTo(2);
        assertThat(count(AttemptSession.class)).isEqualTo(2);
        assertThat(mongoTemplate.findById("session-1", AttemptSession.class).getState())
                .isEqualTo(AttemptSession.State.ABANDONED_RESTARTED);
    }

    @Test
    void replacementRejectsDifferentMockExamAndRollsBack() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult initial = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        ));
        prepareConfirmedOpenGroup(initial);

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-2", "hash-2"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("RESERVATION_STATE_CONFLICT");
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(IdempotencyCommand.class)).isEqualTo(1);
        assertThat(count(AttemptSession.class)).isEqualTo(1);
    }

    @Test
    void gradingGroupReturnsProcessingWithoutNewReservation() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult initial = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        ));
        prepareConfirmedGroup(initial, AttemptGroup.Status.GRADING);

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-1", "hash-2"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("COMMAND_PROCESSING");
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(IdempotencyCommand.class)).isEqualTo(1);
    }

    @Test
    void sessionConflictRollsBackClaimAndGrantCreatedEarlierInTransaction() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        attemptSessionRepository.insert(AttemptSession.proposed(
                "session-conflict", "group-existing", "subject-existing",
                "operation-existing", NOW
        ));

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-conflict", "mock-1", "hash-1"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("RESERVATION_STATE_CONFLICT");
        assertThat(count(TrialClaim.class)).isZero();
        assertThat(count(EntitlementGrant.class)).isZero();
        assertThat(count(EntitlementLedgerEntry.class)).isZero();
        assertThat(count(IdempotencyCommand.class)).isZero();
        assertThat(count(AttemptSession.class)).isEqualTo(1);
    }

    @Test
    void unknownCommitResultConvergesFromCommandSnapshot() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        AtomicBoolean first = new AtomicBoolean(true);
        MongoTransactionExecutor unknownCommitExecutor = new MongoTransactionExecutor(
                transactionManager
        ) {
            @Override
            public <T> T execute(Supplier<T> operation) {
                T result = super.execute(operation);
                if (first.getAndSet(false)) {
                    MongoException exception = new MongoException("simulated unknown commit result");
                    exception.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
                    throw exception;
                }
                return result;
            }
        };
        ReserveService service = serviceWith(unknownCommitExecutor);

        ReserveResult result = service.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        ));

        assertThat(result.replayed()).isTrue();
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(2);
    }

    @Test
    void transientTransactionErrorRetriesWithOneFinalAggregateSet() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        AtomicBoolean first = new AtomicBoolean(true);
        MongoTransactionExecutor transientExecutor = new MongoTransactionExecutor(
                transactionManager
        ) {
            @Override
            public <T> T execute(Supplier<T> operation) {
                if (first.getAndSet(false)) {
                    MongoException exception = new MongoException("simulated transient error");
                    exception.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                    throw exception;
                }
                return super.execute(operation);
            }
        };

        ReserveResult result = serviceWith(transientExecutor).reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        ));

        assertThat(result.replayed()).isFalse();
        assertThat(count(TrialClaim.class)).isEqualTo(1);
        assertThat(count(Reservation.class)).isEqualTo(1);
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(2);
    }

    private ReserveService serviceWith(MongoTransactionExecutor executor) {
        return new ReserveService(
                eligibilityRepository, claimRepository, aliasRepository, subjectLinkRepository,
                grantRepository, ledgerRepository, reservationRepository, allocationRepository,
                commandRepository, attemptGroupRepository, attemptSessionRepository, executor,
                eligibilityProperties, reservationProperties, reserveMetrics, clock
        );
    }

    private void prepareConfirmedOpenGroup(ReserveResult initial) {
        prepareConfirmedGroup(initial, AttemptGroup.Status.OPEN);
    }

    private void prepareConfirmedGroup(ReserveResult initial, AttemptGroup.Status status) {
        mongoTemplate.updateMulti(new Query(), new Update()
                .unset("activeGuard")
                .set("status", Reservation.Status.CONFIRMED), Reservation.class);
        mongoTemplate.updateMulti(new Query(), new Update()
                .set("active", false), IdempotencyCommand.class);
        mongoTemplate.updateMulti(new Query(), new Update()
                .set("heldUnits", 0)
                .set("consumedUnits", 1), EntitlementGrant.class);
        TrialClaim claim = mongoTemplate.findAll(TrialClaim.class).getFirst();
        attemptGroupRepository.insert(AttemptGroup.projection(
                initial.snapshot().attemptGroupId(), claim.getSubjectRefId(),
                claim.getTrialClaimId(), "consumption-ledger-1", "mock-1",
                status, NOW
        ));
    }

    private void prepareCanceledInitial() {
        mongoTemplate.updateMulti(new Query(), new Update()
                .unset("activeGuard")
                .set("status", Reservation.Status.CANCELED), Reservation.class);
        mongoTemplate.updateMulti(new Query(), new Update()
                .set("active", false), IdempotencyCommand.class);
        mongoTemplate.updateMulti(new Query(), new Update()
                .set("availableUnits", 1)
                .set("heldUnits", 0), EntitlementGrant.class);
        mongoTemplate.updateMulti(new Query(), new Update()
                .unset("activeGuard")
                .set("state", AttemptSession.State.FAILED), AttemptSession.class);
    }

    private void applyVerified(String userId, long revision, String eventId, String candidate) {
        String json = """
                {
                  "eventId":"%s",
                  "eventType":"PhoneEligibilityBindingVerified",
                  "schemaVersion":1,
                  "producer":"identity",
                  "occurredAt":"2026-08-28T00:00:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "userId":"%s",
                  "verifiedAt":"2026-08-27T23:59:59Z",
                  "bindingRevision":%d,
                  "fingerprintCandidates":[{"keyVersion":"v1","value":"%s"}]
                }
                """.formatted(eventId, userId, revision, candidate);
        eligibilityService.process(eligibilityDecoder.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    private void applyRevoked(String userId, long revision, String eventId) {
        String json = """
                {
                  "eventId":"%s",
                  "eventType":"PhoneEligibilityBindingRevoked",
                  "schemaVersion":1,
                  "producer":"identity",
                  "occurredAt":"2026-08-28T00:00:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "userId":"%s",
                  "revokedAt":"2026-08-28T00:00:00Z",
                  "bindingRevision":%d
                }
                """.formatted(eventId, userId, revision);
        eligibilityService.process(eligibilityDecoder.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    private void applyVerifiedWithRotation(
            String userId,
            long revision,
            String eventId,
            String oldCandidate,
            String newCandidate
    ) {
        String json = """
                {
                  "eventId":"%s",
                  "eventType":"PhoneEligibilityBindingVerified",
                  "schemaVersion":1,
                  "producer":"identity",
                  "occurredAt":"2026-08-28T00:00:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "userId":"%s",
                  "verifiedAt":"2026-08-27T23:59:59Z",
                  "bindingRevision":%d,
                  "fingerprintCandidates":[
                    {"keyVersion":"v1","value":"%s"},
                    {"keyVersion":"v2","value":"%s"}
                  ]
                }
                """.formatted(eventId, userId, revision, oldCandidate, newCandidate);
        eligibilityService.process(eligibilityDecoder.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static ReserveCommand command(
            String userId,
            String operationId,
            String sessionId,
            String mockExamId,
            String hash
    ) {
        return new ReserveCommand(operationId, userId, sessionId, mockExamId, hash);
    }

    private long count(Class<?> type) {
        return mongoTemplate.count(new Query(), type);
    }

    private Map<String, Document> indexes(String collection) {
        java.util.HashMap<String, Document> indexes = new java.util.HashMap<>();
        for (Document index : mongoTemplate.getCollection(collection).listIndexes()) {
            indexes.put(index.getString("name"), index);
        }
        return indexes;
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
