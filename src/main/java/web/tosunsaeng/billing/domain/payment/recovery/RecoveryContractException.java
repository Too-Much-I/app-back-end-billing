package web.tosunsaeng.billing.domain.payment.recovery;

/** Never attach remote bodies, cursors, identifiers or parser causes to this exception. */
public final class RecoveryContractException extends RuntimeException {
    public RecoveryContractException() { super("Invalid withdrawal recovery contract"); }
}
