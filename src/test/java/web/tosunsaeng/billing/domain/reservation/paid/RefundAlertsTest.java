package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import web.tosunsaeng.billing.domain.payment.worker.PaymentProviderAlerts;
import static org.assertj.core.api.Assertions.*;

class RefundAlertsTest extends RefundOperationsTestSupport {
    @Test void doneWithUndeliveredIsDelayedAndAuthIsNotHiddenByThrottle() {
        prepare(false); var summaries = new ArrayList<PaymentProviderAlerts.Summary>();
        var alerts = new RefundAlerts(payment, "test", Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC), s -> summaries.add(s), new SimpleMeterRegistry());
        alerts.poll();
        assertThat(summaries).anyMatch(s -> s.kind() == PaymentProviderAlerts.Kind.REFUND_DELAY && s.cases() == 1);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.state", "BLOCKED_AUTH")));
        alerts.poll();
        assertThat(summaries).anyMatch(s -> s.kind() == PaymentProviderAlerts.Kind.REFUND_AUTH);
        db.getCollection(RefundFanout.OUTBOX).updateOne(new Document(), new Document("$set", new Document("work.state", "DELIVERED")));
        alerts.poll();
        assertThat(summaries).anyMatch(s -> s.kind() == PaymentProviderAlerts.Kind.REFUND_RECOVERY);
        assertThat(summaries.toString()).doesNotContain(USER, "session", "transaction");
    }
    @Test void slackFailureDoesNotResolveOpenReview() {
        prepare(true); var before = db.getCollection("attempt_groups").find().first(); var metrics = new SimpleMeterRegistry();
        new RefundAlerts(payment, "test", clock, s -> false, metrics).poll();
        assertThat(db.getCollection("attempt_groups").find().first()).isEqualTo(before);
        assertThat(metrics.counter("billing_refund_alert_delivery_failed").count()).isPositive();
    }
}
