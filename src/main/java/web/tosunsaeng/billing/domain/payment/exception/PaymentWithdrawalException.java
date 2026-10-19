package web.tosunsaeng.billing.domain.payment.exception;

import org.springframework.http.HttpStatus;
import web.tosunsaeng.billing.global.exception.InternalApiException;

public final class PaymentWithdrawalException {
    private PaymentWithdrawalException() { }
    public static InternalApiException conflict(String code) {
        return new InternalApiException(HttpStatus.CONFLICT, code, "Withdrawal evidence conflicts.", false);
    }
    public static InternalApiException unavailable() {
        return new InternalApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_TEMPORARILY_UNAVAILABLE",
                "Billing is temporarily unavailable.", true, 5);
    }
    public static InternalApiException unsupported() {
        return new InternalApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_CONTRACT",
                "The contract is unsupported.", false);
    }
}
