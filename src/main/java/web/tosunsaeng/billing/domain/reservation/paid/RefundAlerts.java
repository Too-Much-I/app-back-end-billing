package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.mongodb.client.model.CountOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.worker.PaymentProviderAlerts;
import static web.tosunsaeng.billing.domain.payment.worker.PaymentProviderAlerts.Kind.*;

/** Aggregate-only notifications. A Slack failure never changes financial or delivery state. */
public final class RefundAlerts {
    private final PaymentStore store;
    private final String environment, paymentEnvironment;
    private final Clock clock;
    private final PaymentProviderAlerts.Sink sink;
    private final MeterRegistry metrics;
    private Instant nextIncident = Instant.MIN, nextDaily = Instant.MIN;
    private LocalDate daily;
    private boolean incident;
    private boolean observedAuth, observedBlocked;
    public RefundAlerts(PaymentStore store, String environment, Clock clock, PaymentProviderAlerts.Sink sink, MeterRegistry metrics) {
        if (!Set.of("test", "staging", "production").contains(environment) || sink == null) throw new IllegalArgumentException("Invalid refund alerts");
        this.store = store; this.environment = environment; this.clock = clock; this.sink = sink; this.metrics = metrics;
        paymentEnvironment = environment.equals("production") ? "PRODUCTION" : "SANDBOX";
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public synchronized void poll() {
        try {
            Instant now = clock.instant();
            Document fanout = new Document("environment", paymentEnvironment).append("accessState", "REVOKED")
                    .append("$or", List.of(new Document("refundWork.phase", new Document("$ne", "DONE")), new Document("refundWork.state", "REVIEW_REQUIRED")));
            Document deliveries = new Document("environment", environment).append("work.state", new Document("$ne", "DELIVERED"));
            // Groups lack an environment field: join through the authoritative purchase environment.
            List<Document> pipeline = List.of(new Document("$match", new Document("refundReview.state", "OPEN")),
                    new Document("$lookup", new Document("from", "purchases").append("localField", "sourceSnapshot.purchaseId").append("foreignField", "_id").append("as", "purchase")),
                    new Document("$match", new Document("purchase.environment", paymentEnvironment)),
                    new Document("$group", new Document("_id", null).append("count", new Document("$sum", 1))
                            .append("oldest", new Document("$min", "$refundReview.createdAt"))
                            .append("overdue", new Document("$sum", new Document("$cond", List.of(new Document("$lte", List.of("$refundReview.createdAt", Date.from(now.minus(Duration.ofDays(7))))), 1, 0))))));
            Document groups = store.database().getCollection("attempt_groups").aggregate(pipeline).maxTime(5, TimeUnit.SECONDS).first();
            long pending = count("purchases", fanout), undelivered = count(RefundFanout.OUTBOX, deliveries);
            long review = groups == null ? 0 : ((Number) groups.get("count")).longValue();
            long overdue = groups == null ? 0 : ((Number) groups.get("overdue")).longValue();
            Instant oldest = now;
            for (var entry : List.of(new String[]{"purchases", "refund.appliedAt"}, new String[]{RefundFanout.OUTBOX, "createdAt"})) {
                Document first = store.database().getCollection(entry[0]).find(entry[0].equals("purchases") ? fanout : deliveries)
                        .sort(new Document(entry[1], 1)).limit(1).maxTime(5, TimeUnit.SECONDS).first();
                Date date = first == null ? null : entry[0].equals("purchases") ? first.get("refund", Document.class).getDate("appliedAt") : first.getDate("createdAt");
                if (date != null && date.toInstant().isBefore(oldest)) oldest = date.toInstant();
            }
            if (groups != null && groups.getDate("oldest").toInstant().isBefore(oldest)) oldest = groups.getDate("oldest").toInstant();
            long seconds = Math.max(0, Duration.between(oldest, now).getSeconds());
            boolean auth = count(RefundFanout.OUTBOX, new Document("environment", environment).append("work.state", "BLOCKED_AUTH")) > 0;
            boolean blocked = auth || seconds > 300;
            if (auth && !observedAuth || !blocked && observedBlocked && incident || (blocked || incident) && !now.isBefore(nextIncident)) {
                var kind = auth ? REFUND_AUTH : blocked ? REFUND_DELAY : REFUND_RECOVERY;
                nextIncident = now.plusSeconds(3600);
                if (send(new PaymentProviderAlerts.Summary(paymentEnvironment, kind, now, pending, review, undelivered, seconds / 3600, overdue, "TMI-221"))) incident = blocked;
            }
            observedAuth = auth; observedBlocked = blocked;
            var local = now.atZone(ZoneId.of("Asia/Seoul"));
            if (local.getHour() >= 9 && !local.toLocalDate().equals(daily) && !now.isBefore(nextDaily)) {
                nextDaily = now.plusSeconds(3600);
                if (send(new PaymentProviderAlerts.Summary(paymentEnvironment, REFUND_DAILY, now, pending, review, undelivered, seconds / 3600, overdue, "TMI-221"))) daily = local.toLocalDate();
            }
        } catch (RuntimeException failure) { metrics.counter("billing_refund_alert_poll_failed").increment(); }
    }
    private long count(String collection, Document query) {
        return store.database().getCollection(collection).countDocuments(query, new CountOptions().maxTime(5, TimeUnit.SECONDS));
    }
    private boolean send(PaymentProviderAlerts.Summary summary) {
        try { if (sink.send(summary)) return true; } catch (RuntimeException ignored) { }
        metrics.counter("billing_refund_alert_delivery_failed").increment(); return false;
    }
}
