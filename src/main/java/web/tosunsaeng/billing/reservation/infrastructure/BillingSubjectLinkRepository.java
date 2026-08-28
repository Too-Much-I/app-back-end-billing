package web.tosunsaeng.billing.reservation.infrastructure;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.BillingSubjectLink;

@Repository
public class BillingSubjectLinkRepository {

    private final MongoTemplate mongoTemplate;

    public BillingSubjectLinkRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<BillingSubjectLink> findByClaim(String trialClaimId) {
        Query query = Query.query(Criteria.where("trialClaimId").is(trialClaimId));
        return Optional.ofNullable(mongoTemplate.findOne(query, BillingSubjectLink.class));
    }

    public BillingSubjectLink insert(BillingSubjectLink link) {
        return mongoTemplate.insert(link);
    }
}
