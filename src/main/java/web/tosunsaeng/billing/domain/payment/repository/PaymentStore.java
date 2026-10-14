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
    private final MongoDatabaseFactory factory;
    public PaymentStore(MongoClient client, MongoDatabaseFactory factory) {
        this.client = client;
        this.factory = factory;
        this.db = factory.getMongoDatabase().withReadPreference(ReadPreference.primary())
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).withTimeout(2, TimeUnit.SECONDS);
    }
    public MongoDatabase database() { return db; }
    /** The bound template and raw driver receive the identical transaction session. */
    public <T> T transactionWithTemplate(org.springframework.data.mongodb.core.MongoTemplate template,
            java.util.function.BiFunction<ClientSession, org.springframework.data.mongodb.core.MongoOperations, T> work) {
        if (template.getMongoDatabaseFactory() != factory) {
            throw new IllegalArgumentException("Transaction participants must share the same database factory");
        }
        return transaction(session -> work.apply(session, template.withSession(session)));
    }
    public <T> T transaction(Function<ClientSession, T> work) {
        try (var scope = web.tosunsaeng.billing.global.infrastructure.mongodb.ExplicitMongoTransactionScope.open()) {
            return runTransaction(work);
        }
    }
    private <T> T runTransaction(Function<ClientSession, T> work) {
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
            } catch (RuntimeException error) {
                MongoException mongo = mongoCause(error);
                if (mongo == null) throw error;
                boolean retry = mongo.getCode() == 11000
                        || mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                if (!retry || attempt == 31 || System.nanoTime() >= deadline) throw PaymentException.unavailable();
            }
        }
        throw PaymentException.unavailable();
    }
    // Session-bound Spring repositories translate driver errors; retain their transaction labels.
    private static MongoException mongoCause(RuntimeException error) {
        if (error instanceof MongoException mongo) return mongo;
        if (!(error instanceof org.springframework.dao.DataAccessException)) return null;
        Throwable cause = error.getCause();
        for (int i = 0; cause != null && i < 16; i++, cause = cause.getCause()) {
            if (cause instanceof MongoException mongo) return mongo;
        }
        return null;
    }
    public List<Document> products(ClientSession session, String environment) {
        return db.getCollection("payment_products").find(session,
                new Document("environment", environment).append("saleEnabled", true)).limit(11).into(new ArrayList<>());
    }
}
