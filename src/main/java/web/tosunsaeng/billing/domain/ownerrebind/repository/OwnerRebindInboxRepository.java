package web.tosunsaeng.billing.domain.ownerrebind.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.OwnerRebindInbox;

@Repository
public class OwnerRebindInboxRepository {

    private final MongoTemplate mongoTemplate;

    public OwnerRebindInboxRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<OwnerRebindInbox> findByEventId(String eventId) {
        return Optional.ofNullable(mongoTemplate.findById(eventId, OwnerRebindInbox.class));
    }

    public OwnerRebindInbox insert(OwnerRebindInbox inbox) {
        return mongoTemplate.insert(inbox);
    }
}
