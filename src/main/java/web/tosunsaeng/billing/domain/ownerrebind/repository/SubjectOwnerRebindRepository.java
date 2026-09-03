package web.tosunsaeng.billing.domain.ownerrebind.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.SubjectOwnerRebind;

@Repository
public class SubjectOwnerRebindRepository {

    private final MongoTemplate mongoTemplate;

    public SubjectOwnerRebindRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public SubjectOwnerRebind insert(SubjectOwnerRebind rebind) {
        return mongoTemplate.insert(rebind);
    }

    public Optional<SubjectOwnerRebind> findActiveFence(
            String subjectRefId,
            String sourceUserId,
            String attemptGroupId,
            String sessionId,
            Instant now
    ) {
        Query query = Query.query(Criteria.where("subjectRefId").is(subjectRefId)
                .and("sourceUserId").is(sourceUserId)
                .and("attemptGroupId").is(attemptGroupId)
                .and("sessionId").is(sessionId)
                .and("cleanupState").in(
                        SubjectOwnerRebind.CleanupState.WAITING_TERMINAL,
                        SubjectOwnerRebind.CleanupState.DUE
                )
                .and("legacyFenceExpiresAt").gt(now));
        return Optional.ofNullable(mongoTemplate.findOne(query, SubjectOwnerRebind.class));
    }

    public void markTerminalDue(
            String subjectRefId,
            String attemptGroupId,
            String sessionId,
            Instant terminalAt
    ) {
        Query query = Query.query(Criteria.where("subjectRefId").is(subjectRefId)
                .and("attemptGroupId").is(attemptGroupId)
                .and("sessionId").is(sessionId)
                .and("sourceUserId").exists(true)
                .and("cleanupState").is(SubjectOwnerRebind.CleanupState.WAITING_TERMINAL));
        Update update = new Update()
                .set("cleanupState", SubjectOwnerRebind.CleanupState.DUE)
                .set("cleanupDueAt", terminalAt)
                .set("legacyFenceExpiresAt", terminalAt)
                .set("purgeAt", terminalAt.plusSeconds(24 * 60 * 60L));
        mongoTemplate.updateMulti(query, update, SubjectOwnerRebind.class);
    }

    public List<SubjectOwnerRebind> findDue(Instant now, int limit) {
        Query query = Query.query(Criteria.where("sourceUserId").exists(true)
                        .and("cleanupDueAt").lte(now)
                        .and("cleanupState").in(
                                SubjectOwnerRebind.CleanupState.WAITING_TERMINAL,
                                SubjectOwnerRebind.CleanupState.DUE
                        ))
                .with(Sort.by(Sort.Direction.ASC, "cleanupDueAt"))
                .limit(limit);
        return mongoTemplate.find(query, SubjectOwnerRebind.class);
    }

    public Optional<SubjectOwnerRebind> unlinkSource(String id, Instant unlinkedAt) {
        Query query = Query.query(Criteria.where("_id").is(id)
                .and("sourceUserId").exists(true)
                .and("cleanupDueAt").lte(unlinkedAt));
        Update update = new Update()
                .unset("sourceUserId")
                .unset("attemptGroupId")
                .unset("sessionId")
                .set("sourceUnlinkedAt", unlinkedAt)
                .set("cleanupState", SubjectOwnerRebind.CleanupState.CLEANED)
                .set("purgeAt", unlinkedAt.plusSeconds(24 * 60 * 60L));
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                SubjectOwnerRebind.class
        ));
    }
}
