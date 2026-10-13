package web.tosunsaeng.billing.domain.payment.cleanup;

import com.mongodb.client.ClientSession;
import java.time.Instant;
import java.util.List;

/** Internal ports. No production provider or absence-proof capability exists in PR1. */
public final class CleanupEvidence {
    private CleanupEvidence() {}
    public enum Local { FOUND, NONE, PENDING, UNKNOWN }
    public enum Observation { FOUND, ABSENT_OBSERVED, UNKNOWN }
    public enum State { WAITING, VERIFYING, BLOCKED, FINANCIAL_RETAINED, READY }
    public enum Reason {
        PURCHASE_FOUND, PENDING_EVIDENCE, LOCAL_EVIDENCE_UNAVAILABLE, PROVIDER_UNAVAILABLE,
        PROVIDER_AUTH_ERROR, PROVIDER_CONTRACT_ERROR, PROVIDER_INCOMPLETE, COVERAGE_UNPROVEN,
        DRAIN_UNPROVEN, VERSION_CHANGED, PROOF_EXPIRED, RECOVERY_BLOCKED, DOCUMENT_LIMIT,
        DATA_INCONSISTENT, SCHEMA_MISMATCH
    }
    public record Subject(String environment, String userId, String lifecycleId, long lifecycleVersion,
                          String accountId, Long accountVersion, List<String> references,
                          Instant cursorActivityAt, long pendingEvidence, Instant eligibleAt, String epoch) {
        public Subject { references = List.copyOf(references); }
        @Override public String toString() { return "CleanupSubject[REDACTED]"; }
    }
    public record Provider(Observation observation, boolean complete, Reason failure) {
        public Provider {
            java.util.Objects.requireNonNull(observation);
            if (observation == Observation.UNKNOWN && failure == null) failure = Reason.PROVIDER_UNAVAILABLE;
        }
        public static Provider unknown() { return new Provider(Observation.UNKNOWN, false, Reason.PROVIDER_UNAVAILABLE); }
    }
    /** Approval applies to the exact subject supplied to the policy, never just an empty RC response. */
    public record Coverage(String adapterVersion, String coverageVersion, String drainPolicyVersion,
                           Instant validUntil) {
        public Coverage {
            for (String value : List.of(adapterVersion, coverageVersion, drainPolicyVersion))
                if (!value.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid cleanup policy version");
            java.util.Objects.requireNonNull(validUntil);
        }
    }
    public record Decision(State state, Reason reason, Coverage coverage) {
        public static Decision blocked(Reason reason) { return new Decision(State.BLOCKED, reason, null); }
    }
    @FunctionalInterface public interface LocalReader { Local read(ClientSession session, Subject subject); }
    @FunctionalInterface public interface ProviderReader { Provider read(Subject subject, Instant deadline); }
    @FunctionalInterface public interface CoveragePolicy {
        Coverage confirm(Subject subject, Provider observation, Instant now);
    }
    public static LocalReader unavailableLocal() { return (session, subject) -> Local.UNKNOWN; }
    public static ProviderReader unavailableProvider() { return (subject, deadline) -> Provider.unknown(); }
    public static CoveragePolicy unavailableCoverage() { return (subject, provider, now) -> null; }
}
