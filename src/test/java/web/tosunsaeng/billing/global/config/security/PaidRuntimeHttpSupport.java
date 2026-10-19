package web.tosunsaeng.billing.global.config.security;

import java.time.Clock;
import jakarta.servlet.Filter;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;

/** Test factory for the unchanged production ingress/auth filters and ephemeral signed tokens. */
public final class PaidRuntimeHttpSupport {
    public static Filter[] filters(Clock clock) {
        var config = new WorkloadJwtProperties(); config.setEnvironment("test"); config.setPort(8444);
        config.setLearningCoreReceiverEnabled(true); config.setLearningCorePublicJwkSet(publicSet(KEY));
        var events = new AttemptGroupEventProperties(); events.setEnabled(true);
        return new Filter[]{new WorkloadIngressFilter(config, new PaymentProperties(), new OwnerRebindProperties(), events),
                new WorkloadAuthenticationFilter(config, new WorkloadJwtVerifierRegistry(config, clock))};
    }
    public static String bearer(Clock clock, String purpose) {
        long now = clock.instant().getEpochSecond();
        return "Bearer " + sign("{\"iss\":\"urn:tosunsaeng:workload:learning-core:test\",\"sub\":\"learning-core-service\","
                + "\"aud\":\"tosunsaeng-billing:" + purpose + ":test\",\"iat\":" + now + ",\"exp\":" + (now + 60)
                + ",\"jti\":\"11111111-1111-4111-8111-111111111111\"}");
    }
}
