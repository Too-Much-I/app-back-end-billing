package web.tosunsaeng.billing.global.observability;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Bean
    ContextPropagators billingContextPropagators() {
        return ContextPropagators.create(W3CTraceContextPropagator.getInstance());
    }
}
