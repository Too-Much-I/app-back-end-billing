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
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("enabled provider worker and recovery fence");
    }
    @Test void paidQueryCannotBypassFreeReaderAndRecoveryReadiness() {
        var p = new PaymentProperties(); p.setEntitlementQueryEnabled(true);
        assertThatThrownBy(() -> new PublicSecurityConfig().publicConnector(new EntitlementQueryProperties(), new TrialEligibilityProperties(),
                new BillingMongoProperties(), new MockEnvironment(), p)).isInstanceOf(IllegalStateException.class).hasMessageContaining("recovery control");
    }
    @Test void syncWithWorkerAndFenceStillRequiresTrustedPublicConfiguration() {
        var p = new PaymentProperties(); p.setSyncEnabled(true); p.getRecovery().setFenceRequired(true);
        var env = new MockEnvironment().withProperty("billing.payment.provider.worker.enabled", "true");
        assertThatThrownBy(() -> new PublicSecurityConfig().publicConnector(new EntitlementQueryProperties(), new TrialEligibilityProperties(),
                new BillingMongoProperties(), env, p)).isInstanceOf(IllegalStateException.class).hasMessageContaining("trusted JWT");
    }
    @Test void syncWithStructuralReadinessDoesNotRequireLongTermPurgeOrSlackFlags() {
        var p = new PaymentProperties(); p.setSyncEnabled(true); p.getRecovery().setFenceRequired(true); p.setEnvironment("SANDBOX");
        p.setLimiterKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        var query = new EntitlementQueryProperties(); query.setPublicConnectorEnabled(true);
        query.setIssuer("https://identity.test"); query.setJwksUri("https://identity.test/jwks");
        var mongo = new BillingMongoProperties(); mongo.setRequireTransactions(true); mongo.setInitializeIndexes(true);
        var env = new MockEnvironment().withProperty("billing.payment.provider.worker.enabled", "true");
        assertThat(new PublicSecurityConfig().publicConnector(query, new TrialEligibilityProperties(), mongo, env, p)).isNotNull();
        p.getRecovery().setFenceRequired(false);
        assertThatThrownBy(() -> new PublicSecurityConfig().publicConnector(query, new TrialEligibilityProperties(), mongo, env, p))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("recovery fence");
    }
}
