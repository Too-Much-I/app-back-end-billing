package web.tosunsaeng.billing.domain.attempt.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "attempt_groups")
public class AttemptGroup {

    public enum Status {
        OPEN,
        GRADING,
        RETAKE_AVAILABLE,
        COMPLETED
    }

    @Id
    private String id;
    private String attemptGroupId;
    private String subjectRefId;
    private String trialClaimId;
    private String consumptionLedgerEventId;
    private String mockExamId;
    private Status status;
    private String activeSessionId;
    private Boolean openGuard;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private long version;

    protected AttemptGroup() {
    }

    private AttemptGroup(
            String attemptGroupId,
            String subjectRefId,
            String trialClaimId,
            String consumptionLedgerEventId,
            String mockExamId,
            Status status,
            Instant now
    ) {
        this.id = attemptGroupId;
        this.attemptGroupId = attemptGroupId;
        this.subjectRefId = subjectRefId;
        this.trialClaimId = trialClaimId;
        this.consumptionLedgerEventId = consumptionLedgerEventId;
        this.mockExamId = mockExamId;
        this.status = status;
        this.openGuard = status == Status.COMPLETED ? null : true;
        this.createdAt = now;
        this.updatedAt = now;
        this.version = 1;
    }

    public static AttemptGroup projection(
            String attemptGroupId,
            String subjectRefId,
            String trialClaimId,
            String consumptionLedgerEventId,
            String mockExamId,
            Status status,
            Instant now
    ) {
        return new AttemptGroup(
                attemptGroupId, subjectRefId, trialClaimId, consumptionLedgerEventId,
                mockExamId, status, now
        );
    }

    public static AttemptGroup open(
            String attemptGroupId,
            String subjectRefId,
            String trialClaimId,
            String consumptionLedgerEventId,
            String mockExamId,
            String activeSessionId,
            Instant now
    ) {
        AttemptGroup group = new AttemptGroup(
                attemptGroupId, subjectRefId, trialClaimId, consumptionLedgerEventId,
                mockExamId, Status.OPEN, now
        );
        group.activeSessionId = activeSessionId;
        return group;
    }

    public String getAttemptGroupId() {
        return attemptGroupId;
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public String getTrialClaimId() {
        return trialClaimId;
    }

    public String getMockExamId() {
        return mockExamId;
    }

    public Status getStatus() {
        return status;
    }

    public String getConsumptionLedgerEventId() {
        return consumptionLedgerEventId;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public long getVersion() {
        return version;
    }

    public Boolean getOpenGuard() {
        return openGuard;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
