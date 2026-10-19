package web.tosunsaeng.billing.domain.benefit.config;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.benefit.repository.BenefitDefinitionRepository;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;
import web.tosunsaeng.billing.global.infrastructure.mongodb.BillingMongoIndexInitializer;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class BenefitCatalogInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final BenefitDefinitionRepository repository;
    private final BillingMongoProperties properties;

    public BenefitCatalogInitializer(
            MongoTemplate mongoTemplate,
            BenefitDefinitionRepository repository,
            BillingMongoProperties properties
    ) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isInitializeIndexes()) {
            return;
        }
        if (properties.getSchemaVersion() != BillingMongoIndexInitializer.SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported Billing Mongo schema version.");
        }
        if (!mongoTemplate.collectionExists(BillingMongoIndexInitializer.BENEFIT_COLLECTION)) {
            throw new IllegalStateException("The BenefitDefinition collection is missing.");
        }

        BenefitDefinition current = repository.findByCode(BenefitDefinition.FREE_EXAM_ONCE)
                .orElseGet(this::insertApprovedDefinition);
        if (!current.hasApprovedFreeExamOncePolicy()) {
            throw new IllegalStateException("The FREE_EXAM_ONCE definition has policy drift.");
        }
    }

    private BenefitDefinition insertApprovedDefinition() {
        try {
            return repository.insert(BenefitDefinition.freeExamOnce(Instant.now()));
        } catch (DuplicateKeyException exception) {
            return repository.findByCode(BenefitDefinition.FREE_EXAM_ONCE)
                    .orElseThrow(() -> exception);
        }
    }
}
