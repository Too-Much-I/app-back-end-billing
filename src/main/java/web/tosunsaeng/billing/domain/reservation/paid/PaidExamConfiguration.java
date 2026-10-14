package web.tosunsaeng.billing.domain.reservation.paid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

@Configuration
public class PaidExamConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "billing.reservation", name = "paid-foundation-enabled", havingValue = "true")
    PaidExamTransactions paidExamTransactions(PaymentStore store, MongoTemplate template) {
        PaidExamSchema.verify(store.database()); // Read-only verification, never installs schema on startup.
        return new PaidExamTransactions(store, template);
    }
    @Bean
    @ConditionalOnProperty(prefix = "billing.reservation", name = "paid-recovery-enabled", havingValue = "true")
    MemberOperationRecovery memberOperationRecovery(PaidExamTransactions transactions, PaymentStore store,
            web.tosunsaeng.billing.domain.payment.config.PaymentProperties payment,
            web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties jwt,
            web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties workload, java.time.Clock clock) {
        workload.validateEnvironment(payment.getEnvironment());
        if (!payment.isMemberEvidenceEnabled() || !payment.isPurchaseAccountEnabled())
            throw new IllegalStateException("Member recovery requires purchase-account evidence writer");
        return new MemberOperationRecovery(transactions, store, payment, workload.getEnvironment(), jwt.getIssuer(), clock);
    }
    @Bean
    @ConditionalOnProperty(prefix = "billing.reservation", name = "paid-admission-enabled", havingValue = "true")
    Object rejectPrematurePaidAdmission() {
        throw new IllegalStateException("Paid admission requires the resolver/confirm runtime and joint activation gates");
    }
}
