package web.tosunsaeng.billing.domain.entitlement.repository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.billing.domain.entitlement.application.EntitlementQuerySnapshot;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.domain.entitlement.trial.application.TrialEntitlementPolicy;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.*;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.domain.reservation.domain.entity.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;

/** Read-only, bounded batches. All calls must be inside EntitlementSnapshotExecutor. */
@Repository
public class EntitlementQueryRepository {
    private final MongoTemplate mongo;
    private final TrialEligibilityProperties properties;
    public EntitlementQueryRepository(MongoTemplate mongo, TrialEligibilityProperties properties) {
        this.mongo = mongo; this.properties = properties;
    }
    public EntitlementQuerySnapshot read(String userId, Instant now) {
        String code = BenefitDefinition.FREE_EXAM_ONCE;
        String scope = properties.getExpectedConsumerScopeId();
        var definitions = find(Criteria.where("benefitCode").is(code), BenefitDefinition.class, 1);
        require(definitions.size() == 1 && definitions.getFirst().hasApprovedFreeExamOncePolicy());
        var eligibilityRows = find(Criteria.where("consumerScopeId").is(scope).and("userId").is(userId), TrialEligibility.class, 1);
        var eligibility = eligibilityRows.isEmpty() ? null : eligibilityRows.getFirst();
        List<BillingSubjectLink> owned = find(Criteria.where("userId").is(userId).and("active").is(true)
                .and("consumerScopeId").is(scope).and("retentionExpiresAt").gt(now), BillingSubjectLink.class, 100);
        Set<String> candidateClaims = new HashSet<>();
        if (TrialEntitlementPolicy.verified(eligibility)) {
            var candidates = eligibility.getCandidates().stream().map(c -> Criteria.where("keyVersion").is(c.keyVersion())
                    .and("candidate").is(c.value())).toArray(Criteria[]::new);
            var aliases = find(new Criteria().andOperator(Criteria.where("benefitCode").is(code)
                    .and("active").is(true).and("retentionExpiresAt").gt(now), new Criteria().orOperator(candidates)),
                    TrialCandidateAlias.class, 100);
            aliases.forEach(a -> candidateClaims.add(a.getTrialClaimId()));
        }
        require(candidateClaims.size() <= 1);
        Set<String> claimIds = owned.stream().map(BillingSubjectLink::getTrialClaimId).collect(Collectors.toSet());
        claimIds.addAll(candidateClaims);
        if (claimIds.size() > 100) { throw new EntitlementQueryException(EntitlementQueryException.Failure.LIMIT); }
        var claims = batch("_id", claimIds, TrialClaim.class, 100);
        var links = batch("trialClaimId", claimIds, BillingSubjectLink.class, 100);
        var freeClaims = claims.stream().filter(c -> code.equals(c.getBenefitCode())).toList();
        Set<String> freeIds = freeClaims.stream().map(TrialClaim::getTrialClaimId).collect(Collectors.toSet());
        // Every owned link must resolve; foreign benefit rows are not silently turned into free rights.
        require(claims.size() == claimIds.size() && links.size() == claimIds.size());
        require(candidateClaims.isEmpty() || freeIds.containsAll(candidateClaims));
        var freeLinks = links.stream().filter(l -> freeIds.contains(l.getTrialClaimId())).toList();
        require(freeLinks.stream().allMatch(l -> scope.equals(l.getConsumerScopeId())));
        var grants = freeIds.isEmpty() ? List.<EntitlementGrant>of() : find(Criteria.where("sourceType").is("TRIAL_CLAIM")
                .and("sourceId").in(freeIds).and("benefitCode").is(code), EntitlementGrant.class, 100);
        var groups = batch("trialClaimId", freeIds, AttemptGroup.class, 100);
        var subjects = freeLinks.stream().map(BillingSubjectLink::getSubjectRefId).toList();
        var activeReservations = subjects.isEmpty() ? List.<Reservation>of() : find(Criteria.where("subjectRefId").in(subjects)
                .and("status").is(Reservation.Status.RESERVED), Reservation.class, 100);
        Set<String> sessionIds = groups.stream().map(AttemptGroup::getActiveSessionId).filter(Objects::nonNull).collect(Collectors.toSet());
        activeReservations.forEach(r -> sessionIds.add(r.getProposedSessionId()));
        var sessions = batch("_id", sessionIds, AttemptSession.class, 200);
        // Use the existing subject+operation unique index, never scan all historical reservations.
        var keys = sessions.stream().map(s -> Criteria.where("subjectRefId").is(s.getSubjectRefId())
                .and("operationId").is(s.getOperationId())).toArray(Criteria[]::new);
        var associated = keys.length == 0 ? List.<Reservation>of()
                : find(new Criteria().orOperator(keys), Reservation.class, 200);
        Map<String, Reservation> reservations = new HashMap<>();
        associated.forEach(r -> reservations.put(r.getReservationId(), r));
        activeReservations.forEach(r -> reservations.put(r.getReservationId(), r));
        var commands = find(Criteria.where("callerService").is("LEARNING_CORE").and("userId").is(userId)
                .and("commandType").is("RESERVE").and("active").is(true), IdempotencyCommand.class, 1);
        boolean processing = commands.stream().anyMatch(c -> c.getState() == IdempotencyCommand.State.PROCESSING);
        return new EntitlementQuerySnapshot(userId, now, definitions.getFirst(), eligibility,
                candidateClaims.stream().findFirst().orElse(null), freeClaims, freeLinks, grants, groups,
                List.copyOf(reservations.values()), sessions, processing);
    }
    private <T> List<T> batch(String key, Collection<String> ids, Class<T> type, int cap) {
        return ids.isEmpty() ? List.of() : find(Criteria.where(key).in(ids), type, cap);
    }
    private <T> List<T> find(Criteria criteria, Class<T> type, int cap) {
        List<T> result = mongo.find(Query.query(criteria).limit(cap + 1)
                .maxTime(EntitlementSnapshotExecutor.remaining()), type);
        if (result.size() > cap) { throw new EntitlementQueryException(EntitlementQueryException.Failure.LIMIT); }
        return result;
    }
}
