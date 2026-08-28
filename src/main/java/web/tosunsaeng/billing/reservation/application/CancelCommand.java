package web.tosunsaeng.billing.reservation.application;

import web.tosunsaeng.billing.reservation.api.CancelRequest;

public record CancelCommand(
        String operationId,
        String reservationId,
        String userId,
        CancelRequest.Reason reason,
        String payloadHash
) {
}
