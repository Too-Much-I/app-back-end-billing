package web.tosunsaeng.billing.domain.payment.recovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Deliberately requires a client bean: enabling without an authenticated adapter must fail startup. */
@Component
@ConditionalOnProperty(prefix = "billing.payment.recovery", name = "feed-enabled", havingValue = "true")
public final class WithdrawalFeedWorker {
    private final WithdrawalFeedProcessor processor;
    private final MeterRegistry metrics;
    private volatile boolean ready;
    public WithdrawalFeedWorker(PaymentStore store, PaymentProperties properties, Clock clock,
                                IdentityWithdrawalRecoveryClient client, MeterRegistry metrics) {
        this.processor = new WithdrawalFeedProcessor(store, properties, clock, client,
                new RecoveryRetryPolicy(() -> ThreadLocalRandom.current().nextDouble()));
        this.metrics = metrics;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { ready = true; }
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (!ready) return; // Schema ApplicationRunner must have completed first.
        try {
            int pages = processor.tick();
            metrics.counter("billing.recovery.feed.pages").increment(pages);
            metrics.counter("billing.recovery.feed.tick", "outcome", "SUCCESS").increment();
        } catch (RuntimeException failure) {
            // Do not log payload, cursor, exception cause or request IDs.
            metrics.counter("billing.recovery.feed.tick", "outcome", "FAILED").increment();
        }
    }
}
