package web.tosunsaeng.billing.domain.ownerrebind.domain.entity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;

@Document(collection = "subject_owner_rebinds")
public class SubjectOwnerRebind {

    public enum CleanupState {
        WAITING_TERMINAL,
        DUE,
        CLEANED
    }

    @Id
    private String id;
    private String eventId;
    private OwnerRebindEventKind eventKind;
    private String subjectRefId;
    private String trialClaimId;
    private String sourceUserId;
    private String attemptGroupId;
    private String sessionId;
    private long ownerVersionFrom;
    private long ownerVersionTo;
    private Instant appliedAt;
    private Instant legacyFenceExpiresAt;
    private Instant cleanupDueAt;
    private CleanupState cleanupState;
    private Instant sourceUnlinkedAt;
    private Instant purgeAt;

    protected SubjectOwnerRebind() {
    }

    private SubjectOwnerRebind(
            String eventId,
            OwnerRebindEventKind eventKind,
            String subjectRefId,
            String trialClaimId,
            String sourceUserId,
            String attemptGroupId,
            String sessionId,
            long ownerVersionFrom,
            Instant appliedAt,
            Instant legacyFenceExpiresAt
    ) {
        this.id = UUID.nameUUIDFromBytes(
                (eventId + ":" + subjectRefId).getBytes(StandardCharsets.UTF_8)
        ).toString();
        this.eventId = eventId;
        this.eventKind = eventKind;
        this.subjectRefId = subjectRefId;
        this.trialClaimId = trialClaimId;
        this.sourceUserId = sourceUserId;
        this.attemptGroupId = attemptGroupId;
        this.sessionId = sessionId;
        this.ownerVersionFrom = ownerVersionFrom;
        this.ownerVersionTo = ownerVersionFrom + 1;
        this.appliedAt = appliedAt;
        this.legacyFenceExpiresAt = legacyFenceExpiresAt;
        this.cleanupDueAt = legacyFenceExpiresAt;
        this.cleanupState = CleanupState.WAITING_TERMINAL;
        this.purgeAt = legacyFenceExpiresAt.plusSeconds(24 * 60 * 60L);
    }

    public static SubjectOwnerRebind waitingTerminal(
            String eventId,
            OwnerRebindEventKind eventKind,
            String subjectRefId,
            String trialClaimId,
            String sourceUserId,
            String attemptGroupId,
            String sessionId,
            long ownerVersionFrom,
            Instant appliedAt,
            Instant legacyFenceExpiresAt
    ) {
        return new SubjectOwnerRebind(
                eventId, eventKind, subjectRefId, trialClaimId, sourceUserId,
                attemptGroupId, sessionId, ownerVersionFrom, appliedAt,
                legacyFenceExpiresAt
        );
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public String getSourceUserId() {
        return sourceUserId;
    }

    public String getAttemptGroupId() {
        return attemptGroupId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getLegacyFenceExpiresAt() {
        return legacyFenceExpiresAt;
    }

    public Instant getCleanupDueAt() {
        return cleanupDueAt;
    }

    public CleanupState getCleanupState() {
        return cleanupState;
    }

    @Override
    public String toString() {
        return "SubjectOwnerRebind[cleanupState=" + cleanupState
                + ", sensitiveFields=[REDACTED]]";
    }
}
