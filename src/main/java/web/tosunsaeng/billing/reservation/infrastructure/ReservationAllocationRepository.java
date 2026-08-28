package web.tosunsaeng.billing.reservation.infrastructure;

import org.springframework.data.mongodb.core.MongoTemplate;
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
}
