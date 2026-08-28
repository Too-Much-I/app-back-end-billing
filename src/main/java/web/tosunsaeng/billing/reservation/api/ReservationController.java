package web.tosunsaeng.billing.reservation.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.reservation.application.ReserveResult;
import web.tosunsaeng.billing.reservation.application.ReserveService;
import web.tosunsaeng.billing.reservation.application.CancelCommand;
import web.tosunsaeng.billing.reservation.application.ConfirmCommand;
import web.tosunsaeng.billing.reservation.application.LifecyclePayloadHasher;
import web.tosunsaeng.billing.reservation.application.LifecycleResult;
import web.tosunsaeng.billing.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.reservation.application.ReservationStatusResult;

@RestController
@RequestMapping("/internal/v1/reservations")
public class ReservationController {

    private final ReserveRequestDecoder decoder;
    private final IdempotencyKeyParser idempotencyKeyParser;
    private final ReservePayloadHasher payloadHasher;
    private final ReserveService reserveService;
    private final LifecycleRequestDecoder lifecycleDecoder;
    private final ReservationIdParser reservationIdParser;
    private final LifecyclePayloadHasher lifecyclePayloadHasher;
    private final ReservationLifecycleService lifecycleService;

    public ReservationController(
            ReserveRequestDecoder decoder,
            IdempotencyKeyParser idempotencyKeyParser,
            ReservePayloadHasher payloadHasher,
            ReserveService reserveService,
            LifecycleRequestDecoder lifecycleDecoder,
            ReservationIdParser reservationIdParser,
            LifecyclePayloadHasher lifecyclePayloadHasher,
            ReservationLifecycleService lifecycleService
    ) {
        this.decoder = decoder;
        this.idempotencyKeyParser = idempotencyKeyParser;
        this.payloadHasher = payloadHasher;
        this.reserveService = reserveService;
        this.lifecycleDecoder = lifecycleDecoder;
        this.reservationIdParser = reservationIdParser;
        this.lifecyclePayloadHasher = lifecyclePayloadHasher;
        this.lifecycleService = lifecycleService;
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

    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ConfirmResponse> confirm(
            @PathVariable String reservationId,
            HttpServletRequest request
    ) {
        validateContentType(request);
        String parsedReservationId = reservationIdParser.parse(reservationId);
        String operationId = idempotencyKeyParser.parse(request.getHeader("Idempotency-Key"));
        ConfirmRequest body = lifecycleDecoder.decodeConfirm(readBoundedPayload(request));
        ConfirmCommand command = new ConfirmCommand(
                operationId, parsedReservationId, body.userId(), body.sessionId(),
                body.sessionCommittedAt(), lifecyclePayloadHasher.hashConfirm(parsedReservationId, body)
        );
        LifecycleResult result = lifecycleService.confirm(command);
        return ResponseEntity.ok(ConfirmResponse.from(result.snapshot()));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<CancelResponse> cancel(
            @PathVariable String reservationId,
            HttpServletRequest request
    ) {
        validateContentType(request);
        String parsedReservationId = reservationIdParser.parse(reservationId);
        String operationId = idempotencyKeyParser.parse(request.getHeader("Idempotency-Key"));
        CancelRequest body = lifecycleDecoder.decodeCancel(readBoundedPayload(request));
        CancelCommand command = new CancelCommand(
                operationId, parsedReservationId, body.userId(), body.reason(),
                lifecyclePayloadHasher.hashCancel(parsedReservationId, body)
        );
        LifecycleResult result = lifecycleService.cancel(command);
        return ResponseEntity.ok(CancelResponse.from(result.snapshot()));
    }

    @PostMapping("/status")
    public ResponseEntity<ReservationStatusResponse> status(HttpServletRequest request) {
        validateContentType(request);
        ReservationStatusRequest body = lifecycleDecoder.decodeStatus(readBoundedPayload(request));
        ReservationStatusResult result = lifecycleService.status(body.userId(), body.operationId());
        return ResponseEntity.ok(ReservationStatusResponse.from(result));
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
        if (request.getContentLengthLong() > LifecycleRequestDecoder.MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }
        try {
            byte[] payload = request.getInputStream().readNBytes(
                    LifecycleRequestDecoder.MAX_PAYLOAD_BYTES + 1
            );
            if (payload.length == 0 || payload.length > LifecycleRequestDecoder.MAX_PAYLOAD_BYTES) {
                throw InternalApiException.invalidRequest();
            }
            return payload;
        } catch (IOException exception) {
            throw InternalApiException.invalidRequest();
        }
    }
}
