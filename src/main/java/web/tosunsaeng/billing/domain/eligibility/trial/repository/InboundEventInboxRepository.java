package web.tosunsaeng.billing.domain.eligibility.trial.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.InboundEventInbox;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;

@Repository
public class InboundEventInboxRepository {

    private final MongoTemplate mongoTemplate;

    public InboundEventInboxRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<InboundEventInbox> findByEventId(String eventId) {
        return Optional.ofNullable(mongoTemplate.findById(eventId, InboundEventInbox.class));
    }

    public Optional<InboundEventInbox> findByIdentityRevision(TrialEligibilityEvent event) {
        Query query = Query.query(Criteria.where("producer").is(event.producer())
                .and("consumerScopeId").is(event.consumerScopeId())
                .and("userId").is(event.userId())
                .and("bindingRevision").is(event.bindingRevision()));
        return Optional.ofNullable(mongoTemplate.findOne(query, InboundEventInbox.class));
    }

    public InboundEventInbox insert(InboundEventInbox inbox) {
        return mongoTemplate.insert(inbox);
    }
}
