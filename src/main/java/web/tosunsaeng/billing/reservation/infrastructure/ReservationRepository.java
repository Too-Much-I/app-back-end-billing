package web.tosunsaeng.billing.reservation.infrastructure;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.Reservation;

@Repository
public class ReservationRepository {

    private final MongoTemplate mongoTemplate;

    public ReservationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Reservation> findBySubjectAndOperation(
            String subjectRefId,
            String operationId
    ) {
        Query query = Query.query(Criteria.where("subjectRefId").is(subjectRefId)
                .and("operationId").is(operationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Reservation.class));
    }

    public Reservation insert(Reservation reservation) {
        return mongoTemplate.insert(reservation);
    }
}
