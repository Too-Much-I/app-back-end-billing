package web.tosunsaeng.billing.domain.payment;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoverySchema;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryConfigurationTest {
    @Test void recoveryDefaultsOffAndStartupDoesNotTouchDatabase() {
        PaymentProperties properties = new PaymentProperties();
        assertThat(properties.getRecovery().isFenceRequired()).isFalse();
        PaymentStore store = mock(PaymentStore.class);
        new PaymentRecoverySchema(store, properties).run(null);
        verifyNoInteractions(store);
    }
}
