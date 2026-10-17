package web.tosunsaeng.billing.domain.payment.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.reservation.paid.RefundRuntimeConfiguration;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;
import com.mongodb.client.MongoDatabase;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundTestActivationTest {
    private PaymentProperties ready() {
        var p = new PaymentProperties();
        p.setEnvironment("SANDBOX");
        p.getRecovery().setFenceRequired(true);
        p.getRefund().setProcessingEnabled(true);
        return p;
    }
    @Test void bothSupportedTestFlagCombinationsPassWithoutOpeningProduction() {
        var p = ready();
        assertThatCode(() -> PaymentRefundConfiguration.validate(p, new String[]{"test"}, "test", "fake-billing-test"))
                .doesNotThrowAnyException();
        p.getRefund().setPublisherEnabled(true);
        assertThatCode(() -> PaymentRefundConfiguration.validate(p, new String[]{"test"}, "test", "fake-billing-test"))
                .doesNotThrowAnyException();
        p.getRefund().setProcessingEnabled(false);
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p, new String[]{"test"}, "test", "fake-billing-test"))
                .hasMessageContaining("requires processing");
    }
    @ParameterizedTest @ValueSource(strings = {"production", "staging", "local", ""})
    void nonTestProfilesRemainClosed(String profile) {
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), new String[]{profile}, "test", "fake-test"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void mixedProfilesAndWrongEnvironmentsFail() {
        var p = ready();
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p, new String[]{"test", "production"}, "test", "fake-test"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p, new String[]{"test"}, "staging", "fake-test"))
                .isInstanceOf(IllegalStateException.class);
        p.setEnvironment("PRODUCTION");
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p, new String[]{"test"}, "test", "fake-test"))
                .isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"", "admin", "fake-production", "test", "fake-test-prod"})
    void nonTestDatabasesFail(String database) {
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), new String[]{"test"}, "test", database))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void missingFenceFails() {
        var p = ready(); p.getRecovery().setFenceRequired(false);
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p, new String[]{"test"}, "test", "fake-test"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void offDoesNotResolveDatabaseOrRequireKeys() {
        new ApplicationContextRunner().withUserConfiguration(PaymentRefundConfiguration.class)
                .withBean(PaymentProperties.class, PaymentProperties::new)
                .withBean(WorkloadJwtProperties.class, WorkloadJwtProperties::new)
                .run(c -> assertThat(c).hasNotFailed());
    }
    @Test void springUsesActualStoreDatabaseAndDoesNotBypassRuntimeDependencies() {
        var store = mock(PaymentStore.class); var db = mock(MongoDatabase.class);
        when(store.database()).thenReturn(db); when(db.getName()).thenReturn("fake-test");
        var workload = new WorkloadJwtProperties(); workload.setEnvironment("test");
        var runner = new ApplicationContextRunner().withInitializer(c -> c.getEnvironment().setActiveProfiles("test"))
                .withUserConfiguration(PaymentRefundConfiguration.class)
                .withBean(PaymentProperties.class, this::ready)
                .withBean(WorkloadJwtProperties.class, () -> workload)
                .withBean(PaymentStore.class, () -> store);
        runner.run(c -> assertThat(c).hasNotFailed());
        runner.withPropertyValues("billing.payment.refund.processing-enabled=true")
                .withUserConfiguration(RefundRuntimeConfiguration.class)
                .run(c -> assertThat(c).hasFailed()); // No fake foundation/schema or notification sink fallback.
        when(db.getName()).thenReturn("fake-production");
        runner.run(c -> assertThat(c).hasFailed());
    }
}
