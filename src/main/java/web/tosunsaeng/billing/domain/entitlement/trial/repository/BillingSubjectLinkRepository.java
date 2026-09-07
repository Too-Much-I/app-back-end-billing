package web.tosunsaeng.billing.domain.entitlement.trial.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;

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

    public Optional<BillingSubjectLink> findBySubjectRefId(String subjectRefId) {
        return Optional.ofNullable(mongoTemplate.findById(subjectRefId, BillingSubjectLink.class));
    }

    public List<BillingSubjectLink> findActiveByOwner(
            String userId,
            Instant now,
            int limit
    ) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                        .and("active").is(true)
                        .and("retentionExpiresAt").gt(now))
                .limit(limit);
        return mongoTemplate.find(query, BillingSubjectLink.class);
    }

    public List<BillingSubjectLink> findActiveByOwnerAndScope(
            String userId,
            String consumerScopeId,
            Instant now,
            int limit
    ) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                        .and("consumerScopeId").is(consumerScopeId)
                        .and("active").is(true)
                        .and("retentionExpiresAt").gt(now))
                .limit(limit);
        return mongoTemplate.find(query, BillingSubjectLink.class);
    }

    public Optional<BillingSubjectLink> rebindOwner(
            BillingSubjectLink current,
            String targetUserId,
            Instant updatedAt,
            String transitionReason,
            String transitionId
    ) {
        Criteria version = current.hasExplicitOwnerVersion()
                ? Criteria.where("ownerVersion").is(current.getOwnerVersion())
                : new Criteria().orOperator(
                        Criteria.where("ownerVersion").exists(false),
                        Criteria.where("ownerVersion").is(1L)
                );
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(current.getSubjectRefId()),
                Criteria.where("userId").is(current.getUserId()),
                Criteria.where("active").is(true),
                Criteria.where("retentionExpiresAt").gt(updatedAt),
                Criteria.where("sessionOwnerEpoch").is(current.getSessionOwnerEpoch()),
                Criteria.where("sessionBindingVersion").is(current.getSessionBindingVersion()),
                version
        ));
        Update update = new Update()
                .set("userId", targetUserId)
                .set("ownerVersion", current.getOwnerVersion() + 1)
                .set("sessionOwnerEpoch", "PHONE_REJOIN".equals(transitionReason)
                        ? Math.incrementExact(current.sessionEpochForWrite()) : current.sessionEpochForWrite())
                .set("sessionBindingVersion", current.getSessionBindingVersion() == null
                        ? 0L : current.getSessionBindingVersion())
                .set("ownerUpdatedAt", updatedAt)
                .set("ownerTransitionReason", transitionReason)
                .set("ownerTransitionId", transitionId);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                BillingSubjectLink.class
        ));
    }

    public BillingSubjectLink insert(BillingSubjectLink link) {
        return mongoTemplate.insert(link);
    }

    /** Must run in the same transaction as reserve/confirm. Serializes with owner CAS. */
    public Optional<BillingSubjectLink> bindSession(BillingSubjectLink current, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(current.getSubjectRefId())
                .and("userId").is(current.getUserId()).and("active").is(true)
                .and("retentionExpiresAt").gt(now)
                .and("ownerVersion").is(current.hasExplicitOwnerVersion() ? current.getOwnerVersion() : null)
                .and("sessionOwnerEpoch").is(current.getSessionOwnerEpoch())
                .and("sessionBindingVersion").is(current.getSessionBindingVersion()));
        long version = current.getSessionBindingVersion() == null ? 0 : current.getSessionBindingVersion();
        if (version < 0) { throw new IllegalStateException("Invalid session binding version"); }
        return Optional.ofNullable(mongoTemplate.findAndModify(query, new Update()
                        .set("sessionOwnerEpoch", current.sessionEpochForWrite())
                        .set("sessionBindingVersion", Math.incrementExact(version)),
                FindAndModifyOptions.options().returnNew(true), BillingSubjectLink.class));
    }
}
