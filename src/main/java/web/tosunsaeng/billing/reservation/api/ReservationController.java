package web.tosunsaeng.billing.reservation.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.reservation.application.ReserveResult;
import web.tosunsaeng.billing.reservation.application.ReserveService;

@RestController
@RequestMapping("/internal/v1/reservations")
public class ReservationController {

    private final ReserveRequestDecoder decoder;
    private final IdempotencyKeyParser idempotencyKeyParser;
    private final ReservePayloadHasher payloadHasher;
    private final ReserveService reserveService;

    public ReservationController(
            ReserveRequestDecoder decoder,
            IdempotencyKeyParser idempotencyKeyParser,
            ReservePayloadHasher payloadHasher,
            ReserveService reserveService
    ) {
        this.decoder = decoder;
        this.idempotencyKeyParser = idempotencyKeyParser;
        this.payloadHasher = payloadHasher;
        this.reserveService = reserveService;
    }

    @PostMapping
    public ResponseEntity<ReserveResponse> reserve(HttpServletRequest request) {
        validateContentType(request);
        String operationId = idempotencyKeyParser.parse(
                request.getHeader("Idempotency-Key")
        );
        ReserveRequest reserveRequest = decoder.decode(readBoundedPayload(request));
        ReserveCommand command = new ReserveCommand(
                operationId,
                reserveRequest.userId(),
                reserveRequest.sessionId(),
                reserveRequest.mockExamId(),
                payloadHasher.hash(reserveRequest)
        );
        ReserveResult result = reserveService.reserve(command);
        return ResponseEntity.ok(ReserveResponse.from(result.snapshot()));
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
        if (request.getContentLengthLong() > ReserveRequestDecoder.MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }
        try {
            byte[] payload = request.getInputStream().readNBytes(
                    ReserveRequestDecoder.MAX_PAYLOAD_BYTES + 1
            );
            if (payload.length == 0 || payload.length > ReserveRequestDecoder.MAX_PAYLOAD_BYTES) {
                throw InternalApiException.invalidRequest();
            }
            return payload;
        } catch (IOException exception) {
            throw InternalApiException.invalidRequest();
        }
    }
}
