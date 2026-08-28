package web.tosunsaeng.billing.global.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import web.tosunsaeng.billing.trialeligibility.api.TrialEligibilityEventController;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityMetrics;
import web.tosunsaeng.billing.reservation.api.ReservationController;
import web.tosunsaeng.billing.reservation.application.ReserveMetrics;

@RestControllerAdvice(assignableTypes = {
        TrialEligibilityEventController.class,
        ReservationController.class
})
public class InternalApiExceptionHandler {

    private final TrialEligibilityMetrics metrics;
    private final ReserveMetrics reserveMetrics;

    public InternalApiExceptionHandler(
            ObjectProvider<TrialEligibilityMetrics> metrics,
            ObjectProvider<ReserveMetrics> reserveMetrics
    ) {
        this.metrics = metrics.getIfAvailable();
        this.reserveMetrics = reserveMetrics.getIfAvailable();
    }

    @ExceptionHandler(InternalApiException.class)
    ResponseEntity<InternalApiError> handleInternalApiException(
            InternalApiException exception,
            HttpServletRequest request
    ) {
        if (isReservationPath(request)) {
            if ("INVALID_REQUEST".equals(exception.code())
                    || "INVALID_IDEMPOTENCY_KEY".equals(exception.code())) {
                recordReserveRejected();
            }
        } else if ("INVALID_REQUEST".equals(exception.code())) {
            recordEligibilityRejected("INVALID");
        } else if ("UNSUPPORTED_CONTRACT".equals(exception.code())) {
            recordEligibilityRejected("UNSUPPORTED");
        }
        HttpHeaders headers = new HttpHeaders();
        if (exception.retryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, exception.retryAfterSeconds().toString());
        }
        return new ResponseEntity<>(new InternalApiError(
                exception.code(),
                exception.getMessage(),
                exception.retryable(),
                UUID.randomUUID().toString()
        ), headers, exception.status());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<InternalApiError> handleUnsupportedMediaType(HttpServletRequest request) {
        if (isReservationPath(request)) {
            recordReserveRejected();
        } else {
            recordEligibilityRejected("INVALID");
        }
        return ResponseEntity.badRequest().body(new InternalApiError(
                "INVALID_REQUEST",
                "The request is invalid.",
                false,
                UUID.randomUUID().toString()
        ));
    }

    private void recordEligibilityRejected(String outcome) {
        if (metrics != null) {
            metrics.recordRejected(outcome);
        }
    }

    private void recordReserveRejected() {
        if (reserveMetrics != null) {
            reserveMetrics.record(null, "INVALID");
        }
    }

    private static boolean isReservationPath(HttpServletRequest request) {
        return request.getRequestURI().equals("/internal/v1/reservations");
    }
}
