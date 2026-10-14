package web.tosunsaeng.billing.domain.payment.worker;

import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;
import static org.assertj.core.api.Assertions.*;

class PaymentProviderWorkerConfigurationTest {
    @Test void offRequiresNoDatabaseCredentialClockOrProviderBean() {
        new ApplicationContextRunner().withUserConfiguration(PaymentProviderWorkerConfiguration.class).run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(PaymentProviderWorkerRuntime.class);
            assertThat(c.getBean(PaymentProviderWorkerProperties.class).isEnabled()).isFalse();
            assertThat(c.getBean(PaymentProviderWorkerProperties.class).isTerminalSyncDeleteEnabled()).isFalse();
            assertThat(c.getBean(PaymentProviderWorkerProperties.class).isReferenceRetentionEnabled()).isFalse();
            assertThat(c.getBean(PaymentProviderWorkerProperties.class).isReconciliationEnabled()).isFalse();
            assertThat(c.getBean(PaymentProviderWorkerProperties.class).isAlertsEnabled()).isFalse();
        });
    }
    @Test void onWithoutDependenciesDoesNotPretendReady() {
        new ApplicationContextRunner().withUserConfiguration(PaymentProviderWorkerConfiguration.class)
                .withPropertyValues("billing.payment.provider.worker.enabled=true").run(c -> assertThat(c).hasFailed());
    }
    @Test void publicSyncWithoutWorkerCannotStartEvenWithOnlyFlagSet() {
        new ApplicationContextRunner().withUserConfiguration(PaymentProviderWorkerConfiguration.class)
                .withPropertyValues("billing.payment.sync-enabled=true").run(c -> assertThat(c).hasFailed());
    }
    @Test void schedulerOnlyRuntimeCannotExposeAnUnwiredService() {
        try (var runtime = new PaymentProviderWorkerRuntime(() -> {}, () -> {}, () -> {}, new SimpleMeterRegistry(), 1000)) {
            assertThatThrownBy(runtime::syncService).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void intervalBatchAndSharedDbCannotBeUnboundedOrImplicit() {
        var p = new PaymentProperties(); p.setEnvironment("SANDBOX"); p.getRecovery().setFenceRequired(true);
        var mongo = new BillingMongoProperties(); mongo.setRequireTransactions(true); mongo.setInitializeIndexes(true);
        var w = new PaymentProviderWorkerProperties();
        assertThatThrownBy(() -> PaymentProviderWorkerConfiguration.validate(p, mongo, w, "billing")).isInstanceOf(IllegalStateException.class);
        w.setBudgetDatabase("billing");
        assertThatThrownBy(() -> PaymentProviderWorkerConfiguration.validate(p, mongo, w, "billing")).isInstanceOf(IllegalStateException.class);
        w.setBudgetDatabase("fake_shared_budget"); PaymentProviderWorkerConfiguration.validate(p, mongo, w, "billing");
        w.setBatch(101); assertThatThrownBy(() -> PaymentProviderWorkerConfiguration.validate(p, mongo, w, "billing")).isInstanceOf(IllegalStateException.class);
        w.setBatch(100); w.setPollMillis(999);
        assertThatThrownBy(() -> PaymentProviderWorkerConfiguration.validate(p, mongo, w, "billing")).isInstanceOf(IllegalStateException.class);
    }
    @Test void schedulerConstructsDormantAndDoesNotLetSourceFailureSuppressAccountWork() {
        var calls = new AtomicInteger(); var closes = new AtomicInteger(); var registry = new SimpleMeterRegistry();
        try (var runtime = new PaymentProviderWorkerRuntime(() -> { throw new IllegalStateException("fake"); }, calls::incrementAndGet, closes::incrementAndGet, registry, 1000)) {
            assertThat(calls.get()).isZero(); runtime.tick(); assertThat(calls.get()).isEqualTo(1);
            assertThat(registry.get("billing_payment_provider_worker_failures").tag("phase", "source").counter().count()).isEqualTo(1);
            runtime.close(); runtime.close(); assertThat(closes.get()).isEqualTo(1);
        }
    }
    @Test void hourlyTaskDoesNotHotLoopOnFailureAndAllowsExactBoundary() {
        var calls = new AtomicInteger();
        var now = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-10-14T00:00:00Z"));
        var clock = new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            public java.time.Instant instant() { return now.get(); }
        };
        var task = new PaymentHourlyTask(clock, () -> { calls.incrementAndGet(); throw new IllegalStateException("fake"); });
        assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class);
        task.run(); now.set(now.get().plusSeconds(3599)); task.run(); assertThat(calls.get()).isEqualTo(1);
        now.set(now.get().plusSeconds(1)); assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class);
        now.set(now.get().minusSeconds(3601)); task.run(); assertThat(calls.get()).isEqualTo(2);
    }
    @Test void retentionPhaseStillRunsWhenBothOtherPhasesFail() {
        var calls = new AtomicInteger(); var registry = new SimpleMeterRegistry();
        Runnable failing = () -> { throw new IllegalStateException("fake"); };
        try (var runtime = new PaymentProviderWorkerRuntime(failing, failing, calls::incrementAndGet, () -> {}, registry, 1000)) {
            runtime.tick(); assertThat(calls.get()).isEqualTo(1);
        }
    }
}
