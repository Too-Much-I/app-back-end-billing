package web.tosunsaeng.billing.domain.reservation.repository;

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

import web.tosunsaeng.billing.domain.reservation.config.ReservationProperties;
import web.tosunsaeng.billing.domain.benefit.application.BenefitCatalog;
import web.tosunsaeng.billing.domain.benefit.config.BenefitCatalogInitializer;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.BillingMongoIndexInitializer;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;
import web.tosunsaeng.billing.domain.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.domain.reservation.application.ReserveMetrics;
import web.tosunsaeng.billing.domain.reservation.application.ReserveResult;
import web.tosunsaeng.billing.domain.reservation.application.ReserveService;
import web.tosunsaeng.billing.domain.reservation.application.CancelCommand;
import web.tosunsaeng.billing.domain.reservation.application.ConfirmCommand;
import web.tosunsaeng.billing.domain.reservation.application.LifecycleResult;
import web.tosunsaeng.billing.domain.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.domain.reservation.application.ReservationLifecycleMetrics;
import web.tosunsaeng.billing.domain.reservation.application.ReservationStatusResult;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementLedgerEntry;
import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.domain.entity.ReservationAllocation;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialCandidateAlias;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptSessionRepository;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementGrantRepository;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementLedgerRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialCandidateAliasRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.api.TrialEligibilityEventDecoder;
import web.tosunsaeng.billing.domain.eligibility.trial.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.InboundEventInbox;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository;

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
        registry.add("billing.mongodb.schema-version", () -> "3");
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
    private BenefitCatalog benefitCatalog;
    @Autowired
    private ReservationLifecycleService lifecycleService;
    @Autowired
    private BillingMongoIndexInitializer indexInitializer;
    @Autowired
    private BenefitCatalogInitializer benefitCatalogInitializer;
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
    @Autowired
    private ReservationLifecycleMetrics lifecycleMetrics;

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
        mongoTemplate.save(BenefitDefinition.freeExamOnce(NOW));
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
        assertThat(new java.util.ArrayList<>(indexes("trial_candidate_aliases")
                .get("ux_active_trial_candidate").get("key", Document.class).keySet()))
                .containsExactly("benefitCode", "keyVersion", "candidate");
        assertThat(new java.util.ArrayList<>(indexes("entitlement_grants")
                .get("ux_grant_source_type").get("key", Document.class).keySet()))
                .containsExactly("sourceType", "sourceId", "benefitCode");
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
    void seedsApprovedBenefitDefinitionAndRerunsWithoutChangingIt() throws Exception {
        BenefitDefinition before = mongoTemplate.findById(
                BenefitDefinition.FREE_EXAM_ONCE, BenefitDefinition.class
        );

        benefitCatalogInitializer.run(new DefaultApplicationArguments(new String[0]));
        BenefitDefinition after = mongoTemplate.findById(
                BenefitDefinition.FREE_EXAM_ONCE, BenefitDefinition.class
        );

        assertThat(before).isEqualTo(after);
        assertThat(after).isNotNull();
        assertThat(after.hasApprovedFreeExamOncePolicy()).isTrue();
        assertThat(mongoTemplate.getCollection("benefit_definitions").countDocuments()).isOne();
    }

    @Test
    void catalogInitializerFailsFastOnPolicyDriftWithoutOverwritingIt() {
        mongoTemplate.getCollection("benefit_definitions").updateOne(
                new Document("_id", BenefitDefinition.FREE_EXAM_ONCE),
                new Document("$set", new Document("defaultGrantUnits", 2))
        );

        assertThatThrownBy(() -> benefitCatalogInitializer.run(
                new DefaultApplicationArguments(new String[0])
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy drift")
                .hasMessageNotContaining(USER_ONE)
                .hasMessageNotContaining(CANDIDATE);
        assertThat(mongoTemplate.getCollection("benefit_definitions")
                .find(new Document("_id", BenefitDefinition.FREE_EXAM_ONCE))
                .first().getInteger("defaultGrantUnits")).isEqualTo(2);
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
        TrialCandidateAlias alias = mongoTemplate.findAll(TrialCandidateAlias.class).getFirst();
        assertThat(claim.getClaimedAt()).isEqualTo(NOW);
        assertThat(claim.getRetentionExpiresAt()).isEqualTo(
                Instant.parse("2029-08-28T00:00:00Z")
        );
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(claim.getBenefitCode()).isEqualTo(BenefitDefinition.FREE_EXAM_ONCE);
        assertThat(alias.getBenefitCode()).isEqualTo(claim.getBenefitCode());
        assertThat(grant.getBenefitCode()).isEqualTo(claim.getBenefitCode());
        assertThat(mongoTemplate.getCollection("trial_claims").find().first())
                .containsKey("benefitCode")
                .doesNotContainKey("benefitType");
        assertThat(mongoTemplate.getCollection("trial_candidate_aliases").find().first())
                .containsKey("benefitCode")
                .doesNotContainKey("benefitType");
        assertThat(mongoTemplate.getCollection("entitlement_grants").find().first())
                .containsKey("benefitCode")
                .doesNotContainKey("grantType");
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
    void missingBenefitDefinitionRollsBackAllReserveWrites() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        mongoTemplate.remove(
                Query.query(org.springframework.data.mongodb.core.query.Criteria
                        .where("_id").is(BenefitDefinition.FREE_EXAM_ONCE)),
                BenefitDefinition.class
        );

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(count(TrialClaim.class)).isZero();
        assertThat(count(EntitlementGrant.class)).isZero();
        assertThat(count(Reservation.class)).isZero();
        assertThat(count(IdempotencyCommand.class)).isZero();
    }

    @Test
    void inactiveBenefitDefinitionRollsBackAllReserveWrites() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        mongoTemplate.getCollection("benefit_definitions").updateOne(
                new Document("_id", BenefitDefinition.FREE_EXAM_ONCE),
                new Document("$set", new Document("active", false))
        );

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(count(TrialClaim.class)).isZero();
        assertThat(count(EntitlementGrant.class)).isZero();
        assertThat(count(Reservation.class)).isZero();
        assertThat(count(IdempotencyCommand.class)).isZero();
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
    void existingClaimWithMismatchedGrantBenefitFailsClosed() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        reserveService.reserve(command(USER_ONE, OP_ONE, "session-1", "mock-1", "hash-1"));
        prepareCanceledInitial();
        mongoTemplate.updateMulti(
                new Query(), Update.update("benefitCode", "OTHER_BENEFIT"),
                EntitlementGrant.class
        );

        assertThatThrownBy(() -> reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-1", "hash-2"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(count(TrialClaim.class)).isOne();
        assertThat(count(EntitlementGrant.class)).isOne();
        assertThat(count(Reservation.class)).isOne();
        assertThat(count(IdempotencyCommand.class)).isOne();
    }

    @Test
    void expiredAliasIsFencedBeforeCreatingNewClaim() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        aliasRepository.insert(TrialCandidateAlias.active(
                "alias-expired", BenefitDefinition.FREE_EXAM_ONCE,
                "v1", CANDIDATE, "claim-expired",
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

    @Test
    void initialConfirmConsumesHeldGrantAndOpensAttemptGroup() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        LifecycleResult result = lifecycleService.confirm(confirmCommand(reserved, "confirm-hash"));

        assertThat(result.snapshot().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(result.snapshot().attemptGroupStatus()).isEqualTo(AttemptGroup.Status.OPEN);
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits()).isZero();
        assertThat(grant.getHeldUnits()).isZero();
        assertThat(grant.getConsumedUnits()).isOne();
        assertThat(mongoTemplate.findAll(ReservationAllocation.class).getFirst().getStatus())
                .isEqualTo(ReservationAllocation.Status.CONSUMED);
        assertThat(mongoTemplate.findById("session-1", AttemptSession.class).getState())
                .isEqualTo(AttemptSession.State.ACTIVE);
        assertThat(count(AttemptGroup.class)).isOne();
        assertThat(mongoTemplate.findAll(EntitlementLedgerEntry.class))
                .extracting(EntitlementLedgerEntry::getEventType)
                .containsExactlyInAnyOrder(
                        EntitlementLedgerEntry.EventType.GRANTED,
                        EntitlementLedgerEntry.EventType.RESERVED,
                        EntitlementLedgerEntry.EventType.CONSUMED
                );

        LifecycleResult replay = lifecycleService.confirm(confirmCommand(reserved, "confirm-hash"));
        assertThat(replay.replayed()).isTrue();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
        assertThatThrownBy(() -> lifecycleService.confirm(confirmCommand(
                reserved, "different-hash"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void initialCancelReleasesHoldWithoutDeletingClaim() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        LifecycleResult result = lifecycleService.cancel(cancelCommand(reserved, "cancel-hash"));

        assertThat(result.snapshot().reservationStatus()).isEqualTo(Reservation.Status.CANCELED);
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits()).isOne();
        assertThat(grant.getHeldUnits()).isZero();
        assertThat(grant.getConsumedUnits()).isZero();
        assertThat(mongoTemplate.findAll(ReservationAllocation.class).getFirst().getStatus())
                .isEqualTo(ReservationAllocation.Status.RELEASED);
        assertThat(mongoTemplate.findById("session-1", AttemptSession.class).getState())
                .isEqualTo(AttemptSession.State.FAILED);
        assertThat(count(TrialClaim.class)).isOne();
        assertThat(count(AttemptGroup.class)).isZero();
    }

    @Test
    void dueInitialReservationExpiresAndStatusIsReadOnly() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        assertThat(lifecycleService.expire(
                reserved.snapshot().reservationId(), NOW.plusSeconds(301)
        )).isTrue();
        long commandsBefore = count(IdempotencyCommand.class);
        long ledgerBefore = count(EntitlementLedgerEntry.class);
        ReservationStatusResult status = lifecycleService.status(USER_ONE, OP_ONE);

        assertThat(status.reservationStatus()).isEqualTo(Reservation.Status.EXPIRED);
        assertThat(status.attemptGroupStatus()).isNull();
        assertThat(status.terminalAt()).isEqualTo(NOW.plusSeconds(301));
        assertThat(count(IdempotencyCommand.class)).isEqualTo(commandsBefore);
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(ledgerBefore);
        assertThat(lifecycleService.expire(
                reserved.snapshot().reservationId(), NOW.plusSeconds(302)
        )).isFalse();
    }

    @Test
    void replacementConfirmReusesConsumptionWithoutNewAllocationOrLedger() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult initial = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash-1"
        ));
        lifecycleService.confirm(confirmCommand(initial, "confirm-hash-1"));
        mongoTemplate.updateFirst(
                Query.query(org.springframework.data.mongodb.core.query.Criteria.where(
                        "attemptGroupId"
                ).is(initial.snapshot().attemptGroupId())),
                new Update().set("status", AttemptGroup.Status.RETAKE_AVAILABLE),
                AttemptGroup.class
        );
        ReserveResult replacement = reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-1", "reserve-hash-2"
        ));

        LifecycleResult result = lifecycleService.confirm(new ConfirmCommand(
                OP_TWO, replacement.snapshot().reservationId(), USER_ONE, "session-2",
                NOW, "confirm-hash-2"
        ));

        assertThat(replacement.snapshot().reservationKind()).isEqualTo(Reservation.Kind.REPLACEMENT);
        assertThat(result.snapshot().reservationStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(count(ReservationAllocation.class)).isOne();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
        assertThat(count(AttemptGroup.class)).isOne();
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getConsumedUnits()).isOne();
        assertThat(mongoTemplate.findById("session-2", AttemptSession.class).getState())
                .isEqualTo(AttemptSession.State.ACTIVE);
        assertThat(attemptGroupRepository.findById(initial.snapshot().attemptGroupId())
                .orElseThrow().getStatus()).isEqualTo(AttemptGroup.Status.OPEN);
    }

    @Test
    void replacementCancelAndExpiryDoNotRestoreConsumedGrant() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult initial = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash-1"
        ));
        lifecycleService.confirm(confirmCommand(initial, "confirm-hash-1"));
        ReserveResult canceledReplacement = reserveService.reserve(command(
                USER_ONE, OP_TWO, "session-2", "mock-1", "reserve-hash-2"
        ));

        lifecycleService.cancel(new CancelCommand(
                OP_TWO, canceledReplacement.snapshot().reservationId(), USER_ONE,
                web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.CALLER_ABORTED,
                "cancel-hash-2"
        ));

        assertConsumedGrantUnchanged();
        String operationThree = "2993287e-72d9-4acf-956f-04dbd46b197d";
        ReserveResult expiredReplacement = reserveService.reserve(command(
                USER_ONE, operationThree, "session-3", "mock-1", "reserve-hash-3"
        ));
        assertThat(lifecycleService.expire(
                expiredReplacement.snapshot().reservationId(), NOW.plusSeconds(301)
        )).isTrue();

        assertConsumedGrantUnchanged();
        assertThat(count(ReservationAllocation.class)).isOne();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
        assertThat(attemptGroupRepository.findById(initial.snapshot().attemptGroupId()))
                .get().extracting(AttemptGroup::getStatus).isEqualTo(AttemptGroup.Status.OPEN);
    }

    @Test
    void terminalReservationRejectsOppositeCommandWithoutRepair() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult confirmed = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));
        lifecycleService.confirm(confirmCommand(confirmed, "confirm-hash"));

        assertThatThrownBy(() -> lifecycleService.cancel(cancelCommand(
                confirmed, "cancel-after-confirm"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("RESERVATION_STATE_CONFLICT");

        String userThree = "6f7c0278-3374-40eb-8ece-a93c6a27a943";
        String candidateThree = "C".repeat(43);
        String operationThree = "2993287e-72d9-4acf-956f-04dbd46b197d";
        applyVerified(userThree, 1, "00000000-0000-4000-8000-000000000003", candidateThree);
        ReserveResult canceled = reserveService.reserve(command(
                userThree, operationThree, "session-3", "mock-3", "reserve-hash-3"
        ));
        lifecycleService.cancel(new CancelCommand(
                operationThree, canceled.snapshot().reservationId(), userThree,
                web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.CALLER_ABORTED,
                "cancel-hash-3"
        ));
        assertThatThrownBy(() -> lifecycleService.confirm(new ConfirmCommand(
                operationThree, canceled.snapshot().reservationId(), userThree, "session-3",
                NOW, "confirm-after-cancel"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("RESERVATION_STATE_CONFLICT");
    }

    @Test
    void confirmRejectsMismatchedUserAndSessionWithoutWritingCommand() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        assertThatThrownBy(() -> lifecycleService.confirm(new ConfirmCommand(
                OP_ONE, reserved.snapshot().reservationId(), USER_TWO, "session-1", NOW,
                "wrong-user-hash"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("RESERVATION_STATE_CONFLICT");
        assertThatThrownBy(() -> lifecycleService.confirm(new ConfirmCommand(
                OP_ONE, reserved.snapshot().reservationId(), USER_ONE, "session-other", NOW,
                "wrong-session-hash"
        )))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("RESERVATION_STATE_CONFLICT");
        assertThat(count(IdempotencyCommand.class)).isOne();
        assertThat(reservationRepository.findById(reserved.snapshot().reservationId()))
                .get().extracting(Reservation::getStatus).isEqualTo(Reservation.Status.RESERVED);
    }

    @Test
    void statusMissingOperationIs404AndTerminalCommandsRetainForSevenDays() {
        assertThatThrownBy(() -> lifecycleService.status(USER_ONE, OP_ONE))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("OPERATION_NOT_FOUND");
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));
        lifecycleService.cancel(cancelCommand(reserved, "cancel-hash"));

        assertThat(mongoTemplate.findAll(IdempotencyCommand.class))
                .allSatisfy(command -> {
                    assertThat(command.isActive()).isFalse();
                    assertThat(command.getTerminalAt()).isEqualTo(NOW);
                    assertThat(command.getPurgeAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 60 * 60));
                });
    }

    @Test
    void concurrentExpiryWorkersReleaseOnlyOnceAndNonDueIsNoOp() throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));
        assertThat(lifecycleService.expire(
                reserved.snapshot().reservationId(), NOW.plusSeconds(299)
        )).isFalse();

        race(
                () -> lifecycleService.expire(
                        reserved.snapshot().reservationId(), NOW.plusSeconds(301)
                ),
                () -> lifecycleService.expire(
                        reserved.snapshot().reservationId(), NOW.plusSeconds(301)
                )
        );

        assertThat(reservationRepository.findById(reserved.snapshot().reservationId()))
                .get().extracting(Reservation::getStatus).isEqualTo(Reservation.Status.EXPIRED);
        assertThat(mongoTemplate.findAll(EntitlementLedgerEntry.class))
                .filteredOn(entry -> entry.getEventType() == EntitlementLedgerEntry.EventType.RELEASED)
                .hasSize(1);
        assertThat(mongoTemplate.findAll(EntitlementGrant.class).getFirst().getAvailableUnits())
                .isOne();
    }

    @Test
    void concurrentCancelAndExpiryReleaseExactlyOnce() throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        race(
                () -> lifecycleService.cancel(cancelCommand(reserved, "cancel-hash")),
                () -> lifecycleService.expire(
                        reserved.snapshot().reservationId(), NOW.plusSeconds(301)
                )
        );

        assertThat(reservationRepository.findById(reserved.snapshot().reservationId()))
                .get().extracting(Reservation::getStatus)
                .isIn(Reservation.Status.CANCELED, Reservation.Status.EXPIRED);
        assertThat(mongoTemplate.findAll(EntitlementLedgerEntry.class))
                .filteredOn(entry -> entry.getEventType() == EntitlementLedgerEntry.EventType.RELEASED)
                .hasSize(1);
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits()).isOne();
        assertThat(grant.getHeldUnits()).isZero();
    }

    @Test
    void lifecycleTransientTransactionErrorRetries() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));
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

        LifecycleResult result = lifecycleServiceWith(transientExecutor)
                .confirm(confirmCommand(reserved, "confirm-hash"));

        assertThat(result.replayed()).isFalse();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
    }

    @Test
    void concurrentConfirmAndCancelHaveExactlyOneTerminalWinner() throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        List<Object> results = race(
                () -> lifecycleService.confirm(confirmCommand(reserved, "confirm-hash")),
                () -> lifecycleService.cancel(cancelCommand(reserved, "cancel-hash"))
        );

        assertThat(results).anyMatch(LifecycleResult.class::isInstance);
        assertThat(results).anyMatch(value -> value instanceof InternalApiException exception
                && "RESERVATION_STATE_CONFLICT".equals(exception.code()));
        Reservation reservation = reservationRepository.findById(
                reserved.snapshot().reservationId()
        ).orElseThrow();
        assertThat(reservation.getStatus()).isIn(
                Reservation.Status.CONFIRMED, Reservation.Status.CANCELED
        );
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits() + grant.getHeldUnits() + grant.getConsumedUnits())
                .isOne();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
    }

    @Test
    void concurrentSameConfirmReplaysOneCommittedResult() throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        List<Object> results = race(
                () -> lifecycleService.confirm(confirmCommand(reserved, "confirm-hash")),
                () -> lifecycleService.confirm(confirmCommand(reserved, "confirm-hash"))
        );

        assertThat(results).allMatch(LifecycleResult.class::isInstance);
        assertThat(results.stream().map(LifecycleResult.class::cast)
                .filter(LifecycleResult::replayed).count()).isOne();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
        assertThat(count(AttemptGroup.class)).isOne();
    }

    @Test
    void confirmUnknownCommitConvergesFromLifecycleSnapshot() {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));
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

        LifecycleResult result = lifecycleServiceWith(unknownCommitExecutor)
                .confirm(confirmCommand(reserved, "confirm-hash"));

        assertThat(result.replayed()).isTrue();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
        assertThat(count(AttemptGroup.class)).isOne();
    }

    @Test
    void confirmAndExpiryRaceHasOneTerminalStateAndConservedGrant() throws Exception {
        applyVerified(USER_ONE, 1, "00000000-0000-4000-8000-000000000001", CANDIDATE);
        ReserveResult reserved = reserveService.reserve(command(
                USER_ONE, OP_ONE, "session-1", "mock-1", "reserve-hash"
        ));

        List<Object> results = race(
                () -> lifecycleService.confirm(confirmCommand(reserved, "confirm-hash")),
                () -> lifecycleService.expire(
                        reserved.snapshot().reservationId(), NOW.plusSeconds(301)
                )
        );

        Reservation reservation = reservationRepository.findById(
                reserved.snapshot().reservationId()
        ).orElseThrow();
        assertThat(reservation.getStatus()).isIn(
                Reservation.Status.CONFIRMED, Reservation.Status.EXPIRED
        );
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits() + grant.getHeldUnits() + grant.getConsumedUnits())
                .isOne();
        assertThat(count(EntitlementLedgerEntry.class)).isEqualTo(3);
        assertThat(results).hasSize(2);
    }

    private ReserveService serviceWith(MongoTransactionExecutor executor) {
        return new ReserveService(
                eligibilityRepository, benefitCatalog, claimRepository, aliasRepository,
                subjectLinkRepository,
                grantRepository, ledgerRepository, reservationRepository, allocationRepository,
                commandRepository, attemptGroupRepository, attemptSessionRepository, executor,
                eligibilityProperties, reservationProperties, reserveMetrics, clock
        );
    }

    private ReservationLifecycleService lifecycleServiceWith(MongoTransactionExecutor executor) {
        return new ReservationLifecycleService(
                reservationRepository, allocationRepository, grantRepository, ledgerRepository,
                attemptGroupRepository, attemptSessionRepository, commandRepository, executor,
                reservationProperties, lifecycleMetrics, clock
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

    private static ConfirmCommand confirmCommand(ReserveResult reserved, String hash) {
        return new ConfirmCommand(
                reserved.snapshot().operationId(), reserved.snapshot().reservationId(), USER_ONE,
                reserved.snapshot().sessionId(), NOW, hash
        );
    }

    private static CancelCommand cancelCommand(ReserveResult reserved, String hash) {
        return new CancelCommand(
                reserved.snapshot().operationId(), reserved.snapshot().reservationId(), USER_ONE,
                web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest.Reason.CALLER_ABORTED, hash
        );
    }

    private void assertConsumedGrantUnchanged() {
        EntitlementGrant grant = mongoTemplate.findAll(EntitlementGrant.class).getFirst();
        assertThat(grant.getAvailableUnits()).isZero();
        assertThat(grant.getHeldUnits()).isZero();
        assertThat(grant.getConsumedUnits()).isOne();
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
