package web.tosunsaeng.billing.reservation.infrastructure;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.ReservationAllocation;

@Repository
public class ReservationAllocationRepository {

    private final MongoTemplate mongoTemplate;

    public ReservationAllocationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ReservationAllocation insert(ReservationAllocation allocation) {
        return mongoTemplate.insert(allocation);
    }

    public Optional<ReservationAllocation> findByReservationId(String reservationId) {
        Query query = Query.query(Criteria.where("reservationId").is(reservationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, ReservationAllocation.class));
    }

    public Optional<ReservationAllocation> transitionHeld(
            String allocationId,
            long expectedVersion,
            ReservationAllocation.Status targetStatus,
            Instant terminalAt
    ) {
        if (targetStatus == ReservationAllocation.Status.HELD) {
            throw new IllegalArgumentException("A terminal allocation status is required.");
        }
        Query query = Query.query(Criteria.where("allocationId").is(allocationId)
                .and("status").is(ReservationAllocation.Status.HELD)
                .and("version").is(expectedVersion));
        Update update = new Update()
                .set("status", targetStatus)
                .set("terminalAt", terminalAt)
                .inc("version", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                ReservationAllocation.class
        ));
    }
}
