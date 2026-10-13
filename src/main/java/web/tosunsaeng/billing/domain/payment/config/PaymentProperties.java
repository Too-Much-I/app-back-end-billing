package web.tosunsaeng.billing.domain.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties("billing.payment")
public class PaymentProperties {
    private boolean catalogEnabled;
    private boolean purchaseAccountEnabled;
    private boolean withdrawalEnabled;
    private Recovery recovery = new Recovery();
    @Getter @Setter
    public static class Recovery {
        private boolean fenceRequired;
        private boolean snapshotEnabled;
        private boolean feedEnabled;
        private boolean snapshotWorkerEnabled;
        private boolean retentionEnabled;
        private boolean httpEnabled;
        private String identityBaseUrl = "";
        private String identityAllowedHost = "";
        private String identityEnvironment = "";
    }
    private String environment = "";
    private long catalogVersion = 1;
    private String limiterKey = "";
    public boolean enabled() { return catalogEnabled || purchaseAccountEnabled; }
}
