package web.tosunsaeng.billing.domain.payment.sync;

import java.util.UUID;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Store;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;

/** Untrusted hint. Never convert this directly into a verified PurchaseObservation. */
public record PaymentSyncRequest(String operationId, Store store, String transactionId) {
    public PaymentSyncRequest {
        try {
            UUID id = UUID.fromString(operationId);
            if (!id.toString().equals(operationId) || id.version() != 4 || id.variant() != 2 || store == null
                    || transactionId == null || transactionId.isBlank() || transactionId.length() > 255
                    || transactionId.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new PaymentException(400, "INVALID_REQUEST"); }
    }
    @Override public String toString() { return "PaymentSyncRequest[REDACTED]"; }
}
