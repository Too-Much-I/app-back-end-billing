package web.tosunsaeng.billing.global.mongodb;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MongoTransactionExecutor {

    private final TransactionTemplate transactionTemplate;

    public MongoTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> operation) {
        return transactionTemplate.execute(status -> operation.get());
    }
}
