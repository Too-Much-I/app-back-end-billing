package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.ArrayList;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CleanupAlertDeliveryTest {
    @Test void enabledAlertsRequireConfiguredSlackSink() {
        var props = new web.tosunsaeng.billing.domain.payment.config.PaymentProperties();
        props.setEnvironment("SANDBOX"); props.getRecovery().setFenceRequired(true);
        props.getCleanup().setReconciliationEnabled(true); props.getCleanup().setAlertsEnabled(true);
        var runtime = mock(web.tosunsaeng.billing.domain.payment.worker.PaymentProviderWorkerRuntime.class);
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        assertThatThrownBy(() -> new CleanupConfiguration().cleanupWorker(mock(PaymentStore.class), props,
                Clock.systemUTC(), new SimpleMeterRegistry(), runtime,
                beans.getBeanProvider(web.tosunsaeng.billing.domain.payment.worker.PaymentSlackSink.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Slack");
    }
    @Test void disabledAlertsNeverReadDatabaseOrCallSink() {
        var store = mock(PaymentStore.class);
        var sink = mock(CleanupAlerts.Sink.class);
        var metrics = new SimpleMeterRegistry();
        var alerts = new CleanupAlerts(store, "SANDBOX", Clock.systemUTC(), sink, metrics, false);
        alerts.daily(); alerts.failure(); alerts.recovered();
        verifyNoInteractions(store, sink);
        assertThat(metrics.get("billing_cleanup_failure").counter().count()).isEqualTo(1);
    }
    @Test void configuredSinkAllowsEnabledWorkerConstructionWithoutSending() {
        var props = new web.tosunsaeng.billing.domain.payment.config.PaymentProperties();
        props.setEnvironment("SANDBOX"); props.getRecovery().setFenceRequired(true);
        props.getCleanup().setReconciliationEnabled(true); props.getCleanup().setAlertsEnabled(true);
        var runtime = mock(web.tosunsaeng.billing.domain.payment.worker.PaymentProviderWorkerRuntime.class);
        when(runtime.cleanupReader()).thenReturn(CleanupEvidence.unavailableProvider());
        var sink = mock(web.tosunsaeng.billing.domain.payment.worker.PaymentSlackSink.class);
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("slack", sink);
        assertThat(new CleanupConfiguration().cleanupWorker(mock(PaymentStore.class), props,
                Clock.systemUTC(), new SimpleMeterRegistry(), runtime,
                beans.getBeanProvider(web.tosunsaeng.billing.domain.payment.worker.PaymentSlackSink.class))).isNotNull();
        verifyNoInteractions(sink);
    }
    @Test void failedDeliveryIsCountedAndIncidentAndRecoveryAreBounded() {
        var time = new Time(); var metrics = new SimpleMeterRegistry();
        var sent = new ArrayList<CleanupAlerts.Kind>();
        var alerts = new CleanupAlerts(null, "SANDBOX", time, s -> {
            sent.add(s.kind()); return sent.size() != 1 && sent.size() != 3;
        }, metrics);
        alerts.failure(); alerts.failure(); alerts.recovered();
        assertThat(sent).containsExactly(CleanupAlerts.Kind.INCIDENT);
        time.now = time.now.plusSeconds(3600); alerts.failure(); alerts.recovered(); alerts.recovered();
        assertThat(sent).hasSize(3);
        time.now = time.now.plusSeconds(3600); alerts.recovered(); alerts.recovered();
        assertThat(sent).containsExactly(CleanupAlerts.Kind.INCIDENT, CleanupAlerts.Kind.INCIDENT,
                CleanupAlerts.Kind.RECOVERY, CleanupAlerts.Kind.RECOVERY);
        assertThat(metrics.get("billing_cleanup_alert_failure").counter().count()).isEqualTo(2);
    }
    static final class Time extends Clock {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
