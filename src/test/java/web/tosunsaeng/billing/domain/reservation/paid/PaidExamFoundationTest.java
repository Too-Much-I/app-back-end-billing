package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class PaidExamFoundationTest {
    final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    @Test void sourceCannotChangeTypeOrConsumptionAndPaidDoesNotInventTrial() {
        var pass = new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.EXAM_PASS, "grant", null, null,
                null, null, null, now, null, null);
        var consumed = pass.bindConsumption("original");
        assertThat(consumed.bindConsumption("original")).isEqualTo(consumed);
        assertThatThrownBy(() -> consumed.bindConsumption("new")).isInstanceOf(IllegalStateException.class);
        assertThat(pass.consumptionId()).isNull();
        assertThatThrownBy(() -> new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.EXAM_PASS, "g", null, null,
                "account", null, null, now, null, null)).isInstanceOf(IllegalArgumentException.class);
        var paid = new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.SUBSCRIPTION, null, null, null,
                "account", "purchase", "entitlement", now, now, now.plusSeconds(60));
        assertThat(paid.toDocument().get("trialClaimId")).isNull();
        assertThatThrownBy(() -> paid.bindConsumption("c")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EntitlementSourceSnapshot(EntitlementSourceSnapshot.Type.SUBSCRIPTION, null, null, null,
                "account", "purchase", "entitlement", now, now.minusSeconds(60), now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void guaranteeUsesExactInstantAndDoesNotRenewOnRetry() {
        Instant boundary = now.plusSeconds(10_368_000);
        assertThat(PaidExamStore.withinGuarantee(now, boundary.minusNanos(1))).isTrue();
        assertThat(PaidExamStore.withinGuarantee(now, boundary)).isFalse();
        assertThat(PaidExamStore.withinGuarantee(now, boundary.plusNanos(1))).isFalse();
        assertThat(PaidExamStore.withinGuarantee(now, now.minusNanos(1))).isFalse();
    }
    @Test void offDoesNotRequireDatabaseAndAdmissionCannotBeEnabledPrematurely() {
        new ApplicationContextRunner().withUserConfiguration(PaidExamConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(PaidExamTransactions.class);
        });
        new ApplicationContextRunner().withUserConfiguration(PaidExamConfiguration.class)
                .withPropertyValues("billing.reservation.paid-admission-enabled=true")
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(PaidExamConfiguration.class)
                .withPropertyValues("billing.reservation.paid-foundation-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
