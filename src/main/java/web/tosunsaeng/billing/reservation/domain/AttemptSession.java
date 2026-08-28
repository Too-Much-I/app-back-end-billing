package web.tosunsaeng.billing.reservation.domain;

import java.time.Instant;

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
}
