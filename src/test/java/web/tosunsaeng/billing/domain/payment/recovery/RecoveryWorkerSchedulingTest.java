package web.tosunsaeng.billing.domain.payment.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;

class RecoveryWorkerSchedulingTest {
    @Test void snapshotSchedulerOnlyExistsWhenWorkerEnabledAndHasOneThread() {
        var context = new ApplicationContextRunner().withUserConfiguration(RecoveryWorkerScheduling.class);
        context.run(c -> assertThat(c).doesNotHaveBean("paymentSnapshotScheduler"));
        context.withPropertyValues("billing.payment.recovery.snapshot-worker-enabled=true").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean("taskScheduler")).isNotSameAs(c.getBean("paymentSnapshotScheduler"));
            assertThat(c.getBean("paymentSnapshotScheduler", ThreadPoolTaskScheduler.class).getPoolSize()).isEqualTo(0);
            assertThat(c.getBean("paymentSnapshotScheduler", ThreadPoolTaskScheduler.class).getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
        });
    }
}
