package web.tosunsaeng.billing.global.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;
import static org.assertj.core.api.Assertions.*;

class WorkloadConfigurationTest {
    @Test void offDoesNotCreateConnectorOrRequireKeys() {
        var factory=new TomcatServletWebServerFactory();
        customizer(new WorkloadJwtProperties(),factory);
        assertThat(factory.getAdditionalTomcatConnectors()).isEmpty();
    }
    @Test void enabledRequiresIsolatedPortTlsAndEnvironment() {
        var p=new WorkloadJwtProperties(); p.setReceiverEnabled(true); p.setEnvironment("test");
        assertThatThrownBy(()->customizer(p,new TomcatServletWebServerFactory())).isInstanceOf(IllegalStateException.class);
        p.setPort(8083); assertThatThrownBy(()->customizer(p,new TomcatServletWebServerFactory())).isInstanceOf(IllegalStateException.class);
        p.setPort(8444); p.setTlsKeyStore("/fake/keystore.p12"); p.setTlsKeyStorePassword("fake");
        var factory=new TomcatServletWebServerFactory(); customizer(p,factory);
        assertThat(factory.getAdditionalTomcatConnectors()).singleElement().satisfies(c->{assertThat(c.getSecure()).isTrue();assertThat(c.getScheme()).isEqualTo("https");assertThat(c.findSslHostConfigs()).hasSize(1);});
        p.setEnvironment("production"); assertThatThrownBy(()->customizer(p,new TomcatServletWebServerFactory())).isInstanceOf(IllegalStateException.class);
    }
    @Test void independentReceiverFlagsUseOneTlsConnector() {
        for (boolean identity : new boolean[]{false, true}) for (boolean lc : new boolean[]{false, true}) {
            var p = new WorkloadJwtProperties(); p.setReceiverEnabled(identity); p.setLearningCoreReceiverEnabled(lc);
            p.setEnvironment("test"); p.setPort(8444); p.setTlsKeyStore("/fake/keystore.p12"); p.setTlsKeyStorePassword("fake");
            var factory = new TomcatServletWebServerFactory(); customizer(p, factory);
            assertThat(factory.getAdditionalTomcatConnectors()).hasSize(identity || lc ? 1 : 0);
        }
    }
    @Test void lcOnlyStillRequiresTlsEnvironmentAndTransactionalSchema() {
        var p = new WorkloadJwtProperties(); p.setLearningCoreReceiverEnabled(true); p.setEnvironment("test"); p.setPort(8444);
        assertThatThrownBy(() -> customizer(p, new TomcatServletWebServerFactory())).isInstanceOf(IllegalStateException.class);
        p.setTlsKeyStore("/fake/keystore.p12"); p.setTlsKeyStorePassword("fake"); p.setEnvironment("production");
        assertThatThrownBy(() -> customizer(p, new TomcatServletWebServerFactory())).isInstanceOf(IllegalStateException.class);
    }
    void customizer(WorkloadJwtProperties p,TomcatServletWebServerFactory factory) {
        var payment=new PaymentProperties();payment.setEnvironment("SANDBOX");
        var mongo=new BillingMongoProperties();mongo.setRequireTransactions(true);mongo.setInitializeIndexes(true);
        var eligibility=new TrialEligibilityProperties();eligibility.setExpectedConsumerScopeId("fake");
        new WorkloadSecurityConfiguration().workloadConnector(p,payment,new EntitlementQueryProperties(),mongo,eligibility,new MockEnvironment()).customize(factory);
    }
}
