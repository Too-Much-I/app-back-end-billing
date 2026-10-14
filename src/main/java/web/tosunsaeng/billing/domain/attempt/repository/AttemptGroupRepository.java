package web.tosunsaeng.billing.domain.attempt.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;

@Repository
public class AttemptGroupRepository {

    private final MongoOperations mongoTemplate;

    public AttemptGroupRepository(MongoOperations mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<AttemptGroup> findNonTerminalBySubject(String subjectRefId) {
        Query query = Query.query(Criteria.where("subjectRefId").is(subjectRefId)
                .and("openGuard").is(true));
        return Optional.ofNullable(mongoTemplate.findOne(query, AttemptGroup.class));
    }

    public List<AttemptGroup> findByClaimIds(List<String> trialClaimIds) {
        if (trialClaimIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("trialClaimId").in(trialClaimIds));
        return mongoTemplate.find(query, AttemptGroup.class);
    }

    public AttemptGroup insert(AttemptGroup group) {
        return mongoTemplate.insert(group);
    }

    public Optional<AttemptGroup> findById(String attemptGroupId) {
        return Optional.ofNullable(mongoTemplate.findById(attemptGroupId, AttemptGroup.class));
    }

    public Optional<AttemptGroup> openWithSession(
            String attemptGroupId,
            long expectedVersion,
            String sessionId,
            Instant now
    ) {
        Query query = Query.query(Criteria.where("attemptGroupId").is(attemptGroupId)
                .and("version").is(expectedVersion)
                .and("status").in(AttemptGroup.Status.OPEN, AttemptGroup.Status.RETAKE_AVAILABLE)
                .and("openGuard").is(true).and("replacementBlocked").ne(true));
        Update update = new Update()
                .set("status", AttemptGroup.Status.OPEN)
                .set("activeSessionId", sessionId)
                .set("updatedAt", now)
                .inc("version", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), AttemptGroup.class
        ));
    }

    public Optional<AttemptGroup> markGrading(
            String attemptGroupId,
            String sessionId,
            long expectedVersion,
            Instant now
    ) {
        Query query = activeTransitionQuery(attemptGroupId, sessionId, expectedVersion)
                .addCriteria(Criteria.where("status").is(AttemptGroup.Status.OPEN));
        Update update = new Update()
                .set("status", AttemptGroup.Status.GRADING)
                .set("updatedAt", now)
                .inc("version", 1);
        return findAndModify(query, update);
    }

    public Optional<AttemptGroup> markCompleted(
            String attemptGroupId,
            String sessionId,
            long expectedVersion,
            Instant occurredAt,
            Instant now
    ) {
        Query query = activeTransitionQuery(attemptGroupId, sessionId, expectedVersion)
                .addCriteria(Criteria.where("status").in(
                        AttemptGroup.Status.OPEN, AttemptGroup.Status.GRADING
                ));
        Update update = new Update()
                .set("status", AttemptGroup.Status.COMPLETED)
                .set("completedAt", occurredAt)
                .set("updatedAt", now)
                .unset("openGuard")
                .inc("version", 1);
        return findAndModify(query, update);
    }

    public Optional<AttemptGroup> markRetakeAvailable(
            String attemptGroupId,
            String sessionId,
            long expectedVersion,
            Instant now
    ) {
        Query query = activeTransitionQuery(attemptGroupId, sessionId, expectedVersion)
                .addCriteria(Criteria.where("status").in(
                        AttemptGroup.Status.OPEN, AttemptGroup.Status.GRADING
                ));
        Update update = new Update()
                .set("status", AttemptGroup.Status.RETAKE_AVAILABLE)
                .set("updatedAt", now)
                .set("openGuard", true)
                .unset("activeSessionId")
                .inc("version", 1);
        // A late failure may advance learning status but must never reopen the old group.
        var blocked = findAndModify(Query.of(query).addCriteria(Criteria.where("replacementBlocked").is(true)),
                new Update().set("status", AttemptGroup.Status.RETAKE_AVAILABLE).set("updatedAt", now)
                        .unset("openGuard").unset("activeSessionId").inc("version", 1));
        return blocked.isPresent() ? blocked : findAndModify(query.addCriteria(Criteria.where("replacementBlocked").ne(true)), update);
    }

    private Optional<AttemptGroup> findAndModify(Query query, Update update) {
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), AttemptGroup.class
        ));
    }

    private static Query activeTransitionQuery(
            String attemptGroupId,
            String sessionId,
            long expectedVersion
    ) {
        return Query.query(Criteria.where("attemptGroupId").is(attemptGroupId)
                .and("activeSessionId").is(sessionId)
                .orOperator(Criteria.where("openGuard").is(true), Criteria.where("replacementBlocked").is(true))
                .and("version").is(expectedVersion));
    }
}
