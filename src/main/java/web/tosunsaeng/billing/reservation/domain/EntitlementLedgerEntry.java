package web.tosunsaeng.billing.reservation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "entitlement_ledger")
public class EntitlementLedgerEntry {

    public enum EventType {
        GRANTED,
        RESERVED,
        RELEASED,
        CONSUMED,
        SUBJECT_UNLINKED
    }

    @Id
    private String id;
    private String ledgerEventId;
    private String aggregateType;
    private String aggregateId;
    private long sequence;
    private EventType eventType;
    private int units;
    private String subjectRefId;
    private String trialClaimId;
    private String reservationId;
    private String allocationId;
    private String dedupeKey;
    private Instant occurredAt;
    private int metadataVersion;

    protected EntitlementLedgerEntry() {
    }

    private EntitlementLedgerEntry(
            String ledgerEventId,
            String aggregateId,
            long sequence,
            EventType eventType,
            String subjectRefId,
            String trialClaimId,
            String reservationId,
            String allocationId,
            String dedupeKey,
            Instant occurredAt
    ) {
        this.id = ledgerEventId;
        this.ledgerEventId = ledgerEventId;
        this.aggregateType = "GRANT";
        this.aggregateId = aggregateId;
        this.sequence = sequence;
        this.eventType = eventType;
        this.units = 1;
        this.subjectRefId = subjectRefId;
        this.trialClaimId = trialClaimId;
        this.reservationId = reservationId;
        this.allocationId = allocationId;
        this.dedupeKey = dedupeKey;
        this.occurredAt = occurredAt;
        this.metadataVersion = 1;
    }

    public static EntitlementLedgerEntry granted(
            String ledgerEventId,
            String grantId,
            String subjectRefId,
            String trialClaimId,
            Instant occurredAt
    ) {
        return new EntitlementLedgerEntry(
                ledgerEventId, grantId, 1, EventType.GRANTED, subjectRefId,
                trialClaimId, null, null, "GRANTED:" + trialClaimId, occurredAt
        );
    }

    public static EntitlementLedgerEntry reserved(
            String ledgerEventId,
            String grantId,
            long sequence,
            String subjectRefId,
            String trialClaimId,
            String reservationId,
            String allocationId,
            Instant occurredAt
    ) {
        return new EntitlementLedgerEntry(
                ledgerEventId, grantId, sequence, EventType.RESERVED, subjectRefId,
                trialClaimId, reservationId, allocationId,
                "RESERVED:" + reservationId, occurredAt
        );
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getSequence() {
        return sequence;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    @Override
    public String toString() {
        return "EntitlementLedgerEntry[eventType=" + eventType
                + ", sensitiveFields=[REDACTED]]";
    }
}
