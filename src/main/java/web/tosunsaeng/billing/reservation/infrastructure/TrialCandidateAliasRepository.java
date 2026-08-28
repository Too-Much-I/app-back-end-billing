package web.tosunsaeng.billing.reservation.infrastructure;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.TrialCandidateAlias;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityCandidate;

@Repository
public class TrialCandidateAliasRepository {

    private final MongoTemplate mongoTemplate;

    public TrialCandidateAliasRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void deactivateExpiredMatches(
            List<TrialEligibilityCandidate> candidates,
            Instant now
    ) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("benefitType").is("FREE_EXAM_ONCE"),
                Criteria.where("active").is(true),
                Criteria.where("retentionExpiresAt").lte(now),
                candidateCriteria(candidates)
        ));
        mongoTemplate.updateMulti(query, Update.update("active", false), TrialCandidateAlias.class);
    }

    public List<TrialCandidateAlias> findActiveMatches(
            List<TrialEligibilityCandidate> candidates,
            Instant now
    ) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("benefitType").is("FREE_EXAM_ONCE"),
                Criteria.where("active").is(true),
                Criteria.where("retentionExpiresAt").gt(now),
                candidateCriteria(candidates)
        ));
        return mongoTemplate.find(query, TrialCandidateAlias.class);
    }

    public List<TrialCandidateAlias> findActiveByClaim(String trialClaimId, Instant now) {
        Query query = Query.query(Criteria.where("trialClaimId").is(trialClaimId)
                .and("active").is(true)
                .and("retentionExpiresAt").gt(now));
        return mongoTemplate.find(query, TrialCandidateAlias.class);
    }

    public TrialCandidateAlias insert(TrialCandidateAlias alias) {
        return mongoTemplate.insert(alias);
    }

    private static Criteria candidateCriteria(List<TrialEligibilityCandidate> candidates) {
        Criteria[] values = candidates.stream()
                .map(candidate -> new Criteria().andOperator(
                        Criteria.where("keyVersion").is(candidate.keyVersion()),
                        Criteria.where("candidate").is(candidate.value())
                ))
                .toArray(Criteria[]::new);
        return new Criteria().orOperator(values);
    }
}
