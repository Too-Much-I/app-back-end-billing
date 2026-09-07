package web.tosunsaeng.billing.domain.entitlement.dto.response;

import java.time.Instant;
import java.util.List;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;

public record EntitlementQueryResponse(Instant asOf, Consistency consistency, Status status, List<Benefit> benefits) {
    public enum Consistency { LOCAL_PROJECTION }
    public enum Status { READY, PENDING }
    public enum Action { ALLOWED, BLOCKED, PENDING }
    public enum Eligibility { VERIFIED, NOT_ELIGIBLE, UNKNOWN }
    public enum Usage { NOT_STARTED, INCOMPLETE, COMPLETED, MIXED, UNKNOWN }
    public enum Reason {
        ELIGIBILITY_UNKNOWN, PHONE_NOT_ELIGIBLE, OWNER_LINK_UNRESOLVED,
        RESERVATION_PENDING, RESERVATION_EXPIRY_PENDING, COMMAND_PENDING,
        ATTEMPT_IN_PROGRESS, GRADING_IN_PROGRESS, RETAKE_AVAILABLE,
        REJOIN_RESTART_AVAILABLE, BENEFIT_COMPLETED, NO_NEW_UNITS,
        RECONCILIATION_PENDING, AMBIGUOUS_ATTEMPT_CONTEXT
    }
    public record Benefit(String benefitCode, String displayName, String unit, Integer availableQuantity,
                          Eligibility eligibility, Usage usageState, Action newAttempt, Boolean hasInProgress,
                          Action retake, List<Reason> reasonCodes, List<Group> attemptGroups) {
        public boolean pending() {
            return availableQuantity == null || hasInProgress == null || newAttempt == Action.PENDING
                    || retake == Action.PENDING || attemptGroups.stream().anyMatch(g -> g.retake() == Action.PENDING);
        }
    }
    public record Group(String attemptGroupId, AttemptGroup.Status state, Boolean hasInProgress,
                        Action retake, List<Reason> reasonCodes) { }
}
