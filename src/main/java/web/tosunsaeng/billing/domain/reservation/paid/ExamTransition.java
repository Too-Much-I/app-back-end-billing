package web.tosunsaeng.billing.domain.reservation.paid;

public record ExamTransition(String previousAttemptGroupId, String previousSessionId) {
    public ExamTransition {
        new RestartContext(previousAttemptGroupId, previousSessionId, RestartContext.StartReason.RESTART_INCOMPLETE);
    }
    @Override public String toString() { return "ExamTransition[REDACTED]"; }
}
