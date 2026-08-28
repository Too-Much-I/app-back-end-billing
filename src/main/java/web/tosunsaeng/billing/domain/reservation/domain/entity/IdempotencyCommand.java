package web.tosunsaeng.billing.domain.reservation.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;

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

    public record LifecycleResponseSnapshot(
            String operationId,
            String reservationId,
            Reservation.Status reservationStatus,
            String attemptGroupId,
            AttemptGroup.Status attemptGroupStatus,
            String sessionId,
            Instant terminalAt
    ) {
        @Override
        public String toString() {
            return "LifecycleResponseSnapshot[status=" + reservationStatus
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
    private LifecycleResponseSnapshot lifecycleResponseSnapshot;
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

    public static IdempotencyCommand processingLifecycle(
            String commandId,
            String userId,
            String operationId,
            String commandType,
            String payloadHash,
            Instant createdAt
    ) {
        if (!"CONFIRM".equals(commandType) && !"CANCEL".equals(commandType)) {
            throw new IllegalArgumentException("Unsupported lifecycle command type.");
        }
        IdempotencyCommand command = new IdempotencyCommand(
                commandId, userId, operationId, payloadHash, createdAt
        );
        command.commandType = commandType;
        command.active = false;
        return command;
    }

    public void succeed(String reservationId, ResponseSnapshot responseSnapshot) {
        if (state != State.PROCESSING) {
            throw new IllegalStateException("Only processing commands can succeed.");
        }
        this.reservationId = reservationId;
        this.responseSnapshot = responseSnapshot;
        this.state = State.SUCCEEDED;
    }

    public void succeedLifecycle(
            String reservationId,
            LifecycleResponseSnapshot snapshot,
            Instant terminalAt,
            Instant purgeAt
    ) {
        if (state != State.PROCESSING) {
            throw new IllegalStateException("Only processing commands can succeed.");
        }
        this.reservationId = reservationId;
        this.lifecycleResponseSnapshot = snapshot;
        this.state = State.SUCCEEDED;
        this.terminalAt = terminalAt;
        this.purgeAt = purgeAt;
        this.active = false;
    }

    public void finishReserveLifecycle(Instant terminalAt, Instant purgeAt) {
        if (!"RESERVE".equals(commandType) || state != State.SUCCEEDED) {
            throw new IllegalStateException("Only succeeded reserve commands can finish.");
        }
        this.terminalAt = terminalAt;
        this.purgeAt = purgeAt;
        this.active = false;
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

    public String getCommandType() {
        return commandType;
    }

    public State getState() {
        return state;
    }

    public ResponseSnapshot getResponseSnapshot() {
        return responseSnapshot;
    }

    public LifecycleResponseSnapshot getLifecycleResponseSnapshot() {
        return lifecycleResponseSnapshot;
    }

    public String getReservationId() {
        return reservationId;
    }

    public Instant getTerminalAt() {
        return terminalAt;
    }

    public Instant getPurgeAt() {
        return purgeAt;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "IdempotencyCommand[state=" + state + ", sensitiveFields=[REDACTED]]";
    }
}
