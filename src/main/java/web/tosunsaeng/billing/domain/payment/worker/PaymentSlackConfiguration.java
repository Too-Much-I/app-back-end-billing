package web.tosunsaeng.billing.domain.payment.worker;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentSlackProperties.class)
public class PaymentSlackConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "billing.payment.provider.slack", name = "enabled", havingValue = "true")
    PaymentSlackSink paymentSlackSink(PaymentSlackProperties properties, Clock clock) {
        return new PaymentSlackSink(properties.getWebhookUrl(), clock);
    }
}
