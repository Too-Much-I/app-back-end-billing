package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.function.Function;
import com.mongodb.client.ClientSession;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.reservation.repository.*;
import web.tosunsaeng.billing.domain.entitlement.repository.*;
import web.tosunsaeng.billing.domain.attempt.repository.*;

/** Explicitly constructed/flagged foundation. No admission route or automatic schema installation. */
public final class PaidExamTransactions {
    private final PaymentStore payment;
    private final MongoTemplate template;
    public PaidExamTransactions(PaymentStore payment, MongoTemplate template) {
        this.payment = payment; this.template = template;
    }
    public <T> T execute(Function<Context, T> work) {
        return payment.transactionWithTemplate(template, (session, operations) -> {
            Context context = new Context(session, operations);
            try { return work.apply(context); }
            finally { context.closed = true; }
        });
    }
    public static final class Context {
        private final ClientSession session;
        private final MongoOperations operations;
        private boolean closed;
        private Context(ClientSession session, MongoOperations operations) { this.session = session; this.operations = operations; }
        private void check() { if (closed || !session.hasActiveTransaction()) throw new IllegalStateException("Transaction scope closed"); }
        public ClientSession session() { check(); return session; }
        public MongoOperations operations() { check(); return operations; }
        public ReservationRepository reservations() { return new ReservationRepository(operations()); }
        public ReservationAllocationRepository allocations() { return new ReservationAllocationRepository(operations()); }
        public EntitlementGrantRepository grants() { return new EntitlementGrantRepository(operations()); }
        public EntitlementLedgerRepository ledger() { return new EntitlementLedgerRepository(operations()); }
        public AttemptGroupRepository groups() { return new AttemptGroupRepository(operations()); }
        public AttemptSessionRepository sessions() { return new AttemptSessionRepository(operations()); }
        public PaidExamStore paid() { return new PaidExamStore(operations()); }
    }
}
