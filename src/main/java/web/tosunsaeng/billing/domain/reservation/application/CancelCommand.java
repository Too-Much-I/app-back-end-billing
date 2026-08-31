package web.tosunsaeng.billing.domain.reservation.application;

import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;

public record CancelCommand(
        String operationId,
        String reservationId,
        String userId,
        CancelRequest.Reason reason,
        String payloadHash
) {
}
