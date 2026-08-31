package web.tosunsaeng.billing.domain.attempt.repository;

import java.util.Optional;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroupEventInbox;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventDisposition;

@Repository
public class AttemptGroupEventInboxRepository {

    private static final String COLLECTION = "inbound_event_inbox";

    private final MongoTemplate mongoTemplate;

    public AttemptGroupEventInboxRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<AttemptGroupEventInbox> findByEventId(String eventId) {
        Document document = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("eventId", eventId))
                .limit(1)
                .first();
        return Optional.ofNullable(document).map(AttemptGroupEventInboxRepository::restore);
    }

    public AttemptGroupEventInbox insert(AttemptGroupEventInbox inbox) {
        mongoTemplate.getCollection(COLLECTION).insertOne(new Document("_id", inbox.getEventId())
                .append("eventId", inbox.getEventId())
                .append("producer", inbox.getProducer())
                .append("eventType", inbox.getEventType())
                .append("schemaVersion", inbox.getSchemaVersion())
                .append("payloadDigest", inbox.getPayloadDigest())
                .append("disposition", inbox.getDisposition().name())
                .append("receivedAt", java.util.Date.from(inbox.getReceivedAt()))
                .append("purgeAt", java.util.Date.from(inbox.getPurgeAt())));
        return inbox;
    }

    private static AttemptGroupEventInbox restore(Document document) {
        Number schemaVersion = document.get("schemaVersion", Number.class);
        java.util.Date receivedAt = document.getDate("receivedAt");
        java.util.Date purgeAt = document.getDate("purgeAt");
        String disposition = document.getString("disposition");
        return AttemptGroupEventInbox.restored(
                document.getString("eventId"),
                document.getString("producer"),
                document.getString("eventType"),
                schemaVersion == null ? 0 : schemaVersion.intValue(),
                document.getString("payloadDigest"),
                disposition == null ? null : AttemptGroupEventDisposition.valueOf(disposition),
                receivedAt == null ? null : receivedAt.toInstant(),
                purgeAt == null ? null : purgeAt.toInstant()
        );
    }
}
