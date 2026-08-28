package web.tosunsaeng.billing.domain.reservation.dto.request;

public record CancelRequest(String userId, Reason reason) {
    public enum Reason {
        SESSION_COMMIT_FAILED,
        CALLER_ABORTED
    }

    @Override
    public String toString() {
        return "CancelRequest[reason=" + reason + ", sensitiveFields=[REDACTED]]";
    }
}
