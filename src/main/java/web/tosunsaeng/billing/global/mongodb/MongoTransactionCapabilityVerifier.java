package web.tosunsaeng.billing.global.mongodb;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.config.BillingMongoProperties;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoTransactionCapabilityVerifier implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final BillingMongoProperties properties;

    public MongoTransactionCapabilityVerifier(
            MongoTemplate mongoTemplate,
            BillingMongoProperties properties
    ) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRequireTransactions()) {
            return;
        }
        Document hello = mongoTemplate.executeCommand(new Document("hello", 1));
        if (!(hello.get("setName") instanceof String setName) || setName.isBlank()
                || !(hello.get("logicalSessionTimeoutMinutes") instanceof Number)) {
            throw new IllegalStateException(
                    "MongoDB replica-set transaction capability is required."
            );
        }
    }
}
