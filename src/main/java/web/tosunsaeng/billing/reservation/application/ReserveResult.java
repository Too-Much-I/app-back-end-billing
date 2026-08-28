package web.tosunsaeng.billing.reservation.application;

import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;

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
