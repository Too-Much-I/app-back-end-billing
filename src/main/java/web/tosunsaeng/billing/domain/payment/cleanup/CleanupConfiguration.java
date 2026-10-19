package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.*;

@Configuration(proxyBeanMethods = false)
public class CleanupConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "billing.payment.cleanup", name = "reconciliation-enabled", havingValue = "true")
    CleanupWorker cleanupWorker(PaymentStore store, PaymentProperties properties, Clock clock, MeterRegistry metrics,
            web.tosunsaeng.billing.domain.payment.worker.PaymentProviderWorkerRuntime runtime,
            ObjectProvider<web.tosunsaeng.billing.domain.payment.worker.PaymentSlackSink> slack) {
        validateFlags(properties);
        var sink = properties.getCleanup().isAlertsEnabled() ? slack.getIfAvailable() : null;
        if (properties.getCleanup().isAlertsEnabled() && sink == null)
            throw new IllegalStateException("Cleanup alerts require enabled payment Slack delivery");
        var local = new CleanupLocalReader(store);
        var policy = new CleanupDeletionPolicy(properties.getEnvironment());
        var purger = properties.getCleanup().isPurgeEnabled() ? new CleanupPurger(store, properties, clock, local, policy) : null;
        var processor = new CleanupProcessor(store, properties, clock, local, runtime.cleanupReader(), policy, purger);
        var alerts = new CleanupAlerts(store, properties.getEnvironment(), clock,
                sink == null ? summary -> false : sink::sendCleanup, metrics, properties.getCleanup().isAlertsEnabled());
        return new CleanupWorker(processor, alerts, clock, metrics);
    }
    @Bean ApplicationRunner cleanupStartup(PaymentStore store, PaymentProperties properties) {
        return args -> {
            validateFlags(properties);
            if (!properties.getCleanup().isReconciliationEnabled()) return;
            PaymentRecoverySchema.verify(store.database());
            CleanupSchema.verify(store.database());
            new PaymentRecoveryFence(store, properties).capture();
            var control = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(new org.bson.Document(
                    "environment", properties.getEnvironment()).append("consumer", "billing")).first();
            if (control == null || !Long.valueOf(4).equals(control.get("schemaVersion"))) throw CleanupSchema.mismatch();
        };
    }
    @Bean ApplicationListener<ApplicationReadyEvent> cleanupReady(ObjectProvider<CleanupWorker> worker) {
        return event -> worker.ifAvailable(CleanupWorker::start);
    }
    public static void validateFlags(PaymentProperties properties) {
        if (properties.getCleanup().isPurgeEnabled() && !properties.getCleanup().isReconciliationEnabled())
            throw new IllegalStateException("Cleanup purge requires reconciliation");
        if (properties.getCleanup().isAlertsEnabled() && !properties.getCleanup().isReconciliationEnabled())
            throw new IllegalStateException("Cleanup alerts require reconciliation");
        if (properties.getCleanup().isReconciliationEnabled() && (!properties.getRecovery().isFenceRequired()
                || !java.util.Set.of("SANDBOX", "PRODUCTION").contains(properties.getEnvironment())))
            throw new IllegalStateException("Cleanup requires an isolated environment and recovery fence");
    }
}
