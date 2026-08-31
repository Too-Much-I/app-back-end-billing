package web.tosunsaeng.billing.domain.attempt.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventMetrics;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventService;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@RestController
@RequestMapping("/internal/v1/attempt-group-events")
public class AttemptGroupEventController {

    private final AttemptGroupEventDecoder decoder;
    private final AttemptGroupEventService service;
    private final AttemptGroupEventMetrics metrics;
    private final TraceCorrelation traceCorrelation;

    public AttemptGroupEventController(
            AttemptGroupEventDecoder decoder,
            AttemptGroupEventService service,
            AttemptGroupEventMetrics metrics,
            TraceCorrelation traceCorrelation
    ) {
        this.decoder = decoder;
        this.service = service;
        this.metrics = metrics;
        this.traceCorrelation = traceCorrelation;
    }

    @PostMapping
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        observeTraceContext(request.getHeader("traceparent"));
        validateContentType(request);
        byte[] payload = readBoundedPayload(request);
        AttemptGroupStatusEvent event = decoder.decode(payload);
        service.process(event);
        return ResponseEntity.noContent().build();
    }

    private void observeTraceContext(String traceparent) {
        TraceCorrelation.TraceparentStatus status = traceCorrelation.classify(traceparent);
        if (status != TraceCorrelation.TraceparentStatus.VALID) {
            metrics.recordTraceContext(status.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static void validateContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(
                    MediaType.parseMediaType(contentType)
            )) {
                throw InternalApiException.invalidRequest();
            }
        } catch (IllegalArgumentException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    private static byte[] readBoundedPayload(HttpServletRequest request) {
        if (request.getContentLengthLong() > AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }
        try {
            byte[] payload = request.getInputStream().readNBytes(
                    AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES + 1
            );
            if (payload.length == 0
                    || payload.length > AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES) {
                throw InternalApiException.invalidRequest();
            }
            return payload;
        } catch (IOException exception) {
            throw InternalApiException.invalidRequest();
        }
    }
}
