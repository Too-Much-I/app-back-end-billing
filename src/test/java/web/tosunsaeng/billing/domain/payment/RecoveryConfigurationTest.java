package web.tosunsaeng.billing.domain.payment;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoverySchema;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentSnapshotSchema;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryConfigurationTest {
    @Test void recoveryDefaultsOffAndStartupDoesNotTouchDatabase() {
        PaymentProperties properties = new PaymentProperties();
        assertThat(properties.getRecovery().isFenceRequired()).isFalse();
        assertThat(properties.getRecovery().isSnapshotEnabled()).isFalse();
        PaymentStore store = mock(PaymentStore.class);
        new PaymentRecoverySchema(store, properties).run(null);
        new PaymentSnapshotSchema(store, properties).run(null);
        verifyNoInteractions(store);
    }
    @Test void snapshotCannotBeEnabledWithoutFence() {
        PaymentProperties properties = new PaymentProperties();
        properties.getRecovery().setSnapshotEnabled(true);
        PaymentStore store = mock(PaymentStore.class);
        assertThatThrownBy(() -> new PaymentSnapshotSchema(store, properties).run(null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store);
    }
}
