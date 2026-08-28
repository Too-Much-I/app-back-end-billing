package web.tosunsaeng.billing.domain.reservation.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.reservation.application.ReserveResult;
import web.tosunsaeng.billing.domain.reservation.application.ReserveService;
import web.tosunsaeng.billing.domain.reservation.application.LifecycleResult;
import web.tosunsaeng.billing.domain.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.domain.reservation.application.ReservationStatusResult;
import web.tosunsaeng.billing.domain.reservation.api.support.IdempotencyKeyParser;
import web.tosunsaeng.billing.domain.reservation.api.support.LifecycleRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.api.support.ReservationIdParser;
import web.tosunsaeng.billing.domain.reservation.api.support.ReserveRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReservationStatusRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.domain.reservation.dto.response.CancelResponse;
import web.tosunsaeng.billing.domain.reservation.dto.response.ConfirmResponse;
import web.tosunsaeng.billing.domain.reservation.dto.response.ReservationStatusResponse;
import web.tosunsaeng.billing.domain.reservation.dto.response.ReserveResponse;
import web.tosunsaeng.billing.domain.reservation.converter.ReservationConverter;

@RestController
@RequestMapping("/internal/v1/reservations")
public class ReservationController {

    private final ReserveRequestDecoder decoder;
    private final IdempotencyKeyParser idempotencyKeyParser;
    private final ReserveService reserveService;
    private final LifecycleRequestDecoder lifecycleDecoder;
    private final ReservationIdParser reservationIdParser;
    private final ReservationLifecycleService lifecycleService;
    private final ReservationConverter converter;

    public ReservationController(
            ReserveRequestDecoder decoder,
            IdempotencyKeyParser idempotencyKeyParser,
            ReserveService reserveService,
            LifecycleRequestDecoder lifecycleDecoder,
            ReservationIdParser reservationIdParser,
            ReservationLifecycleService lifecycleService,
            ReservationConverter converter
    ) {
        this.decoder = decoder;
        this.idempotencyKeyParser = idempotencyKeyParser;
        this.reserveService = reserveService;
        this.lifecycleDecoder = lifecycleDecoder;
        this.reservationIdParser = reservationIdParser;
        this.lifecycleService = lifecycleService;
        this.converter = converter;
    }

    @PostMapping
    public ResponseEntity<ReserveResponse> reserve(HttpServletRequest request) {
        validateContentType(request);
        String operationId = idempotencyKeyParser.parse(
                request.getHeader("Idempotency-Key")
        );
        ReserveRequest reserveRequest = decoder.decode(readBoundedPayload(request));
        ReserveResult result = reserveService.reserve(
                converter.toReserveCommand(operationId, reserveRequest)
        );
        return ResponseEntity.ok(converter.toReserveResponse(result.snapshot()));
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
        LifecycleResult result = lifecycleService.confirm(
                converter.toConfirmCommand(operationId, parsedReservationId, body)
        );
        return ResponseEntity.ok(converter.toConfirmResponse(result.snapshot()));
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
        LifecycleResult result = lifecycleService.cancel(
                converter.toCancelCommand(operationId, parsedReservationId, body)
        );
        return ResponseEntity.ok(converter.toCancelResponse(result.snapshot()));
    }

    @PostMapping("/status")
    public ResponseEntity<ReservationStatusResponse> status(HttpServletRequest request) {
        validateContentType(request);
        ReservationStatusRequest body = lifecycleDecoder.decodeStatus(readBoundedPayload(request));
        ReservationStatusResult result = lifecycleService.status(body.userId(), body.operationId());
        return ResponseEntity.ok(converter.toStatusResponse(result));
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
