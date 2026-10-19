package web.tosunsaeng.billing.domain.payment.worker;

import java.time.*;

/** Local cadence only; correctness across replicas belongs to the transaction/CAS in the task. */
final class PaymentHourlyTask implements Runnable {
    private final Clock clock;
    private final Runnable task;
    private Instant next;
    PaymentHourlyTask(Clock clock, Runnable task) { this.clock = clock; this.task = task; }
    @Override public synchronized void run() {
        Instant now = clock.instant();
        if (next != null && now.isBefore(next)) return;
        next = now.plus(Duration.ofHours(1));
        task.run();
    }
}
