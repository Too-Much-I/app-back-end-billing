package web.tosunsaeng.billing.domain.payment.recovery;

import java.net.URI;
import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtCodec;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties;

/** No signing key or HTTP client is instantiated while disabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "billing.payment.recovery", name = "http-enabled", havingValue = "true")
public class RecoveryHttpConfiguration {
    @Bean(destroyMethod = "close")
    HttpIdentityRecoveryClient tracedIdentityWithdrawalRecoveryClient(PaymentProperties properties, Clock clock, MeterRegistry metrics,
                                                                      WorkloadJwtProperties workload, io.micrometer.tracing.Tracer tracer) {
        return client(properties, clock, metrics, workload, tracer);
    }

    HttpIdentityRecoveryClient identityWithdrawalRecoveryClient(PaymentProperties properties, Clock clock, MeterRegistry metrics,
                                                               WorkloadJwtProperties workload) {
        return client(properties, clock, metrics, workload, io.micrometer.tracing.Tracer.NOOP);
    }

    private HttpIdentityRecoveryClient client(PaymentProperties properties, Clock clock, MeterRegistry metrics,
                                               WorkloadJwtProperties workload, io.micrometer.tracing.Tracer tracer) {
        workload.validateEnvironment(properties.getEnvironment());
        URI endpoint = jwtEndpoint(properties, workload);
        var issuer = new WorkloadJwtCodec.Issuer(workload.getBillingPrivateJwk(), workload.billingIssuer(), clock);
        return new JwtIdentityRecoveryClient(endpoint, new RecoveryTracingTransport(new RecoveryHttpTransport(), tracer),
                clock, metrics, issuer, workload.recoveryAudience());
    }

    static URI jwtEndpoint(PaymentProperties properties, WorkloadJwtProperties workload) {
        try {
            var config = properties.getRecovery(); URI uri = URI.create(config.getIdentityBaseUrl());
            if (!config.isFenceRequired() || !workload.getEnvironment().equals(config.getIdentityEnvironment())
                    || !"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().equals(config.getIdentityAllowedHost())
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() != -1 || !"".equals(uri.getRawPath())) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException invalid) { throw new IllegalStateException("Invalid Identity recovery HTTPS origin"); }
    }

}
