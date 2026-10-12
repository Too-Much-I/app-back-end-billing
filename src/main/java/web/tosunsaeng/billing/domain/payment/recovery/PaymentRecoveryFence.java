package web.tosunsaeng.billing.domain.payment.recovery;

import com.mongodb.client.ClientSession;
import org.bson.Document;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/**
 * A write fence, not just a read check. Never creates a missing control.
 * Capture once before a logical request/dispatch; transaction retries reuse that epoch.
 */
public final class PaymentRecoveryFence {
    public static final String COLLECTION = "payment_recovery_controls";
    public enum Writer { ACCOUNT, PUSH, RECOVERY }
    public enum Barrier { OPEN, PURCHASE_BLOCKED, ALL_BLOCKED }
    public record Ticket(String epoch) {
        public Ticket { canonical(epoch); }
        @Override public String toString() { return "RecoveryTicket[REDACTED]"; }
    }
    private final PaymentStore store;
    private final PaymentProperties properties;
    public PaymentRecoveryFence(PaymentStore store, PaymentProperties properties) {
        this.store = store; this.properties = properties;
    }
    public Ticket capture() {
        if (!properties.getRecovery().isFenceRequired()) return null;
        try {
            Document control = store.database().getCollection(COLLECTION).find(key()).first();
            validate(control);
            return new Ticket(control.getString("epoch"));
        } catch (RuntimeException unavailable) {
            throw web.tosunsaeng.billing.domain.payment.exception.PaymentException.unavailable();
        }
    }
    public void acquire(ClientSession session, Ticket ticket, Writer writer) {
        require(session != null && session.hasActiveTransaction());
        if (!properties.getRecovery().isFenceRequired()) {
            // Recovery can never run without its mandatory fence.
            require(ticket == null && writer != Writer.RECOVERY);
            return;
        }
        require(ticket != null && writer != null);
        var controls = store.database().getCollection(COLLECTION);
        Document control = controls.find(session, key()).first();
        validate(control);
        require(control.getString("epoch").equals(ticket.epoch()));
        Barrier barrier = Barrier.valueOf(control.getString("barrier"));
        require(barrier != Barrier.ALL_BLOCKED);
        require(writer != Writer.ACCOUNT || barrier == Barrier.OPEN);
        long version = control.getLong("version");
        require(version < Long.MAX_VALUE);
        Document match = new Document(key()).append("_id", control.get("_id"))
                .append("schemaVersion", 1L).append("epoch", ticket.epoch())
                .append("version", version).append("barrier", barrier.name());
        require(controls.updateOne(session, match,
                new Document("$inc", new Document("version", 1L))).getModifiedCount() == 1);
    }
    private Document key() {
        require(Set.of("SANDBOX", "PRODUCTION").contains(properties.getEnvironment()));
        return new Document("environment", properties.getEnvironment()).append("consumer", "billing");
    }
    private static void validate(Document control) {
        require(control != null && control.get("schemaVersion") instanceof Long
                && control.getLong("schemaVersion") == 1L && control.get("version") instanceof Long
                && control.getLong("version") > 0);
        require(control.get("epoch") instanceof String && control.get("barrier") instanceof String);
        canonical(control.getString("epoch"));
        require(Set.of("OPEN", "PURCHASE_BLOCKED", "ALL_BLOCKED").contains(control.getString("barrier")));
    }
    private static void canonical(String value) {
        require(value != null);
        try {
            UUID id = UUID.fromString(value);
            require(id.toString().equals(value) && id.version() == 4 && id.variant() == 2);
        } catch (IllegalArgumentException invalid) {
            throw web.tosunsaeng.billing.domain.payment.exception.PaymentException.unavailable();
        }
    }
}
