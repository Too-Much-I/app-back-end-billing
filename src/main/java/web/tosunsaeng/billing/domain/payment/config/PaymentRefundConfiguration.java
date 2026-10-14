package web.tosunsaeng.billing.domain.payment.config;

import org.springframework.context.annotation.Configuration;

/** Root core is testable; production activation awaits TMI-220 cleanup and verified adapters. */
@Configuration(proxyBeanMethods = false)
public class PaymentRefundConfiguration {
    public PaymentRefundConfiguration(PaymentProperties properties) { validate(properties); }
    public static void validate(PaymentProperties properties) {
        var flags = properties.getRefund();
        if (flags.isPublisherEnabled() && !flags.isProcessingEnabled())
            throw new IllegalStateException("Refund publisher requires processing");
        if (flags.isProcessingEnabled())
            throw new IllegalStateException("Refund pipeline incomplete: TMI-220 and provider verification required");
    }
}
