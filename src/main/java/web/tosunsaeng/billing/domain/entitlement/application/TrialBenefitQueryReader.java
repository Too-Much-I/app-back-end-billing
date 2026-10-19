package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Instant;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementQueryRepository;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Benefit;

@Component
public class TrialBenefitQueryReader implements FreeBenefitQueryReader {
    private final EntitlementQueryRepository repository;
    private final TrialEntitlementQueryEvaluator evaluator;
    public TrialBenefitQueryReader(EntitlementQueryRepository repository, TrialEntitlementQueryEvaluator evaluator) {
        this.repository = repository; this.evaluator = evaluator;
    }
    public Benefit read(String userId, Instant asOf) { return evaluator.evaluate(repository.read(userId, asOf)); }
}
