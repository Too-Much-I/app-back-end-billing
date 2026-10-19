package web.tosunsaeng.billing.global.config.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PaymentWithdrawalSecurityConfigurationTest {
    @ParameterizedTest @ValueSource(strings = {"sigv4", "workload-jwt", "", "unknown"})
    void retiredSelectorFailsEvenWhenFeaturesOff(String value) {
        new ApplicationContextRunner().withUserConfiguration(RetiredRecoveryAuthenticationConfiguration.class)
                .withPropertyValues("billing.payment.recovery.authentication=" + value)
                .run(c -> {
                    assertThat(c).hasFailed();
                    assertThat(c.getStartupFailure()).hasRootCauseMessage(
                        "Remove retired recovery authentication setting; workload JWT is the only supported authentication");
                });
    }
    @Test void retiredEnvironmentVariableAlsoRejected() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("fake",
                Map.of("BILLING_PAYMENT_RECOVERY_AUTHENTICATION", "sigv4")));
        assertThatThrownBy(() -> new RetiredRecoveryAuthenticationConfiguration(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void noSelectorAndDisabledFeaturesNeedNoSecrets() {
        new ApplicationContextRunner().withUserConfiguration(RetiredRecoveryAuthenticationConfiguration.class)
                .run(c -> assertThat(c).hasNotFailed());
    }
}
