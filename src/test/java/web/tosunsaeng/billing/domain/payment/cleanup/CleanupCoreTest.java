package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CleanupCoreTest {
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    final CleanupEligibilityEvaluator evaluator = new CleanupEligibilityEvaluator();
    Subject subject(long pending) {
        return new Subject("SANDBOX", "private-user", "private-life", 1, null, null, List.of(), null,
                pending, NOW, UUID.randomUUID().toString());
    }
    Coverage proof(Instant until) { return new Coverage("adapter-1", "coverage-1", "drain-1", until); }
    Provider empty() { return new Provider(Observation.ABSENT_OBSERVED, true, null); }
    @Test void emptyObservationIsNeverProof() {
        assertThat(evaluator.evaluate(subject(0), Local.NONE, empty(), null, NOW).reason()).isEqualTo(Reason.COVERAGE_UNPROVEN);
    }
    @Test void incompleteObservationCannotBeOverriddenByCoverage() {
        assertThat(evaluator.evaluate(subject(0), Local.NONE, new Provider(Observation.ABSENT_OBSERVED, false, null),
                proof(NOW.plusSeconds(60)), NOW).reason()).isEqualTo(Reason.PROVIDER_INCOMPLETE);
    }
    @ParameterizedTest @EnumSource(Local.class) void pendingAndFoundNeverBecomeReady(Local local) {
        assertThat(evaluator.evaluate(subject(1), local, empty(), proof(NOW.plusSeconds(60)), NOW).state())
                .isEqualTo(local == Local.FOUND ? State.FINANCIAL_RETAINED : State.BLOCKED);
    }
    @Test void proofExpiryIsExclusiveAndEligibilityInclusive() {
        assertThat(evaluator.evaluate(subject(0), Local.NONE, empty(), proof(NOW), NOW).state()).isEqualTo(State.BLOCKED);
        assertThat(evaluator.evaluate(subject(0), Local.NONE, empty(), proof(NOW.plusSeconds(1)), NOW).state()).isEqualTo(State.READY);
        assertThat(evaluator.evaluate(subject(0), Local.NONE, empty(), proof(NOW.plusSeconds(1)), NOW.minusNanos(1)).state()).isEqualTo(State.BLOCKED);
    }
    @Test void actualPurchasePrecedesAbsenceAndPending() {
        assertThat(evaluator.evaluate(subject(2), Local.UNKNOWN, new Provider(Observation.FOUND, false, null), null, NOW).state())
                .isEqualTo(State.FINANCIAL_RETAINED);
    }
    @Test void productionDefaultsAreUnavailableAndFlagsFailClosed() {
        assertThat(unavailableLocal().read(null, subject(0))).isEqualTo(Local.UNKNOWN);
        assertThat(unavailableProvider().read(subject(0), NOW).observation()).isEqualTo(Observation.UNKNOWN);
        assertThat(unavailableCoverage().confirm(subject(0), empty(), NOW)).isNull();
        var props = new PaymentProperties();
        assertThatCode(() -> CleanupConfiguration.validateFlags(props)).doesNotThrowAnyException();
        props.getCleanup().setReconciliationEnabled(true);
        assertThatThrownBy(() -> CleanupConfiguration.validateFlags(props)).isInstanceOf(IllegalStateException.class);
        props.setEnvironment("SANDBOX"); props.getRecovery().setFenceRequired(true);
        assertThatCode(() -> CleanupConfiguration.validateFlags(props)).doesNotThrowAnyException();
        props.getCleanup().setPurgeEnabled(true);
        assertThatThrownBy(() -> CleanupConfiguration.validateFlags(props)).isInstanceOf(IllegalStateException.class);
        props.getCleanup().setPurgeEnabled(false); props.getCleanup().setAlertsEnabled(true);
        assertThatThrownBy(() -> CleanupConfiguration.validateFlags(props)).isInstanceOf(IllegalStateException.class);
    }
    @Test void policyVersionsRejectControlCharactersAndRecordsAreRedacted() {
        assertThatThrownBy(() -> new Coverage("value\n", "v1", "v1", NOW)).isInstanceOf(IllegalArgumentException.class);
        Subject s = subject(0);
        assertThat(s.toString()).doesNotContain("private-user", "private-life", s.epoch());
        assertThat(new CleanupProcessor.Lease("secret-job", 1, "secret-lease", s, Local.NONE).toString())
                .doesNotContain("secret", "private");
    }
    @Test void startupOffDoesNotAccessDatabaseAndUnsupportedFlagsFailBeforeAnyWrite() throws Exception {
        var store = mock(web.tosunsaeng.billing.domain.payment.repository.PaymentStore.class);
        var props = new PaymentProperties();
        var runner = new CleanupConfiguration().cleanupStartup(store, props);
        runner.run(new org.springframework.boot.DefaultApplicationArguments());
        verifyNoInteractions(store);
        props.getCleanup().setPurgeEnabled(true);
        assertThatThrownBy(() -> runner.run(new org.springframework.boot.DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store);
    }
}
