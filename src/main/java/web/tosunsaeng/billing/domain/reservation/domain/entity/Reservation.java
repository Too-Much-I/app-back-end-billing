package web.tosunsaeng.billing.domain.reservation.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "reservations")
public class Reservation {

    public enum Kind {
        INITIAL,
        REPLACEMENT
    }

    public enum Status {
        RESERVED,
        CONFIRMED,
        CANCELED,
        EXPIRED
    }

    @Id
    private String id;
    private String reservationId;
    private String callerService;
    private String subjectRefId;
    private String operationId;
    private String payloadHash;
    private Kind reservationKind;
    private Status status;
    private String attemptGroupId;
    private String proposedSessionId;
    private String mockExamId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant terminalAt;
    private long version;
    private Boolean activeGuard;

    protected Reservation() {
    }

    private Reservation(
            String reservationId,
            String subjectRefId,
            String operationId,
            String payloadHash,
            Kind reservationKind,
            String attemptGroupId,
            String proposedSessionId,
            String mockExamId,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = reservationId;
        this.reservationId = reservationId;
        this.callerService = "LEARNING_CORE";
        this.subjectRefId = subjectRefId;
        this.operationId = operationId;
        this.payloadHash = payloadHash;
        this.reservationKind = reservationKind;
        this.status = Status.RESERVED;
        this.attemptGroupId = attemptGroupId;
        this.proposedSessionId = proposedSessionId;
        this.mockExamId = mockExamId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.version = 1;
        this.activeGuard = true;
    }

    public static Reservation reserved(
            String reservationId,
            String subjectRefId,
            String operationId,
            String payloadHash,
            Kind reservationKind,
            String attemptGroupId,
            String proposedSessionId,
            String mockExamId,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new Reservation(
                reservationId, subjectRefId, operationId, payloadHash, reservationKind,
                attemptGroupId, proposedSessionId, mockExamId, createdAt, expiresAt
        );
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getCallerService() {
        return callerService;
    }

    public String getSubjectRefId() {
        return subjectRefId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Kind getReservationKind() {
        return reservationKind;
    }

    public Status getStatus() {
        return status;
    }

    public String getAttemptGroupId() {
        return attemptGroupId;
    }

    public String getProposedSessionId() {
        return proposedSessionId;
    }

    public String getMockExamId() {
        return mockExamId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTerminalAt() {
        return terminalAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Reservation[kind=" + reservationKind + ", status=" + status
                + ", sensitiveFields=[REDACTED]]";
    }
}
