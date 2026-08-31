package web.tosunsaeng.billing.domain.eligibility.trial.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;

@Repository
public class TrialEligibilityRepository {

    private final MongoTemplate mongoTemplate;

    public TrialEligibilityRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<TrialEligibility> findByScopeAndUser(String consumerScopeId, String userId) {
        Query query = Query.query(Criteria.where("consumerScopeId").is(consumerScopeId)
                .and("userId").is(userId));
        return Optional.ofNullable(mongoTemplate.findOne(query, TrialEligibility.class));
    }

    public TrialEligibility replace(TrialEligibility eligibility) {
        Query query = Query.query(Criteria.where("consumerScopeId")
                .is(eligibility.getConsumerScopeId())
                .and("userId").is(eligibility.getUserId()));
        FindAndReplaceOptions options = FindAndReplaceOptions.options().upsert().returnNew();
        TrialEligibility replaced = mongoTemplate.findAndReplace(query, eligibility, options);
        if (replaced == null) {
            throw new IllegalStateException("Trial eligibility replacement returned no document.");
        }
        return replaced;
    }
}
