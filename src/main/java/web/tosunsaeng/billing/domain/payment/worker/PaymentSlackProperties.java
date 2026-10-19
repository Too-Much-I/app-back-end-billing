package web.tosunsaeng.billing.domain.payment.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties("billing.payment.provider.slack")
public class PaymentSlackProperties {
    private boolean enabled;
    private String webhookUrl = "";
    @Override public String toString() { return "PaymentSlackProperties[REDACTED]"; }
}
