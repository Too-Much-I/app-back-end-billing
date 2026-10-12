package web.tosunsaeng.billing.domain.payment.repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;

/** Explicit sessions keep all payment writes in the same snapshot/majority+j boundary. */
@Repository
public class PaymentStore {
    private final MongoClient client;
    private final MongoDatabase db;
    public PaymentStore(MongoClient client, MongoDatabaseFactory factory) {
        this.client = client;
        this.db = factory.getMongoDatabase().withReadPreference(ReadPreference.primary())
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).withTimeout(2, TimeUnit.SECONDS);
    }
    public MongoDatabase database() { return db; }
    public <T> T transaction(Function<ClientSession, T> work) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        for (int attempt = 0; attempt < 32; attempt++) {
            if (System.nanoTime() >= deadline) throw PaymentException.unavailable();
            try (ClientSession session = client.startSession()) {
                session.startTransaction(TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
                        .readPreference(ReadPreference.primary()).writeConcern(WriteConcern.MAJORITY.withJournal(true))
                        .timeout(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())), TimeUnit.MILLISECONDS).build());
                try {
                    T result = work.apply(session);
                    // Unknown commit retries only commit on the SAME session/transaction.
                    for (int commit = 0; ; commit++) {
                        try { session.commitTransaction(); return result; }
                        catch (MongoException error) {
                            if (!error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) throw error;
                            if (commit >= 2 || System.nanoTime() >= deadline) throw PaymentException.unavailable();
                        }
                    }
                } catch (RuntimeException error) {
                    if (session.hasActiveTransaction()) {
                        try { session.abortTransaction(); } catch (RuntimeException ignored) { /* No sensitive logging. */ }
                    }
                    throw error;
                }
            } catch (MongoException error) {
                boolean retry = error.getCode() == 11000
                        || error.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                if (!retry || attempt == 31 || System.nanoTime() >= deadline) throw PaymentException.unavailable();
            }
        }
        throw PaymentException.unavailable();
    }
    public List<Document> products(ClientSession session, String environment) {
        return db.getCollection("payment_products").find(session,
                new Document("environment", environment).append("saleEnabled", true)).limit(11).into(new ArrayList<>());
    }
}
