package web.tosunsaeng.billing.domain.ownerrebind.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindMetrics;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindService;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindTracing;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@RestController
public class OwnerRebindEventController {

    public static final String PHONE_PATH = "/internal/v1/eligibility/trial/owner/events";
    public static final String MERGE_PATH = "/internal/v1/owners/merge/events";

    private final PhoneOwnerRebindEventDecoder phoneDecoder;
    private final UserMergedEventDecoder mergeDecoder;
    private final OwnerRebindService service;
    private final OwnerRebindTracing tracing;
    private final OwnerRebindMetrics metrics;
    private final TraceCorrelation traceCorrelation;

    public OwnerRebindEventController(
            PhoneOwnerRebindEventDecoder phoneDecoder,
            UserMergedEventDecoder mergeDecoder,
            OwnerRebindService service,
            OwnerRebindTracing tracing,
            OwnerRebindMetrics metrics,
            TraceCorrelation traceCorrelation
    ) {
        this.phoneDecoder = phoneDecoder;
        this.mergeDecoder = mergeDecoder;
        this.service = service;
        this.tracing = tracing;
        this.metrics = metrics;
        this.traceCorrelation = traceCorrelation;
    }

    @PostMapping(PHONE_PATH)
    public ResponseEntity<Void> phoneRejoin(HttpServletRequest request) {
        return receive(request, PhoneOwnerRebindEventDecoder.MAX_PAYLOAD_BYTES, phoneDecoder::decode);
    }

    @PostMapping(MERGE_PATH)
    public ResponseEntity<Void> userMerged(HttpServletRequest request) {
        return receive(request, UserMergedEventDecoder.MAX_PAYLOAD_BYTES, mergeDecoder::decode);
    }

    private ResponseEntity<Void> receive(
            HttpServletRequest request,
            int maxBytes,
            Decoder decoder
    ) {
        observeTraceContext(request.getHeader("traceparent"));
        validateContentType(request);
        byte[] payload = readBoundedPayload(request, maxBytes);
        return tracing.inConsumeSpan(() -> {
            OwnerRebindCommand command = decoder.decode(payload);
            service.process(command);
            return ResponseEntity.noContent().build();
        });
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

    private static byte[] readBoundedPayload(HttpServletRequest request, int maxBytes) {
        if (request.getContentLengthLong() > maxBytes) {
            throw InternalApiException.invalidRequest();
        }
        try {
            byte[] payload = request.getInputStream().readNBytes(maxBytes + 1);
            if (payload.length == 0 || payload.length > maxBytes) {
                throw InternalApiException.invalidRequest();
            }
            return payload;
        } catch (IOException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    @FunctionalInterface
    private interface Decoder {
        OwnerRebindCommand decode(byte[] payload);
    }
}
