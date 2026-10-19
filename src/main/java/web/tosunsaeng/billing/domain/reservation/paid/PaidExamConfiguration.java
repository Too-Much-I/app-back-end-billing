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
    @Configuration
    @ConditionalOnProperty(prefix = "billing.reservation", name = "paid-admission-enabled", havingValue = "true")
    static class Runtime {
        @Bean
        PaidReservationService paidReservationService(PaidExamTransactions tx, MemberOperationRecovery recovery, PaymentStore store,
                web.tosunsaeng.billing.domain.payment.config.PaymentProperties payment,
                web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties workload,
                web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties trial,
                web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties attempts, java.time.Clock clock) {
            workload.validateEnvironment(payment.getEnvironment());
            if (!workload.isLearningCoreReceiverEnabled() || !payment.isEntitlementQueryEnabled() || !attempts.isEnabled())
                throw new IllegalStateException("Paid admission requires LC receiver, status events and integrated entitlement reader");
            web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema.verify(store.database());
            return new PaidReservationService(tx, recovery, store, payment, workload.getEnvironment(), trial.getExpectedConsumerScopeId(), clock);
        }
        @Bean
        PaidExamRouter paidExamRouter(MongoTemplate mongo, PaidReservationService service,
                web.tosunsaeng.billing.domain.payment.config.PaymentProperties payment,
                web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties workload) {
            return new PaidExamRouter(mongo, service, payment.getEnvironment(), workload.getEnvironment());
        }
        @Bean
        PaidAttemptEventService paidAttemptEvents(PaidReservationService readiness, PaidExamTransactions tx, MongoTemplate mongo,
                PaymentStore store, web.tosunsaeng.billing.domain.payment.config.PaymentProperties payment,
                web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties workload, java.time.Clock clock) {
            return new PaidAttemptEventService(tx, mongo, store, payment, workload.getEnvironment(), clock);
        }
        @Bean
        PaidExamQueryReader paidExamReader(PaidReservationService readiness, MongoTemplate mongo,
                web.tosunsaeng.billing.domain.payment.config.PaymentProperties payment,
                web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties jwt,
                web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties workload) {
            return new PaidExamQueryReader(mongo, workload.getEnvironment(), payment.getEnvironment(), jwt.getIssuer());
        }
    }
}
