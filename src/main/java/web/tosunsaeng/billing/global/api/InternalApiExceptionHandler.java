package web.tosunsaeng.billing.global.api;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import web.tosunsaeng.billing.trialeligibility.api.TrialEligibilityEventController;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityMetrics;

@RestControllerAdvice(assignableTypes = TrialEligibilityEventController.class)
public class InternalApiExceptionHandler {

    private final TrialEligibilityMetrics metrics;

    public InternalApiExceptionHandler(ObjectProvider<TrialEligibilityMetrics> metrics) {
        this.metrics = metrics.getIfAvailable();
    }

    @ExceptionHandler(InternalApiException.class)
    ResponseEntity<InternalApiError> handleInternalApiException(InternalApiException exception) {
        if ("INVALID_REQUEST".equals(exception.code())) {
            recordRejected("INVALID");
        } else if ("UNSUPPORTED_CONTRACT".equals(exception.code())) {
            recordRejected("UNSUPPORTED");
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
    ResponseEntity<InternalApiError> handleUnsupportedMediaType() {
        recordRejected("INVALID");
        return ResponseEntity.badRequest().body(new InternalApiError(
                "INVALID_REQUEST",
                "The request is invalid.",
                false,
                UUID.randomUUID().toString()
        ));
    }

    private void recordRejected(String outcome) {
        if (metrics != null) {
            metrics.recordRejected(outcome);
        }
    }
}
