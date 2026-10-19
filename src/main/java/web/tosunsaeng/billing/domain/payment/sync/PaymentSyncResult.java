package web.tosunsaeng.billing.domain.payment.sync;

import com.fasterxml.jackson.annotation.JsonInclude;

public record PaymentSyncResult(String operationId, Status status, String purchaseId, String purchaseStatus,
                               @JsonInclude(JsonInclude.Include.NON_NULL) String processingHint) {
    public enum Status { PENDING, APPLIED }
    public static PaymentSyncResult pending(String operation, boolean review) {
        return new PaymentSyncResult(operation, Status.PENDING, null, null, review ? "CONTACT_SUPPORT" : null);
    }
    @Override public String toString() { return "PaymentSyncResult[REDACTED]"; }
}
