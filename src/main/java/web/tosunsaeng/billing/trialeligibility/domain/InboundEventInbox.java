package web.tosunsaeng.billing.trialeligibility.domain;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "inbound_event_inbox")
public class InboundEventInbox {

    private static final Duration RETENTION = Duration.ofDays(120);

    @Id
    private String id;
    private String eventId;
    private String producer;
    private String eventType;
    private int schemaVersion;
    private String payloadDigest;
    private String consumerScopeId;
    private String userId;
    private long bindingRevision;
    private InboundEventDisposition disposition;
    private Instant receivedAt;
    private Instant purgeAt;

    protected InboundEventInbox() {
    }

    private InboundEventInbox(
            String eventId,
            String producer,
            String eventType,
            int schemaVersion,
            String payloadDigest,
            String consumerScopeId,
            String userId,
            long bindingRevision,
            InboundEventDisposition disposition,
            Instant receivedAt,
            Instant purgeAt
    ) {
        this.id = eventId;
        this.eventId = eventId;
        this.producer = producer;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payloadDigest = payloadDigest;
        this.consumerScopeId = consumerScopeId;
        this.userId = userId;
        this.bindingRevision = bindingRevision;
        this.disposition = disposition;
        this.receivedAt = receivedAt;
        this.purgeAt = purgeAt;
    }

    public static InboundEventInbox from(
            TrialEligibilityEvent event,
            InboundEventDisposition disposition,
            Instant receivedAt
    ) {
        return new InboundEventInbox(
                event.eventId(), event.producer(), event.eventType().wireName(),
                event.schemaVersion(), event.payloadDigest(), event.consumerScopeId(),
                event.userId(), event.bindingRevision(), disposition, receivedAt,
                receivedAt.plus(RETENTION)
        );
    }

    public String getEventId() {
        return eventId;
    }

    public String getProducer() {
        return producer;
    }

    public String getEventType() {
        return eventType;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public String getConsumerScopeId() {
        return consumerScopeId;
    }

    public String getUserId() {
        return userId;
    }

    public long getBindingRevision() {
        return bindingRevision;
    }

    public InboundEventDisposition getDisposition() {
        return disposition;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getPurgeAt() {
        return purgeAt;
    }

    @Override
    public String toString() {
        return "InboundEventInbox[eventType=" + eventType
                + ", disposition=" + disposition
                + ", sensitiveFields=[REDACTED]]";
    }
}
