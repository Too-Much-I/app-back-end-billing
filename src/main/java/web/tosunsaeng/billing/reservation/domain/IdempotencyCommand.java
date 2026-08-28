package web.tosunsaeng.billing.reservation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "idempotency_commands")
public class IdempotencyCommand {

    public enum State {
        PROCESSING,
        SUCCEEDED,
        FAILED_TERMINAL
    }

    public record ResponseSnapshot(
            String operationId,
            String reservationId,
            Reservation.Kind reservationKind,
            Reservation.Status reservationStatus,
            String attemptGroupId,
            String sessionId,
            String mockExamId,
            Instant expiresAt
    ) {
        @Override
        public String toString() {
            return "ResponseSnapshot[kind=" + reservationKind
                    + ", status=" + reservationStatus
                    + ", sensitiveFields=[REDACTED]]";
        }
    }

    @Id
    private String id;
    private String commandId;
    private String callerService;
    private String userId;
    private String operationId;
    private String commandType;
    private String payloadHash;
    private State state;
    private String reservationId;
    private ResponseSnapshot responseSnapshot;
    private Instant createdAt;
    private Instant terminalAt;
    private Instant purgeAt;
    private boolean active;

    protected IdempotencyCommand() {
    }

    private IdempotencyCommand(
            String commandId,
            String userId,
            String operationId,
            String payloadHash,
            Instant createdAt
    ) {
        this.id = commandId;
        this.commandId = commandId;
        this.callerService = "LEARNING_CORE";
        this.userId = userId;
        this.operationId = operationId;
        this.commandType = "RESERVE";
        this.payloadHash = payloadHash;
        this.state = State.PROCESSING;
        this.createdAt = createdAt;
        this.active = true;
    }

    public static IdempotencyCommand processing(
            String commandId,
            String userId,
            String operationId,
            String payloadHash,
            Instant createdAt
    ) {
        return new IdempotencyCommand(commandId, userId, operationId, payloadHash, createdAt);
    }

    public void succeed(String reservationId, ResponseSnapshot responseSnapshot) {
        if (state != State.PROCESSING) {
            throw new IllegalStateException("Only processing commands can succeed.");
        }
        this.reservationId = reservationId;
        this.responseSnapshot = responseSnapshot;
        this.state = State.SUCCEEDED;
    }

    public String getUserId() {
        return userId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public State getState() {
        return state;
    }

    public ResponseSnapshot getResponseSnapshot() {
        return responseSnapshot;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "IdempotencyCommand[state=" + state + ", sensitiveFields=[REDACTED]]";
    }
}
