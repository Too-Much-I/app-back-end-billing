package web.tosunsaeng.billing.domain.reservation.paid;

public record RestartContext(String previousAttemptGroupId, String previousSessionId, StartReason startReason) {
    public enum StartReason { RESTART_INCOMPLETE, NEW_AFTER_SUBMISSION, NEW_AFTER_COMPLETION }
    public RestartContext {
        if (previousAttemptGroupId == null || !previousAttemptGroupId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || previousSessionId == null || previousSessionId.isBlank() || previousSessionId.length() > 128
                || !previousSessionId.equals(previousSessionId.strip()) || previousSessionId.chars().anyMatch(Character::isISOControl) || startReason == null)
            throw new IllegalArgumentException("Invalid restart context");
    }
    @Override public String toString() { return "RestartContext[REDACTED]"; }
}
