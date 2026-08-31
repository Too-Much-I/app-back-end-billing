package web.tosunsaeng.billing.domain.benefit.repository;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;

@Repository
public class BenefitDefinitionRepository {

    private final MongoTemplate mongoTemplate;

    public BenefitDefinitionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<BenefitDefinition> findByCode(String benefitCode) {
        return Optional.ofNullable(mongoTemplate.findById(
                benefitCode, BenefitDefinition.class
        ));
    }

    public BenefitDefinition insert(BenefitDefinition definition) {
        return mongoTemplate.insert(definition);
    }
}
