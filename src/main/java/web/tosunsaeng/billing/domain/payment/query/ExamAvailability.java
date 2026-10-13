package web.tosunsaeng.billing.domain.payment.query;

import java.util.List;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Action;

/** Boolean summary is derived, never an independent authorization decision. */
public record ExamAvailability(Boolean canTakeExam, Action action, Action newAttempt, Action retake,
                               Source preferredSource, Boolean hasInProgress, List<String> reasonCodes) {
    public enum Source { PAID, FREE_NEW, FREE_RETAKE }
    public ExamAvailability {
        if (action == null || newAttempt == null || retake == null) throw new IllegalArgumentException("Invalid availability");
        Action expected = newAttempt == Action.ALLOWED || retake == Action.ALLOWED ? Action.ALLOWED
                : newAttempt == Action.PENDING || retake == Action.PENDING ? Action.PENDING : Action.BLOCKED;
        if (action != expected || action == Action.ALLOWED && preferredSource == null || action != Action.ALLOWED && preferredSource != null)
            throw new IllegalArgumentException("Inconsistent availability");
        canTakeExam = action == Action.PENDING ? null : action == Action.ALLOWED;
        reasonCodes = List.copyOf(reasonCodes);
    }
    public static ExamAvailability unavailable() {
        return new ExamAvailability(null, Action.PENDING, Action.PENDING, Action.PENDING, null, null, List.of("ADMISSION_NOT_READY"));
    }
}
