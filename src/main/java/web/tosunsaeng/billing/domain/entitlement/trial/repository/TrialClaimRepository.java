package web.tosunsaeng.billing.domain.entitlement.trial.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;

@Repository
public class TrialClaimRepository {

    private final MongoTemplate mongoTemplate;

    public TrialClaimRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<TrialClaim> findById(String trialClaimId) {
        return Optional.ofNullable(mongoTemplate.findById(trialClaimId, TrialClaim.class));
    }

    public TrialClaim insert(TrialClaim claim) {
        return mongoTemplate.insert(claim);
    }
}
