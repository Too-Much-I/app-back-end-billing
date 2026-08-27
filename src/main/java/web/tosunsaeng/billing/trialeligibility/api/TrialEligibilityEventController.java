package web.tosunsaeng.billing.trialeligibility.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityEvent;

@RestController
@RequestMapping("/internal/v1/eligibility/trial")
public class TrialEligibilityEventController {

    private final TrialEligibilityEventDecoder decoder;
    private final TrialEligibilityEventService service;

    public TrialEligibilityEventController(
            TrialEligibilityEventDecoder decoder,
            TrialEligibilityEventService service
    ) {
        this.decoder = decoder;
        this.service = service;
    }

    @PostMapping("/events")
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        validateContentType(request);
        byte[] payload = readBoundedPayload(request);
        TrialEligibilityEvent event = decoder.decode(payload);
        service.process(event);
        return ResponseEntity.noContent().build();
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
        if (request.getContentLengthLong() > TrialEligibilityEventDecoder.MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }
        try {
            byte[] payload = request.getInputStream().readNBytes(
                    TrialEligibilityEventDecoder.MAX_PAYLOAD_BYTES + 1
            );
            if (payload.length == 0
                    || payload.length > TrialEligibilityEventDecoder.MAX_PAYLOAD_BYTES) {
                throw InternalApiException.invalidRequest();
            }
            return payload;
        } catch (IOException exception) {
            throw InternalApiException.invalidRequest();
        }
    }
}
