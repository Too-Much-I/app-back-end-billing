package web.tosunsaeng.billing.domain.reservation.application;

import java.time.Instant;

public record ConfirmCommand(
        String operationId,
        String reservationId,
        String userId,
        String sessionId,
        Instant sessionCommittedAt,
        String payloadHash
) {
}
