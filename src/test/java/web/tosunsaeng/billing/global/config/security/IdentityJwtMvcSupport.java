package web.tosunsaeng.billing.global.config.security;

import java.time.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;

/** Ephemeral local signing key and actual production JWT chain, not a role bypass. */
public abstract class IdentityJwtMvcSupport {
    @Autowired protected Clock jwtClock;
    @DynamicPropertySource static void jwtProperties(DynamicPropertyRegistry r) {
        r.add("billing.workload-jwt.receiver-enabled", () -> true);
        r.add("billing.workload-jwt.port", () -> 8444);
        r.add("billing.workload-jwt.environment", () -> "test");
        r.add("billing.workload-jwt.identity-public-jwk-set", () -> publicSet(KEY));
        r.add("billing.workload-jwt.tls-key-store", () -> "/fake/mvc-only.p12");
        r.add("billing.workload-jwt.tls-key-store-password", () -> "fake");
        r.add("billing.payment.environment", () -> "SANDBOX");
        r.add("billing.mongodb.initialize-indexes", () -> true);
        r.add("billing.mongodb.require-transactions", () -> true);
    }
    protected RequestPostProcessor identityJwt() { return principalJwt("identity-service"); }
    protected RequestPostProcessor principalJwt(String subject) {
        return r -> {
            r.setLocalPort(8444);
            long now = jwtClock.instant().getEpochSecond();
            String audience = "tosunsaeng-billing:" + WorkloadIngressFilter.ROUTES.get(r.getRequestURI()) + ":test";
            String payload = "{\"iss\":\"" + ISSUER + "\",\"sub\":\"" + subject + "\",\"aud\":\"" + audience
                    + "\",\"iat\":" + now + ",\"exp\":" + (now + 60)
                    + ",\"jti\":\"11111111-1111-4111-8111-111111111111\"}";
            r.addHeader("Authorization", "Bearer " + sign(payload)); return r;
        };
    }
    @org.springframework.boot.test.context.TestConfiguration
    public static class ClockConfig {
        @org.springframework.context.annotation.Bean Clock clock() { return Clock.systemUTC(); }
    }
}
