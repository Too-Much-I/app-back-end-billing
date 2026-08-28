package web.tosunsaeng.billing.reservation.infrastructure;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.EntitlementGrant;

@Repository
public class EntitlementGrantRepository {

    private final MongoTemplate mongoTemplate;

    public EntitlementGrantRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<EntitlementGrant> findFreeGrantByClaim(String trialClaimId) {
        Query query = Query.query(Criteria.where("sourceType").is("TRIAL_CLAIM")
                .and("sourceId").is(trialClaimId)
                .and("grantType").is("FREE_EXAM_ONCE"));
        return Optional.ofNullable(mongoTemplate.findOne(query, EntitlementGrant.class));
    }

    public EntitlementGrant insert(EntitlementGrant grant) {
        return mongoTemplate.insert(grant);
    }

    public Optional<EntitlementGrant> findById(String grantId) {
        return Optional.ofNullable(mongoTemplate.findById(grantId, EntitlementGrant.class));
    }

    public Optional<EntitlementGrant> holdOne(String grantId, Instant now) {
        Query query = Query.query(Criteria.where("grantId").is(grantId)
                .and("state").is("ACTIVE")
                .and("availableUnits").gte(1));
        Update update = new Update()
                .inc("availableUnits", -1)
                .inc("heldUnits", 1)
                .inc("version", 1)
                .set("updatedAt", now);
        EntitlementGrant updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), EntitlementGrant.class
        );
        return Optional.ofNullable(updated);
    }

    public Optional<EntitlementGrant> consumeHeldOne(
            String grantId,
            long expectedVersion,
            Instant now
    ) {
        return moveHeldOne(grantId, expectedVersion, "consumedUnits", now);
    }

    public Optional<EntitlementGrant> releaseHeldOne(
            String grantId,
            long expectedVersion,
            Instant now
    ) {
        return moveHeldOne(grantId, expectedVersion, "availableUnits", now);
    }

    private Optional<EntitlementGrant> moveHeldOne(
            String grantId,
            long expectedVersion,
            String destination,
            Instant now
    ) {
        Query query = Query.query(Criteria.where("grantId").is(grantId)
                .and("state").is("ACTIVE")
                .and("heldUnits").gte(1)
                .and("version").is(expectedVersion));
        Update update = new Update()
                .inc("heldUnits", -1)
                .inc(destination, 1)
                .inc("version", 1)
                .set("updatedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                EntitlementGrant.class
        ));
    }
}
