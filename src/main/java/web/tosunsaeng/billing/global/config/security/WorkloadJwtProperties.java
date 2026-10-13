package web.tosunsaeng.billing.global.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secrets are deployment inputs; deliberately no toString. */
@Getter @Setter
@ConfigurationProperties("billing.workload-jwt")
public class WorkloadJwtProperties {
    private boolean receiverEnabled;
    private int port;
    private String environment = "";
    private String identityPublicJwkSet = "";
    private String billingPrivateJwk = "";
    private String tlsKeyStore = "";
    private String tlsKeyStorePassword = "";
    public void validateEnvironment(String paymentEnvironment) {
        boolean valid = ("test".equals(environment) || "staging".equals(environment)) && "SANDBOX".equals(paymentEnvironment)
                || "production".equals(environment) && "PRODUCTION".equals(paymentEnvironment);
        if (!valid) throw new IllegalStateException("Invalid workload environment mapping");
    }
    public String identityIssuer() { return "urn:tosunsaeng:workload:identity:" + environment; }
    public String billingIssuer() { return "urn:tosunsaeng:workload:billing:" + environment; }
    public String recoveryAudience() { return "tosunsaeng-identity:withdrawal-recovery:" + environment; }
}
