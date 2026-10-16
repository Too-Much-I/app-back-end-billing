package web.tosunsaeng.billing.domain.examdeletion;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.response.InternalApiError;

/** Ingress/JWT authorization must run before this controller reads any body bytes. */
@RestController
@ConditionalOnProperty(prefix = "app.billing.exam-deletion", name = "enabled", havingValue = "true")
public class DeletionController {
    public static final String ROUTE = "/internal/v1/exam-deletions/events";
    private final DeletionService service;
    private final DeletionRequestDecoder decoder = new DeletionRequestDecoder();
    public DeletionController(DeletionService service) { this.service = service; }

    @PostMapping(ROUTE)
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        validateHeaders(request);
        if (request.getContentLengthLong() > DeletionRequestDecoder.MAX_BYTES) throw tooLarge();
        try {
            byte[] bytes = request.getInputStream().readNBytes(DeletionRequestDecoder.MAX_BYTES + 1);
            service.process(decoder.decode(bytes));
            return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        } catch (IOException e) { throw InternalApiException.invalidRequest(); }
    }

    static void validateHeaders(HttpServletRequest request) {
        List<String> types = Collections.list(request.getHeaders(HttpHeaders.CONTENT_TYPE));
        if (types.size() != 1 || !types.getFirst().matches("(?i)application/json(?:[ \\t]*;[ \\t]*charset[ \\t]*=[ \\t]*(?:utf-8|\"utf-8\"))?[ \\t]*"))
            throw unsupported();
        List<String> encodings = Collections.list(request.getHeaders(HttpHeaders.CONTENT_ENCODING));
        if (encodings.size() > 1 || encodings.size() == 1 && !"identity".equalsIgnoreCase(encodings.getFirst())) throw unsupported();
    }
    private static InternalApiException unsupported() { return new InternalApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "INVALID_REQUEST", "The request is invalid.", false); }
    private static InternalApiException tooLarge() { return new InternalApiException(HttpStatus.PAYLOAD_TOO_LARGE,
            "INVALID_REQUEST", "The request is invalid.", false); }

    @ExceptionHandler(InternalApiException.class)
    ResponseEntity<InternalApiError> error(InternalApiException e) {
        HttpHeaders headers = new HttpHeaders(); headers.setCacheControl("no-store");
        if (e.status() == HttpStatus.SERVICE_UNAVAILABLE) headers.set(HttpHeaders.RETRY_AFTER, "1");
        return new ResponseEntity<>(new InternalApiError(e.code(), e.getMessage(), e.retryable(), UUID.randomUUID().toString()), headers, e.status());
    }
}
