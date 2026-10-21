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
@EnableConfigurationProperties(InviteRewardProperties.class)
public class InviteRewardConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.rewards.invite-share", name = "enabled", havingValue = "true")
    InviteRewardService inviteRewardService(PaidExamTransactions tx, PaidReservationService admissionReadiness,
            PaidExamQueryReader readerReadiness, PaymentStore store, PaymentProperties payment,
            EntitlementQueryProperties jwt, WorkloadJwtProperties workload, Clock clock, Environment env) {
        if (!jwt.isPublicConnectorEnabled() || !payment.isPurchaseAccountEnabled() || !payment.isMemberEvidenceEnabled()
                || !payment.isEntitlementQueryEnabled() || !payment.getRecovery().isFenceRequired()
                || !env.getProperty("billing.reservation.paid-recovery-enabled", Boolean.class, false))
            throw new IllegalStateException("Invite rewards require MEMBER account, recovery and integrated paid admission/readers");
        workload.validateEnvironment(payment.getEnvironment());
        InviteRewardSchema.verify(store.database());
        return new InviteRewardService(tx, store, payment, workload.getEnvironment(), jwt.getIssuer(), clock);
    }
}
