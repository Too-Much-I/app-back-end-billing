package web.tosunsaeng.billing.reservation.application;

import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;

public record LifecycleResult(
        IdempotencyCommand.LifecycleResponseSnapshot snapshot,
        boolean replayed
) {
}
