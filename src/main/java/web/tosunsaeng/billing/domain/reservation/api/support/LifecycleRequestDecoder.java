package web.tosunsaeng.billing.domain.reservation.api.support;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReservationStatusRequest;

@Component
public class LifecycleRequestDecoder {

    public static final int MAX_PAYLOAD_BYTES = LifecycleJsonDecoder.MAX_PAYLOAD_BYTES;
    private static final Set<String> CONFIRM_FIELDS = Set.of(
            "userId", "sessionId", "sessionCommittedAt"
    );
    private static final Set<String> CANCEL_FIELDS = Set.of("userId", "reason");
    private static final Set<String> STATUS_FIELDS = Set.of("userId", "operationId");
    private final LifecycleJsonDecoder decoder = new LifecycleJsonDecoder();

    public ConfirmRequest decodeConfirm(byte[] payload) {
        JsonNode root = decoder.object(payload, CONFIRM_FIELDS);
        return new ConfirmRequest(
                decoder.uuid(root, "userId"),
                decoder.opaque(root, "sessionId"),
                decoder.utcMillis(root, "sessionCommittedAt")
        );
    }

    public CancelRequest decodeCancel(byte[] payload) {
        JsonNode root = decoder.object(payload, CANCEL_FIELDS);
        try {
            return new CancelRequest(
                    decoder.uuid(root, "userId"),
                    CancelRequest.Reason.valueOf(decoder.text(root, "reason"))
            );
        } catch (IllegalArgumentException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    public ReservationStatusRequest decodeStatus(byte[] payload) {
        JsonNode root = decoder.object(payload, STATUS_FIELDS);
        return new ReservationStatusRequest(
                decoder.uuid(root, "userId"), decoder.uuid(root, "operationId")
        );
    }
}
