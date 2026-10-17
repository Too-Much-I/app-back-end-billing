package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;

class CleanupDeletionPolicyTest {
    final Instant now = Instant.parse("2026-10-17T00:00:00Z");
    final CleanupDeletionPolicy policy = new CleanupDeletionPolicy("SANDBOX");
    final Provider empty = new Provider(Observation.ABSENT_OBSERVED, true, null);
    Subject subject(Instant activity, long pending) {
        return new Subject("SANDBOX", "user", "life", 1, "account", 1L, List.of("ref"), activity, pending, now, "epoch");
    }
    @Test void approvedObservationIsNotAnAbsoluteStoreAbsenceGuarantee() {
        var s = subject(now.minusSeconds(1920), 0);
        var proof = policy.confirm(s, empty, now);
        assertThat(proof).isNotNull();
        assertThat(policy.valid(s, proof, now, now.plusSeconds(3599))).isTrue();
        assertThat(policy.valid(s, proof, now, now.plusSeconds(3600))).isFalse();
    }
    @Test void activityDrainPendingAndFifteenDayBoundaryAreRequired() {
        assertThat(policy.confirm(subject(now.minusSeconds(1919), 0), empty, now)).isNull();
        assertThat(policy.confirm(subject(now.minusSeconds(1920), 1), empty, now)).isNull();
        assertThat(policy.confirm(subject(now.minusSeconds(1920), 0), empty, now.minusNanos(1))).isNull();
    }
    @Test void incompleteUnknownFoundAndOldProofsCannotAuthorizeDeletion() {
        var s = subject(now.minusSeconds(1920), 0);
        for (Provider p : List.of(Provider.unknown(), new Provider(Observation.ABSENT_OBSERVED, false, null),
                new Provider(Observation.FOUND, true, null))) assertThat(policy.confirm(s, p, now)).isNull();
        assertThat(policy.valid(s, new Coverage("old", "old", "old", now.plusSeconds(3600)), now, now)).isFalse();
    }
}
