package web.tosunsaeng.billing.domain.reservation.paid;

import java.net.URI;
import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.worker.PaymentProviderAlerts;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;

/** Wiring only: PaymentRefundConfiguration still rejects activation pending external gates. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "billing.payment.refund", name = "processing-enabled", havingValue = "true")
public class RefundRuntimeConfiguration {
    @Bean
    RefundFanout refundFanout(PaidExamTransactions tx, MongoTemplate mongo, MemberOperationRecovery recovery,
            PaymentStore store, PaymentProperties payment, WorkloadJwtProperties workload, TrialEligibilityProperties trial, Clock clock) {
        workload.validateEnvironment(payment.getEnvironment());
        if (!payment.getRecovery().isFenceRequired()) throw new IllegalStateException("Refund processing requires recovery fence");
        // Cleanup does not require opening new paid admission routes.
        var service = new PaidReservationService(tx, recovery, store, payment, workload.getEnvironment(), trial.getExpectedConsumerScopeId(), clock);
        return new RefundFanout(tx, mongo, service, store, payment, workload.getEnvironment(), trial.getExpectedConsumerScopeId(), clock);
    }
    @Bean
    RefundAlerts refundAlerts(PaymentStore store, WorkloadJwtProperties workload, Clock clock,
            PaymentProviderAlerts.Sink sink, MeterRegistry metrics) {
        return new RefundAlerts(store, workload.getEnvironment(), clock, sink, metrics);
    }
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "billing.payment.refund", name = "publisher-enabled", havingValue = "true")
    static class Publisher {
        @Bean(destroyMethod = "close") RefundAccessHttpTransport refundTransport() { return new RefundAccessHttpTransport(); }
        @Bean RefundAccessPublisher refundPublisher(PaidExamTransactions tx, MongoTemplate mongo, PaymentStore store,
                PaymentProperties payment, WorkloadJwtProperties workload, Clock clock, RefundAccessHttpTransport transport) {
            workload.validateEnvironment(payment.getEnvironment());
            return new RefundAccessPublisher(tx, mongo, store, payment, workload.getEnvironment(),
                    URI.create(payment.getRefund().getLearningCoreOrigin()), payment.getRefund().getPrivateJwk(), clock, transport);
        }
    }
}
