package web.tosunsaeng.billing.global.infrastructure.mongodb;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;

@Component
public class EntitlementSnapshotExecutor {
    private final MongoDatabaseFactory factory;
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    public EntitlementSnapshotExecutor(MongoDatabaseFactory factory) {
        this.factory = factory;
    }
    public <T> T execute(Supplier<T> work) {
        DEADLINE.set(System.nanoTime() + Duration.ofSeconds(2).toNanos());
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    remaining();
                    // Driver CSOT bounds server selection, socket reads and commit, not just server query time.
                    var options = TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
                            .readPreference(ReadPreference.primary()).timeout(remaining().toMillis(), TimeUnit.MILLISECONDS).build();
                    var transaction = new TransactionTemplate(new MongoTransactionManager(factory, options));
                    transaction.setReadOnly(true);
                    transaction.setTimeout(2);
                    T result = transaction.execute(status -> work.get());
                    remaining();
                    return result;
                } catch (EntitlementQueryException e) { throw e; }
                catch (RuntimeException e) {
                    if (attempt == 1) { throw new EntitlementQueryException(EntitlementQueryException.Failure.STORAGE); }
                }
            }
            throw new EntitlementQueryException(EntitlementQueryException.Failure.STORAGE);
        } finally { DEADLINE.remove(); }
    }
    public static Duration remaining() {
        Long deadline = DEADLINE.get();
        long nanos = deadline == null ? Duration.ofSeconds(2).toNanos() : deadline - System.nanoTime();
        if (nanos <= 0) { throw new EntitlementQueryException(EntitlementQueryException.Failure.STORAGE); }
        return Duration.ofMillis(Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos)));
    }
}
