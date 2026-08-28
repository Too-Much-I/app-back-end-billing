package web.tosunsaeng.billing.reservation.infrastructure;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.AttemptGroup;

@Repository
public class AttemptGroupRepository {

    private final MongoTemplate mongoTemplate;

    public AttemptGroupRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<AttemptGroup> findNonTerminalBySubject(String subjectRefId) {
        Query query = Query.query(Criteria.where("subjectRefId").is(subjectRefId)
                .and("openGuard").is(true));
        return Optional.ofNullable(mongoTemplate.findOne(query, AttemptGroup.class));
    }

    public AttemptGroup insert(AttemptGroup group) {
        return mongoTemplate.insert(group);
    }
}
