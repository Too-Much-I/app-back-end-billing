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

    public enum ContinuationReason {
        PHONE_REJOIN
    }

    public enum Status {
        RESERVED,
        CONFIRMED,
        CANCELED,
        EXPIRED
    }

    // Absent on legacy records; never infer TRIAL from absence.
    private web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot sourceSnapshot;

    public web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot getSourceSnapshot() { return sourceSnapshot; }
    public Reservation withSourceSnapshot(web.tosunsaeng.billing.domain.reservation.paid.EntitlementSourceSnapshot source) {
        java.util.Objects.requireNonNull(source);
        if (sourceSnapshot != null && !sourceSnapshot.equals(source)) throw new IllegalStateException("Source snapshot is immutable");
        sourceSnapshot = source;
        return this;
    }

    private web.tosunsaeng.billing.domain.reservation.paid.RestartContext restartContext;
    private web.tosunsaeng.billing.domain.reservation.paid.ExamTransition transition;
    private String previousGroupId;
    private Long previousOwnerEpoch;
    private String confirmRejection;
    public web.tosunsaeng.billing.domain.reservation.paid.RestartContext getRestartContext() { return restartContext; }
    public web.tosunsaeng.billing.domain.reservation.paid.ExamTransition getTransition() { return transition; }
    public String getPreviousGroupId() { return previousGroupId; }
    public Long getPreviousOwnerEpoch() { return previousOwnerEpoch; }
    public String getConfirmRejection() { return confirmRejection; }
    public Reservation withTransitionContext(web.tosunsaeng.billing.domain.reservation.paid.RestartContext context,
            String previousGroupId, Long epoch) {
        if (this.previousGroupId != null) throw new IllegalStateException("Immutable transition context");
        this.restartContext = context; this.previousGroupId = previousGroupId; this.previousOwnerEpoch = epoch;
        if (context != null && reservationKind == Kind.INITIAL)
            this.transition = new web.tosunsaeng.billing.domain.reservation.paid.ExamTransition(context.previousAttemptGroupId(), context.previousSessionId());
        return this;
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
    private ContinuationReason continuationReason;
    private String continuationId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant terminalAt;
    private long version;
    private Boolean activeGuard;
    private Long sessionOwnerEpoch;

    public Long getSessionOwnerEpoch() { return sessionOwnerEpoch; }

    public Reservation withSessionOwnerEpoch(long epoch) {
        if (epoch < 1 || sessionOwnerEpoch != null) { throw new IllegalStateException("Invalid epoch initialization"); }
        sessionOwnerEpoch = epoch;
        return this;
    }

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
            ContinuationReason continuationReason,
            String continuationId,
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
        this.continuationReason = continuationReason;
        this.continuationId = continuationId;
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
            ContinuationReason continuationReason,
            String continuationId,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new Reservation(
                reservationId, subjectRefId, operationId, payloadHash, reservationKind,
                attemptGroupId, proposedSessionId, mockExamId,
                continuationReason, continuationId, createdAt, expiresAt
        );
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
        return reserved(
                reservationId, subjectRefId, operationId, payloadHash, reservationKind,
                attemptGroupId, proposedSessionId, mockExamId, null, null,
                createdAt, expiresAt
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

    public ContinuationReason getContinuationReason() {
        return continuationReason;
    }

    public String getContinuationId() {
        return continuationId;
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
