package web.tosunsaeng.billing.reservation.infrastructure;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.AttemptSession;

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
}
