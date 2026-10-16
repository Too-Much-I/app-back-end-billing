package web.tosunsaeng.billing.domain.attempt.domain.entity;

import java.time.Instant;
import web.tosunsaeng.billing.domain.attempt.domain.model.SessionCreationOwner;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "attempt_sessions")
public class AttemptSession {

    public enum State {
        PROPOSED,
        ACTIVE,
        ABANDONED_RESTARTED,
        COMPLETED,
        FAILED
    }

    // Absent on legacy records; never infer TRIAL from absence.
    private web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot sourceSnapshot;

    public web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot getSourceSnapshot() { return sourceSnapshot; }
    public AttemptSession withSourceSnapshot(web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot source) {
        java.util.Objects.requireNonNull(source);
        if (sourceSnapshot != null && !sourceSnapshot.equals(source)) throw new IllegalStateException("Source snapshot is immutable");
        sourceSnapshot = source;
        return this;
    }

    // Optional on legacy rows. Absence is unknown, never the current owner inferred retroactively.
    private SessionCreationOwner creationOwner;

    public SessionCreationOwner getCreationOwner() { return creationOwner; }

    public AttemptSession withCreationOwner(SessionCreationOwner owner) {
        java.util.Objects.requireNonNull(owner);
        if (creationOwner != null && !creationOwner.equals(owner))
            throw new IllegalStateException("Session creation owner is immutable");
        creationOwner = owner;
        return this;
    }

    private boolean runtimeManaged;
    private boolean deleted;
    public boolean isDeleted() { return deleted; }
    private boolean completionAllowed;
    public AttemptSession withPaidRuntime() { runtimeManaged = true; activeGuard = null; return this; }
    public boolean isCompletionAllowed() { return completionAllowed; }

    @Id
    private String id;
    private String sessionId;
    private String attemptGroupId;
    private String subjectRefId;
    private String operationId;
    private State state;
    private Boolean activeGuard;
    private Instant proposedAt;
    private Instant confirmedAt;
    private Instant terminalAt;
    private long version;
    private Long sessionOwnerEpoch;

    public Long getSessionOwnerEpoch() { return sessionOwnerEpoch; }

    public AttemptSession withSessionOwnerEpoch(long epoch) {
        if (epoch < 1 || sessionOwnerEpoch != null) { throw new IllegalStateException("Invalid epoch initialization"); }
        sessionOwnerEpoch = epoch;
        return this;
    }

    protected AttemptSession() {
    }

    private AttemptSession(
            String sessionId,
            String attemptGroupId,
            String subjectRefId,
            String operationId,
            Instant proposedAt
    ) {
        this.id = sessionId;
        this.sessionId = sessionId;
        this.attemptGroupId = attemptGroupId;
        this.subjectRefId = subjectRefId;
        this.operationId = operationId;
        this.state = State.PROPOSED;
        this.activeGuard = true;
        this.proposedAt = proposedAt;
        this.version = 1;
    }

    public static AttemptSession proposed(
            String sessionId,
            String attemptGroupId,
            String subjectRefId,
            String operationId,
            Instant proposedAt
    ) {
        return new AttemptSession(
                sessionId, attemptGroupId, subjectRefId, operationId, proposedAt
        );
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAttemptGroupId() {
        return attemptGroupId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public State getState() {
        return state;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public long getVersion() {
        return version;
    }

    public Boolean getActiveGuard() {
        return activeGuard;
    }

    public Instant getTerminalAt() {
        return terminalAt;
    }

    public Instant getProposedAt() {
        return proposedAt;
    }
}
