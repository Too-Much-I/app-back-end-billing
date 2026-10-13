package web.tosunsaeng.billing.global.config.security;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;

/** Real signed LC JWTs on the production workload chain, not legacy test-role authentication. */
public abstract class LearningCoreJwtMvcSupport {
    @Autowired protected Clock jwtClock;
    @DynamicPropertySource static void lcProperties(DynamicPropertyRegistry r) {
        r.add("billing.workload-jwt.learning-core-receiver-enabled", () -> true);
        r.add("billing.workload-jwt.port", () -> 8444);
        r.add("billing.workload-jwt.environment", () -> "test");
        r.add("billing.workload-jwt.learning-core-public-jwk-set", () -> publicSet(KEY));
        r.add("billing.workload-jwt.tls-key-store", () -> "/fake/mvc-only.p12");
        r.add("billing.workload-jwt.tls-key-store-password", () -> "fake");
        r.add("billing.payment.environment", () -> "SANDBOX");
        r.add("billing.mongodb.initialize-indexes", () -> true);
        r.add("billing.mongodb.require-transactions", () -> true);
    }
    protected RequestPostProcessor learningCoreJwt() { return lcPrincipalJwt("learning-core-service"); }
    protected RequestPostProcessor lcPrincipalJwt(String subject) {
        return r -> {
            r.setLocalPort(8444);
            long now = jwtClock.instant().getEpochSecond();
            String purpose = WorkloadIngressFilter.learningCorePurpose(r.getRequestURI());
            String payload = "{\"iss\":\"urn:tosunsaeng:workload:learning-core:test\",\"sub\":\"" + subject
                    + "\",\"aud\":\"tosunsaeng-billing:" + purpose + ":test\",\"iat\":" + now + ",\"exp\":" + (now + 60)
                    + ",\"jti\":\"11111111-1111-4111-8111-111111111111\"}";
            r.addHeader("Authorization", "Bearer " + sign(payload)); return r;
        };
    }
    protected RequestPostProcessor workloadPort() { return r -> { r.setLocalPort(8444); return r; }; }
}
