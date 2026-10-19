package web.tosunsaeng.billing.domain.entitlement.trial.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;

@Repository
public class TrialClaimRepository {

    private final MongoOperations mongoTemplate;

    public TrialClaimRepository(MongoOperations mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<TrialClaim> findById(String trialClaimId) {
        return Optional.ofNullable(mongoTemplate.findById(trialClaimId, TrialClaim.class));
    }

    public TrialClaim insert(TrialClaim claim) {
        return mongoTemplate.insert(claim);
    }
}
