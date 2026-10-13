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
    CleanupWorker cleanupWorker(PaymentStore store, PaymentProperties properties, Clock clock, MeterRegistry metrics) {
        // Deliberate PR1 wiring: no autowired fake/provider that could prove absence in production.
        var processor = new CleanupProcessor(store, properties, clock, CleanupEvidence.unavailableLocal(),
                CleanupEvidence.unavailableProvider(), CleanupEvidence.unavailableCoverage());
        var alerts = new CleanupAlerts(store, properties.getEnvironment(), clock, summary -> false, metrics);
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
        if (properties.getCleanup().isPurgeEnabled() || properties.getCleanup().isAlertsEnabled())
            throw new IllegalStateException("Production cleanup does not support purge or external alert delivery yet");
        if (properties.getCleanup().isReconciliationEnabled() && (!properties.getRecovery().isFenceRequired()
                || !java.util.Set.of("SANDBOX", "PRODUCTION").contains(properties.getEnvironment())))
            throw new IllegalStateException("Cleanup requires an isolated environment and recovery fence");
    }
}
