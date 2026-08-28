package web.tosunsaeng.billing.domain.reservation.application;

import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;

public record LifecycleResult(
        IdempotencyCommand.LifecycleResponseSnapshot snapshot,
        boolean replayed
) {
}
