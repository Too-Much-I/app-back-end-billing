package web.tosunsaeng.billing.domain.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;

/** Explicit environment boundary; accepting configuration does not approve a Store refund or deployment. */
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
        boolean test = java.util.Arrays.equals(profiles, new String[]{"test"})
                && "test".equals(workloadEnvironment) && "SANDBOX".equals(properties.getEnvironment())
                && database != null && database.matches("[A-Za-z0-9_-]+[-_]test");
        // Spring profile "prod" is deliberately distinct from the wire environment "production".
        // Use the actual PaymentStore database, never a caller-supplied readiness assertion.
        boolean production = java.util.Arrays.equals(profiles, new String[]{"prod"})
                && "production".equals(workloadEnvironment) && "PRODUCTION".equals(properties.getEnvironment())
                && database != null && database.length() <= 63 && database.matches("[A-Za-z0-9_-]+-billing-prod");
        if ((!test && !production) || !properties.getRecovery().isFenceRequired())
            throw new IllegalStateException("Refund pipeline incomplete: requires an isolated test or production configuration and recovery fence");
    }
}
