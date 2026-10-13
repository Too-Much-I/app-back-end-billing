package web.tosunsaeng.billing.domain.payment.recovery;

import java.time.Instant;

/** Transport classification only; never retain raw response, header, URL or cause. */
public final class RecoveryRemoteException extends RuntimeException {
    public enum Kind { TRANSIENT, AUTH, CONTRACT, APPLICATION }
    private final Kind kind;
    private final RecoveryPayloadDecoder.RemoteError error;
    private final Instant retryAt;
    private RecoveryRemoteException(Kind kind, RecoveryPayloadDecoder.RemoteError error, Instant retryAt) {
        super("Recovery remote request failed");
        this.kind = kind; this.error = error; this.retryAt = retryAt;
    }
    public static RecoveryRemoteException transientFailure(Instant retryAt) {
        return new RecoveryRemoteException(Kind.TRANSIENT, null, retryAt);
    }
    public static RecoveryRemoteException auth() { return new RecoveryRemoteException(Kind.AUTH, null, null); }
    public static RecoveryRemoteException contract() { return new RecoveryRemoteException(Kind.CONTRACT, null, null); }
    /** Application error must first pass RecoveryPayloadDecoder.error with dispatch binding. */
    public static RecoveryRemoteException application(RecoveryPayloadDecoder.RemoteError error, Instant retryAt) {
        if (error == null || error.code() == null) throw new IllegalArgumentException("Missing recovery error");
        return new RecoveryRemoteException(Kind.APPLICATION, error, retryAt);
    }
    public Kind kind() { return kind; }
    public RecoveryPayloadDecoder.RemoteError error() { return error; }
    public Instant retryAt() { return retryAt; }
}
