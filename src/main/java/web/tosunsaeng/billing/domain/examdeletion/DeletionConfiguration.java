package web.tosunsaeng.billing.domain.examdeletion;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.reservation.paid.*;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeletionProperties.class)
public class DeletionConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.billing.exam-deletion", name = "enabled", havingValue = "true")
    DeletionService deletionService(PaidExamTransactions tx, PaidReservationService admissionReadiness,
            PaymentStore store, PaymentProperties payments, WorkloadJwtProperties workload, Clock clock) {
        if (!workload.isLearningCoreReceiverEnabled()) throw new IllegalStateException("Deletion requires LC workload receiver");
        workload.validateEnvironment(payments.getEnvironment());
        DeletionSchema.verify(store.database());
        return new DeletionService(tx, store, payments, workload.getEnvironment(), clock);
    }
}
