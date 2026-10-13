package web.tosunsaeng.billing.domain.payment.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.PaymentWithdrawal;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentWithdrawalException.unavailable;

@Service
public class PaymentWithdrawalService {
    public enum Outcome { APPLIED, NOOP, DUPLICATE }
    private final PaymentStore store;
    private final PaymentWithdrawalApplier applier;
    private final web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence fence;
    public PaymentWithdrawalService(PaymentStore store, PaymentProperties properties, Clock clock) {
        this.store = store;
        this.applier = new PaymentWithdrawalApplier(store, properties, clock);
        this.fence = new web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence(store, properties);
    }
    public Outcome process(PaymentWithdrawal event) {
        try {
            var ticket = fence.capture();
            return store.transaction(session -> {
                fence.acquire(session, ticket, web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence.Writer.PUSH);
                return applier.applyEvent(session, event);
            });
        } catch (InternalApiException error) { throw error; }
        catch (RuntimeException error) { throw unavailable(); }
    }
}
