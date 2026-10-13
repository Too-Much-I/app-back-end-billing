package web.tosunsaeng.billing.domain.payment.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Keep snapshot hashing off the shared feed/retention/heartbeat scheduling thread. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "billing.payment.recovery", name = "snapshot-worker-enabled", havingValue = "true")
public class RecoveryWorkerScheduling {
    // Declaring a custom scheduler suppresses Boot's default. Keep the unqualified jobs separate.
    @Bean(name = "taskScheduler")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("billing-scheduled-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
    @Bean(name = "paymentSnapshotScheduler")
    ThreadPoolTaskScheduler paymentSnapshotScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("payment-snapshot-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
