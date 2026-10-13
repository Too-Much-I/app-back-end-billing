package web.tosunsaeng.billing.domain.payment.recovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

@Component
@ConditionalOnProperty(prefix = "billing.payment.recovery", name = "retention-enabled", havingValue = "true")
public final class PaymentRecoveryRetentionWorker {
    private final PaymentRecoveryRetention retention;
    private final MeterRegistry metrics;
    private volatile boolean ready;
    public PaymentRecoveryRetentionWorker(PaymentStore store, PaymentProperties properties, Clock clock, MeterRegistry metrics) {
        retention = new PaymentRecoveryRetention(store, properties, clock); this.metrics = metrics;
    }
    @EventListener(ApplicationReadyEvent.class) public void ready() { ready = true; }
    @Scheduled(fixedDelay = 1000) public void tick() {
        if (!ready) return;
        try {
            int removed = retention.batch();
            metrics.counter("billing.recovery.retention.removed").increment(removed);
        } catch (RuntimeException failure) { metrics.counter("billing.recovery.retention.failed").increment(); }
    }
}
