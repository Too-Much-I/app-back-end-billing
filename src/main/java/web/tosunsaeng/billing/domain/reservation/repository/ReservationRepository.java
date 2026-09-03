package web.tosunsaeng.billing.domain.reservation.repository;

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

import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;

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

    public Optional<Reservation> findById(String reservationId) {
        return Optional.ofNullable(mongoTemplate.findById(reservationId, Reservation.class));
    }

    public List<Reservation> findActiveBySubjects(List<String> subjectRefIds) {
        if (subjectRefIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("subjectRefId").in(subjectRefIds)
                .and("status").is(Reservation.Status.RESERVED)
                .and("activeGuard").is(true));
        return mongoTemplate.find(query, Reservation.class);
    }

    public Optional<Reservation> transitionReserved(
            String reservationId,
            long expectedVersion,
            Reservation.Status targetStatus,
            Instant terminalAt
    ) {
        if (targetStatus == Reservation.Status.RESERVED) {
            throw new IllegalArgumentException("A terminal reservation status is required.");
        }
        Query query = Query.query(Criteria.where("reservationId").is(reservationId)
                .and("status").is(Reservation.Status.RESERVED)
                .and("activeGuard").is(true)
                .and("version").is(expectedVersion));
        Update update = new Update()
                .set("status", targetStatus)
                .unset("activeGuard")
                .set("terminalAt", terminalAt)
                .inc("version", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Reservation.class
        ));
    }

    public List<Reservation> findDue(Instant now, int limit) {
        Query query = Query.query(Criteria.where("status").is(Reservation.Status.RESERVED)
                        .and("expiresAt").lte(now))
                .with(Sort.by(Sort.Direction.ASC, "expiresAt"))
                .limit(limit);
        return mongoTemplate.find(query, Reservation.class);
    }
}
