package web.tosunsaeng.billing.domain.promotion;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader;
import web.tosunsaeng.billing.domain.reservation.paid.PaidExamQueryReader;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;

@Configuration
@ConditionalOnProperty(prefix = "billing.workload-jwt", name = "promotion-eligibility-enabled", havingValue = "true")
public class PromotionEligibilityConfiguration {
    @Bean PromotionEligibilityService promotionEligibilityService(MongoTemplate mongo, EntitlementSnapshotExecutor snapshots,
            PaidEntitlementReader paid, PaidExamQueryReader readiness, Clock clock, WorkloadJwtProperties workload,
            PaymentProperties payment, EntitlementQueryProperties jwt) {
        if (!workload.isLearningCoreReceiverEnabled() || !payment.isEntitlementQueryEnabled()
                || !payment.isMemberEvidenceEnabled() || !payment.getRecovery().isFenceRequired()
                || jwt.getIssuer() == null || jwt.getIssuer().isBlank())
            throw new IllegalStateException("PromotionEligibility eligibility requires LC receiver, MEMBER evidence and snapshot/recovery readiness");
        workload.validateEnvironment(payment.getEnvironment());
        readiness.prepare();
        return new PromotionEligibilityService(mongo, snapshots, paid, clock,
                payment.getEnvironment(), jwt.getIssuer());
    }
}
