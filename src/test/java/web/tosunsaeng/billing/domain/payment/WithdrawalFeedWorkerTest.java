package web.tosunsaeng.billing.domain.payment;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalFeedWorkerTest {
    private ApplicationContextRunner context(PaymentStore store) {
        return new ApplicationContextRunner().withUserConfiguration(WithdrawalFeedWorker.class)
                .withBean(PaymentStore.class, () -> store)
                .withBean(PaymentProperties.class, PaymentProperties::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }
    @Test void defaultOffHasNoWorkerAndEnabledWithoutClientFailsClosed() {
        PaymentStore store = mock(PaymentStore.class);
        context(store).run(c -> assertThat(c).doesNotHaveBean(WithdrawalFeedWorker.class));
        context(store).withPropertyValues("billing.payment.recovery.feed-enabled=true")
                .run(c -> assertThat(c).hasFailed());
        verifyNoInteractions(store);
    }
    @Test void workerDoesNotRunBeforeApplicationReadyAndSchemaVerification() {
        PaymentStore store = mock(PaymentStore.class);
        var worker = new WithdrawalFeedWorker(store, new PaymentProperties(), Clock.systemUTC(),
                mock(IdentityWithdrawalRecoveryClient.class), new SimpleMeterRegistry());
        worker.tick();
        verifyNoInteractions(store);
    }
    @Test void snapshotWorkerNeedsClientButRetentionDoesNot() {
        PaymentStore store = mock(PaymentStore.class);
        new ApplicationContextRunner().withUserConfiguration(WithdrawalSnapshotWorker.class)
                .withBean(PaymentStore.class, () -> store).withBean(PaymentProperties.class, PaymentProperties::new)
                .withBean(Clock.class, Clock::systemUTC).withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("billing.payment.recovery.snapshot-worker-enabled=true")
                .run(c -> assertThat(c).hasFailed());
        var retention = new PaymentRecoveryRetentionWorker(store, new PaymentProperties(), Clock.systemUTC(), new SimpleMeterRegistry());
        retention.tick();
        var monitor = new PaymentRecoveryMonitor(store, new PaymentProperties(), Clock.systemUTC(), new SimpleMeterRegistry());
        monitor.sample();
        verifyNoInteractions(store);
    }
}
