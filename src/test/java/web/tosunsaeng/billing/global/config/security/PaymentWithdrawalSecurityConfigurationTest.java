package web.tosunsaeng.billing.global.config.security;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;
import static org.assertj.core.api.Assertions.*;

class PaymentWithdrawalSecurityConfigurationTest {
    @Test void withdrawalRequiresEnvironmentTransactionalSchemaAndInternalIngress() {
        var p = new PaymentProperties(); p.setWithdrawalEnabled(true); p.setEnvironment("SANDBOX");
        var ingress = new InternalIngressProperties();
        var mongo = new BillingMongoProperties(); mongo.setInitializeIndexes(true); mongo.setRequireTransactions(true);
        assertThatThrownBy(() -> SecurityConfig.validateWithdrawal(ingress, p, mongo)).isInstanceOf(IllegalStateException.class);
        ingress.setMode(InternalIngressProperties.Mode.TEST);
        SecurityConfig.validateWithdrawal(ingress, p, mongo);
        mongo.setRequireTransactions(false);
        assertThatThrownBy(() -> SecurityConfig.validateWithdrawal(ingress, p, mongo)).isInstanceOf(IllegalStateException.class);
        mongo.setRequireTransactions(true); mongo.setInitializeIndexes(false);
        assertThatThrownBy(() -> SecurityConfig.validateWithdrawal(ingress, p, mongo)).isInstanceOf(IllegalStateException.class);
        mongo.setInitializeIndexes(true); ingress.setMode(InternalIngressProperties.Mode.LATTICE_AWS_IAM); ingress.setEnvironment("production");
        assertThatThrownBy(() -> SecurityConfig.validateWithdrawal(ingress, p, mongo)).isInstanceOf(IllegalStateException.class);
        p.setEnvironment("PRODUCTION");
        SecurityConfig.validateWithdrawal(ingress, p, mongo);
        p.setEnvironment("SANDBOX"); ingress.setEnvironment("staging");
        SecurityConfig.validateWithdrawal(ingress, p, mongo);
        // Withdrawal-only startup does not require enabling the public APIs/limiter/JWT connector.
        assertThat(p.enabled()).isFalse();
    }
}
