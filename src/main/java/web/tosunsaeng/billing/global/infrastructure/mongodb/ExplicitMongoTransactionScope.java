package web.tosunsaeng.billing.global.infrastructure.mongodb;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Prevents silently committing a second transaction inside an explicit payment transaction. */
public final class ExplicitMongoTransactionScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private ExplicitMongoTransactionScope() { ACTIVE.set(true); }

    public static ExplicitMongoTransactionScope open() {
        requireNoExplicitTransaction();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Cannot start an explicit transaction inside a Spring transaction");
        }
        return new ExplicitMongoTransactionScope();
    }
    public static void requireNoExplicitTransaction() {
        if (Boolean.TRUE.equals(ACTIVE.get())) {
            throw new IllegalStateException("Nested transaction boundaries are not allowed");
        }
    }
    @Override public void close() { ACTIVE.remove(); }
}
