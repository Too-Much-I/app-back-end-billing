package web.tosunsaeng.billing.domain.reservation.application;

import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;

public record ReserveResult(
        IdempotencyCommand.ResponseSnapshot snapshot,
        boolean replayed
) {
    @Override
    public String toString() {
        return "ReserveResult[kind=" + snapshot.reservationKind()
                + ", replayed=" + replayed
                + ", sensitiveFields=[REDACTED]]";
    }
}
