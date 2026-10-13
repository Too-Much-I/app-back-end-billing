package web.tosunsaeng.billing.global.config.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.bind.Binder;

/** Reject stale mode selectors even while both directions are OFF. Never silently downgrade. */
@Configuration(proxyBeanMethods = false)
public class RetiredRecoveryAuthenticationConfiguration {
    public RetiredRecoveryAuthenticationConfiguration(Environment environment) {
        if (environment.containsProperty("BILLING_PAYMENT_RECOVERY_AUTHENTICATION")
                || environment.containsProperty("billing.payment.recovery.authentication")
                || Binder.get(environment).bind("billing.payment.recovery.authentication", String.class).isBound()) {
            throw new IllegalStateException("Remove retired recovery authentication setting; workload JWT is the only supported authentication");
        }
    }
}
