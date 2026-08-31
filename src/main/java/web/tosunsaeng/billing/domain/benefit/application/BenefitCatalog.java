package web.tosunsaeng.billing.domain.benefit.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.benefit.repository.BenefitDefinitionRepository;

@Service
public class BenefitCatalog {

    private final BenefitDefinitionRepository repository;

    public BenefitCatalog(BenefitDefinitionRepository repository) {
        this.repository = repository;
    }

    public Optional<BenefitDefinition> findActiveFreeExamOnce() {
        return repository.findByCode(BenefitDefinition.FREE_EXAM_ONCE)
                .filter(BenefitDefinition::hasApprovedFreeExamOncePolicy);
    }
}
