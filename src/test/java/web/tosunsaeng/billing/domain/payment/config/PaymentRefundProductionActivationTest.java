package web.tosunsaeng.billing.domain.payment.config;

import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.reservation.paid.RefundRuntimeConfiguration;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundProductionActivationTest {
    private PaymentProperties ready() {
        var p = new PaymentProperties();
        p.setEnvironment("PRODUCTION");
        p.getRecovery().setFenceRequired(true);
        p.getRefund().setProcessingEnabled(true);
        return p;
    }
    private void validate(PaymentProperties p) {
        PaymentRefundConfiguration.validate(p, new String[]{"prod"}, "production", "fake-billing-prod");
    }
    @Test void existingTwoSwitchesAreEnoughButPublisherStillRequiresProcessing() {
        var p = ready();
        assertThatCode(() -> validate(p)).doesNotThrowAnyException();
        p.getRefund().setPublisherEnabled(true);
        assertThatCode(() -> validate(p)).doesNotThrowAnyException();
        p.getRefund().setProcessingEnabled(false);
        assertThatThrownBy(() -> validate(p)).hasMessageContaining("requires processing");
    }
    @ParameterizedTest @ValueSource(strings = {"test", "production", "staging", "local", ""})
    void rejectsWrongProfile(String profile) {
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), new String[]{profile}, "production", "fake-billing-prod"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsMissingOrMixedProfiles() {
        for (var profiles : new String[][]{{}, {"prod", "test"}, {"test", "prod"}, {"prod", "production"}})
            assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), profiles, "production", "fake-billing-prod"))
                    .isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"test", "staging", "prod", "PRODUCTION", ""})
    void rejectsWrongWorkloadEnvironment(String environment) {
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), new String[]{"prod"}, environment, "fake-billing-prod"))
                .isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"SANDBOX", "production", ""})
    void rejectsWrongPaymentEnvironment(String environment) {
        var p = ready(); p.setEnvironment(environment);
        assertThatThrownBy(() -> validate(p)).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", "admin", "config", "local", "fake-billing-test", "fake-prod", "billing-prod",
            "fake-billing-production", "fake-billing-prod-extra", "fake.billing-prod", "fake/billing-prod"})
    void rejectsNonBillingProductionDatabase(String database) {
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), new String[]{"prod"}, "production", database))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsOverlongDatabaseAndMissingFence() {
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(ready(), new String[]{"prod"}, "production", "a".repeat(51) + "-billing-prod"))
                .isInstanceOf(IllegalStateException.class);
        var p = ready(); p.getRecovery().setFenceRequired(false);
        assertThatThrownBy(() -> validate(p)).isInstanceOf(IllegalStateException.class);
    }
    @Test void defaultsRemainOffAndOffDoesNotRequireStoreKeysOrRuntime() {
        var p = new PaymentProperties();
        assertThat(p.getRefund().isProcessingEnabled()).isFalse();
        assertThat(p.getRefund().isPublisherEnabled()).isFalse();
        new ApplicationContextRunner().withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(PaymentRefundConfiguration.class, RefundRuntimeConfiguration.class)
                .withBean(PaymentProperties.class, () -> p)
                .withBean(WorkloadJwtProperties.class, WorkloadJwtProperties::new)
                .run(c -> assertThat(c).hasNotFailed());
    }
    @Test void springChecksActualDatabaseAndStillRequiresRuntimeDependencies() {
        var store = mock(PaymentStore.class); var db = mock(MongoDatabase.class);
        when(store.database()).thenReturn(db); when(db.getName()).thenReturn("fake-billing-prod");
        var workload = new WorkloadJwtProperties(); workload.setEnvironment("production");
        var runner = new ApplicationContextRunner().withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(PaymentRefundConfiguration.class)
                .withBean(PaymentProperties.class, this::ready)
                .withBean(WorkloadJwtProperties.class, () -> workload)
                .withBean(PaymentStore.class, () -> store);
        runner.run(c -> assertThat(c).hasNotFailed());
        runner.withPropertyValues("billing.payment.refund.processing-enabled=true")
                .withUserConfiguration(RefundRuntimeConfiguration.class)
                .run(c -> assertThat(c).hasFailed());
        when(db.getName()).thenReturn("fake-billing-test");
        runner.run(c -> assertThat(c).hasFailed());
        verify(store, atLeastOnce()).database();
    }
}
