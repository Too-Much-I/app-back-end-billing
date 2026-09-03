package web.tosunsaeng.billing.domain.reservation.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;

@Repository
public class IdempotencyCommandRepository {

    private final MongoTemplate mongoTemplate;

    public IdempotencyCommandRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<IdempotencyCommand> findReserve(
            String userId,
            String operationId
    ) {
        Query query = Query.query(Criteria.where("callerService").is("LEARNING_CORE")
                .and("userId").is(userId)
                .and("operationId").is(operationId)
                .and("commandType").is("RESERVE"));
        return Optional.ofNullable(mongoTemplate.findOne(query, IdempotencyCommand.class));
    }

    public Optional<IdempotencyCommand> find(
            String userId,
            String operationId,
            String commandType
    ) {
        Query query = Query.query(Criteria.where("callerService").is("LEARNING_CORE")
                .and("userId").is(userId)
                .and("operationId").is(operationId)
                .and("commandType").is(commandType));
        return Optional.ofNullable(mongoTemplate.findOne(query, IdempotencyCommand.class));
    }

    public Optional<IdempotencyCommand> findReserveByReservationId(String reservationId) {
        Query query = Query.query(Criteria.where("callerService").is("LEARNING_CORE")
                .and("commandType").is("RESERVE")
                .and("reservationId").is(reservationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, IdempotencyCommand.class));
    }

    public Optional<IdempotencyCommand> findActiveReserve(String userId) {
        Query query = Query.query(Criteria.where("callerService").is("LEARNING_CORE")
                .and("userId").is(userId)
                .and("commandType").is("RESERVE")
                .and("active").is(true));
        return Optional.ofNullable(mongoTemplate.findOne(query, IdempotencyCommand.class));
    }

    public boolean existsProcessingReserve(String userId) {
        Query query = Query.query(Criteria.where("callerService").is("LEARNING_CORE")
                .and("userId").is(userId)
                .and("commandType").is("RESERVE")
                .and("state").is(IdempotencyCommand.State.PROCESSING)
                .and("active").is(true));
        return mongoTemplate.exists(query, IdempotencyCommand.class);
    }

    public IdempotencyCommand insert(IdempotencyCommand command) {
        return mongoTemplate.insert(command);
    }

    public IdempotencyCommand save(IdempotencyCommand command) {
        return mongoTemplate.save(command);
    }
}
