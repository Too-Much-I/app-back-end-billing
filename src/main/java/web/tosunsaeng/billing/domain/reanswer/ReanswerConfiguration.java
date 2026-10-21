package web.tosunsaeng.billing.domain.reanswer;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.billing.domain.entitlement.application.TrialEntitlementQueryEvaluator;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementQueryRepository;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader;
import web.tosunsaeng.billing.domain.reservation.paid.PaidExamQueryReader;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;

@Configuration
@ConditionalOnProperty(prefix = "app.billing.reanswer-eligibility", name = "enabled", havingValue = "true")
public class ReanswerConfiguration {
    @Bean ReanswerService reanswerService(MongoTemplate mongo, EntitlementSnapshotExecutor snapshots,
            PaidEntitlementReader paid, PaidExamQueryReader readiness, EntitlementQueryRepository trial,
            TrialEntitlementQueryEvaluator evaluator, Clock clock, WorkloadJwtProperties workload,
            PaymentProperties payment, EntitlementQueryProperties jwt) {
        if (!workload.isLearningCoreReceiverEnabled() || !payment.isEntitlementQueryEnabled()
                || !payment.isMemberEvidenceEnabled() || !payment.getRecovery().isFenceRequired()
                || jwt.getIssuer() == null || jwt.getIssuer().isBlank())
            throw new IllegalStateException("Reanswer eligibility requires LC receiver, MEMBER evidence and snapshot/recovery readiness");
        workload.validateEnvironment(payment.getEnvironment());
        readiness.prepare();
        return new ReanswerService(mongo, snapshots, paid, trial, evaluator, clock,
                workload.getEnvironment(), payment.getEnvironment(), jwt.getIssuer());
    }
}
