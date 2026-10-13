package web.tosunsaeng.billing.global.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;
import static org.assertj.core.api.Assertions.*;

class PaymentPhaseTwoConfigurationTest {
    @Test void defaultOffNeedsNoPaymentWorkerOrProvider() {
        assertThat(new PublicSecurityConfig().publicConnector(new EntitlementQueryProperties(), new TrialEligibilityProperties(),
                new BillingMongoProperties(), new MockEnvironment(), new PaymentProperties())).isNotNull();
    }
    @Test void syncOnFailsEvenInTestProfileWithoutProductionBypass() {
        var p = new PaymentProperties(); p.setSyncEnabled(true);
        assertThatThrownBy(() -> new PublicSecurityConfig().publicConnector(new EntitlementQueryProperties(), new TrialEligibilityProperties(),
                new BillingMongoProperties(), new MockEnvironment().withProperty("spring.profiles.active", "test"), p))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("verified provider and worker");
    }
    @Test void paidQueryCannotBypassFreeReaderAndRecoveryReadiness() {
        var p = new PaymentProperties(); p.setEntitlementQueryEnabled(true);
        assertThatThrownBy(() -> new PublicSecurityConfig().publicConnector(new EntitlementQueryProperties(), new TrialEligibilityProperties(),
                new BillingMongoProperties(), new MockEnvironment(), p)).isInstanceOf(IllegalStateException.class).hasMessageContaining("recovery control");
    }
}
