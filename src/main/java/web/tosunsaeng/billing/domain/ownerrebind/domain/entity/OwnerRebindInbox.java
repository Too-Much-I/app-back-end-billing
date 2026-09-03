package web.tosunsaeng.billing.domain.ownerrebind.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindDisposition;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;

@Document(collection = "owner_rebind_inbox")
public class OwnerRebindInbox {

    @Id
    private String id;
    private String eventId;
    private String producer;
    private OwnerRebindEventKind eventKind;
    private int schemaVersion;
    private String payloadDigest;
    private OwnerRebindDisposition disposition;
    private String conflictCode;
    private int affectedSubjectCount;
    private Instant receivedAt;
    private Instant processedAt;
    private Instant purgeAt;

    protected OwnerRebindInbox() {
    }

    private OwnerRebindInbox(
            OwnerRebindCommand command,
            OwnerRebindDisposition disposition,
            String conflictCode,
            int affectedSubjectCount,
            Instant processedAt,
            Instant purgeAt
    ) {
        this.id = command.eventId();
        this.eventId = command.eventId();
        this.producer = "identity";
        this.eventKind = command.eventKind();
        this.schemaVersion = command.schemaVersion();
        this.payloadDigest = command.payloadDigest();
        this.disposition = disposition;
        this.conflictCode = conflictCode;
        this.affectedSubjectCount = affectedSubjectCount;
        this.receivedAt = processedAt;
        this.processedAt = processedAt;
        this.purgeAt = purgeAt;
    }

    public static OwnerRebindInbox processed(
            OwnerRebindCommand command,
            OwnerRebindDisposition disposition,
            int affectedSubjectCount,
            Instant processedAt,
            Instant purgeAt
    ) {
        return new OwnerRebindInbox(
                command, disposition, null, affectedSubjectCount, processedAt, purgeAt
        );
    }

    public static OwnerRebindInbox conflict(
            OwnerRebindCommand command,
            String conflictCode,
            Instant processedAt,
            Instant purgeAt
    ) {
        return new OwnerRebindInbox(
                command, OwnerRebindDisposition.CONFLICT, conflictCode, 0,
                processedAt, purgeAt
        );
    }

    public String getEventId() {
        return eventId;
    }

    public String getProducer() {
        return producer;
    }

    public OwnerRebindEventKind getEventKind() {
        return eventKind;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public OwnerRebindDisposition getDisposition() {
        return disposition;
    }

    public String getConflictCode() {
        return conflictCode;
    }

    public int getAffectedSubjectCount() {
        return affectedSubjectCount;
    }

    public Instant getPurgeAt() {
        return purgeAt;
    }

    @Override
    public String toString() {
        return "OwnerRebindInbox[eventId=" + eventId
                + ", disposition=" + disposition + "]";
    }
}
