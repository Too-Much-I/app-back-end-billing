package web.tosunsaeng.billing.domain.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;

/** Test runtime may process validated evidence; this does not approve a Store adapter or production rollout. */
@Configuration(proxyBeanMethods = false)
public class PaymentRefundConfiguration {
    public PaymentRefundConfiguration(PaymentProperties properties, Environment environment,
            WorkloadJwtProperties workload, ObjectProvider<PaymentStore> stores) {
        String database = properties.getRefund().isProcessingEnabled() ? stores.getObject().database().getName() : "";
        validate(properties, environment.getActiveProfiles(), workload.getEnvironment(), database);
    }
    public static void validate(PaymentProperties properties) {
        validate(properties, new String[0], "", "");
    }
    public static void validate(PaymentProperties properties, String[] profiles, String workloadEnvironment, String database) {
        var flags = properties.getRefund();
        if (flags.isPublisherEnabled() && !flags.isProcessingEnabled())
            throw new IllegalStateException("Refund publisher requires processing");
        if (!flags.isProcessingEnabled()) return;
        if (!java.util.Arrays.equals(profiles, new String[]{"test"})
                || !"test".equals(workloadEnvironment) || !"SANDBOX".equals(properties.getEnvironment())
                || database == null || !database.matches("[A-Za-z0-9_-]+[-_]test")
                || !properties.getRecovery().isFenceRequired())
            throw new IllegalStateException("Refund pipeline incomplete: only isolated test/SANDBOX runtime with recovery fence is supported");
    }
}
