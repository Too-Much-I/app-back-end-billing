package web.tosunsaeng.billing.domain.attempt.domain.entity;

import java.time.Duration;
import java.time.Instant;

import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventDisposition;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;

public final class AttemptGroupEventInbox {

    private static final Duration RETENTION = Duration.ofDays(120);

    private final String eventId;
    private final String producer;
    private final String eventType;
    private final int schemaVersion;
    private final String payloadDigest;
    private final AttemptGroupEventDisposition disposition;
    private final Instant receivedAt;
    private final Instant purgeAt;

    private AttemptGroupEventInbox(
            String eventId,
            String producer,
            String eventType,
            int schemaVersion,
            String payloadDigest,
            AttemptGroupEventDisposition disposition,
            Instant receivedAt,
            Instant purgeAt
    ) {
        this.eventId = eventId;
        this.producer = producer;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payloadDigest = payloadDigest;
        this.disposition = disposition;
        this.receivedAt = receivedAt;
        this.purgeAt = purgeAt;
    }

    public static AttemptGroupEventInbox from(
            AttemptGroupStatusEvent event,
            AttemptGroupEventDisposition disposition,
            Instant receivedAt
    ) {
        return new AttemptGroupEventInbox(
                event.eventId(),
                event.producer(),
                event.eventType(),
                event.schemaVersion(),
                event.payloadDigest(),
                disposition,
                receivedAt,
                receivedAt.plus(RETENTION)
        );
    }

    public static AttemptGroupEventInbox restored(
            String eventId,
            String producer,
            String eventType,
            int schemaVersion,
            String payloadDigest,
            AttemptGroupEventDisposition disposition,
            Instant receivedAt,
            Instant purgeAt
    ) {
        return new AttemptGroupEventInbox(
                eventId, producer, eventType, schemaVersion, payloadDigest,
                disposition, receivedAt, purgeAt
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

    public AttemptGroupEventDisposition getDisposition() {
        return disposition;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getPurgeAt() {
        return purgeAt;
    }
}
