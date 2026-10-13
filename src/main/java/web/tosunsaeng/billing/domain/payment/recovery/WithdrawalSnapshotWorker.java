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
@ConditionalOnProperty(prefix = "billing.payment.recovery", name = "snapshot-worker-enabled", havingValue = "true")
public final class WithdrawalSnapshotWorker {
    private final WithdrawalSnapshotProcessor processor;
    private final MeterRegistry metrics;
    private volatile boolean ready;
    public WithdrawalSnapshotWorker(PaymentStore store, PaymentProperties properties, Clock clock,
                                     IdentityWithdrawalRecoveryClient client, MeterRegistry metrics) {
        processor = new WithdrawalSnapshotProcessor(store, properties, clock, client); this.metrics = metrics;
    }
    @EventListener(ApplicationReadyEvent.class) public void ready() { ready = true; }
    @Scheduled(fixedDelay = 1000, scheduler = "paymentSnapshotScheduler") public void tick() {
        if (!ready) return;
        try { processor.tick(); metrics.counter("billing.recovery.snapshot.tick", "outcome", "RETURNED").increment(); }
        catch (RuntimeException failure) { metrics.counter("billing.recovery.snapshot.tick", "outcome", "FAILED").increment(); }
    }
}
