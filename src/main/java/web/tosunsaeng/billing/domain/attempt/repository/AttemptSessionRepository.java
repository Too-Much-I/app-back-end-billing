package web.tosunsaeng.billing.domain.attempt.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;

@Repository
public class AttemptSessionRepository {

    private final MongoTemplate mongoTemplate;

    public AttemptSessionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<AttemptSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(mongoTemplate.findById(sessionId, AttemptSession.class));
    }

    public AttemptSession insert(AttemptSession session) {
        return mongoTemplate.insert(session);
    }

    public void abandonActiveBySubject(String subjectRefId, java.time.Instant now) {
        Query query = Query.query(Criteria.where("subjectRefId").is(subjectRefId)
                .and("activeGuard").is(true));
        Update update = new Update()
                .set("state", AttemptSession.State.ABANDONED_RESTARTED)
                .unset("activeGuard")
                .set("terminalAt", now)
                .inc("version", 1);
        mongoTemplate.updateFirst(query, update, AttemptSession.class);
    }

    public Optional<AttemptSession> activateProposed(
            String sessionId,
            String attemptGroupId,
            String subjectRefId,
            String operationId,
            long expectedVersion,
            Instant confirmedAt
    ) {
        Query query = proposedQuery(
                sessionId, attemptGroupId, subjectRefId, operationId, expectedVersion
        );
        Update update = new Update()
                .set("state", AttemptSession.State.ACTIVE)
                .set("confirmedAt", confirmedAt)
                .inc("version", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), AttemptSession.class
        ));
    }

    public Optional<AttemptSession> failProposed(
            String sessionId,
            String attemptGroupId,
            String subjectRefId,
            String operationId,
            long expectedVersion,
            Instant terminalAt
    ) {
        Query query = proposedQuery(
                sessionId, attemptGroupId, subjectRefId, operationId, expectedVersion
        );
        Update update = new Update()
                .set("state", AttemptSession.State.FAILED)
                .unset("activeGuard")
                .set("terminalAt", terminalAt)
                .inc("version", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), AttemptSession.class
        ));
    }

    private static Query proposedQuery(
            String sessionId,
            String attemptGroupId,
            String subjectRefId,
            String operationId,
            long expectedVersion
    ) {
        return Query.query(Criteria.where("sessionId").is(sessionId)
                .and("attemptGroupId").is(attemptGroupId)
                .and("subjectRefId").is(subjectRefId)
                .and("operationId").is(operationId)
                .and("state").is(AttemptSession.State.PROPOSED)
                .and("activeGuard").is(true)
                .and("version").is(expectedVersion));
    }
}
