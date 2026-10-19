package web.tosunsaeng.billing.domain.reservation.paid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.billing.domain.payment.config.*;
import static org.assertj.core.api.Assertions.*;

class RefundRuntimeConfigurationTest {
    @Test void offRequiresNoDatabaseKeysTransportOrSlack() {
        new ApplicationContextRunner().withUserConfiguration(RefundRuntimeConfiguration.class)
                .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(RefundFanout.class).doesNotHaveBean(RefundAccessPublisher.class).doesNotHaveBean(RefundAlerts.class));
    }
    @Test void activationGateAndInvalidFlagCombinationRemainClosed() {
        var p = new PaymentProperties(); p.getRefund().setPublisherEnabled(true);
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p)).isInstanceOf(IllegalStateException.class);
        p.getRefund().setProcessingEnabled(true);
        assertThatThrownBy(() -> PaymentRefundConfiguration.validate(p)).isInstanceOf(IllegalStateException.class);
    }
}
