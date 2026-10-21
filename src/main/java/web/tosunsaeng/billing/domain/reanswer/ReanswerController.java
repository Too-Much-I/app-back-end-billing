package web.tosunsaeng.billing.domain.reanswer;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.response.InternalApiError;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import org.springframework.dao.DataAccessException;

@RestController
@ConditionalOnProperty(prefix = "app.billing.reanswer-eligibility", name = "enabled", havingValue = "true")
public class ReanswerController {
    public static final String ROUTE = "/internal/v1/entitlements/reanswer-eligibility";
    private final ReanswerService service;
    private final ReanswerRequestDecoder decoder = new ReanswerRequestDecoder();
    public ReanswerController(ReanswerService service) { this.service = service; }

    @PostMapping(ROUTE)
    public ResponseEntity<ReanswerResponse> query(HttpServletRequest request) {
        var types = Collections.list(request.getHeaders(HttpHeaders.CONTENT_TYPE));
        var encodings = Collections.list(request.getHeaders(HttpHeaders.CONTENT_ENCODING));
        if (types.size() != 1 || !types.getFirst().matches("(?i)application/json(?:[ \t]*;[ \t]*charset[ \t]*=[ \t]*(?:utf-8|\"utf-8\"))?[ \t]*")
                || encodings.size() > 1 || encodings.size() == 1 && !"identity".equalsIgnoreCase(encodings.getFirst())
                || request.getContentLengthLong() > ReanswerRequestDecoder.MAX_BYTES) throw InternalApiException.invalidRequest();
        try {
            String user = decoder.decode(request.getInputStream().readNBytes(ReanswerRequestDecoder.MAX_BYTES + 1));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.query(user));
        } catch (IOException e) { throw InternalApiException.invalidRequest(); }
    }
    @ExceptionHandler(InternalApiException.class)
    ResponseEntity<InternalApiError> invalid(InternalApiException e) {
        return ResponseEntity.status(e.status()).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InternalApiError(e.code(), e.getMessage(), e.retryable(), UUID.randomUUID().toString()));
    }
    @ExceptionHandler({EntitlementQueryException.class, DataAccessException.class})
    ResponseEntity<InternalApiError> unavailable(RuntimeException e) {
        return ResponseEntity.status(503).header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.RETRY_AFTER, "1")
                .body(new InternalApiError("BILLING_TEMPORARILY_UNAVAILABLE", "Billing is temporarily unavailable.", true, UUID.randomUUID().toString()));
    }
}
