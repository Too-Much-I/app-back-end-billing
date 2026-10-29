package web.tosunsaeng.billing.domain.reward;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.reservation.paid.*;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;

@Configuration
@EnableConfigurationProperties(ReferralRewardProperties.class)
public class ReferralRewardConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.rewards.referral", name = "enabled", havingValue = "true")
    ReferralRewardService referralRewardService(PaidExamTransactions tx, PaidReservationService admissionReadiness,
            PaidExamQueryReader readerReadiness, PaymentStore store, PaymentProperties payment,
            EntitlementQueryProperties jwt, WorkloadJwtProperties workload, Clock clock, Environment env,
            ReferralRewardProperties referral) {
        if (!jwt.isPublicConnectorEnabled() || !payment.isPurchaseAccountEnabled() || !payment.isMemberEvidenceEnabled()
                || !payment.isEntitlementQueryEnabled() || !payment.getRecovery().isFenceRequired()
                || !env.getProperty("billing.reservation.paid-recovery-enabled", Boolean.class, false)
                || env.getProperty("app.rewards.invite-share.enabled", Boolean.class, false))
            throw new IllegalStateException("Referral rewards require MEMBER/recovery/admission/readers and invite-share OFF");
        workload.validateEnvironment(payment.getEnvironment());
        ReferralRewardSchema.verify(store.database());
        return new ReferralRewardService(tx, store, payment, workload.getEnvironment(), jwt.getIssuer(), clock, referral);
    }
}
