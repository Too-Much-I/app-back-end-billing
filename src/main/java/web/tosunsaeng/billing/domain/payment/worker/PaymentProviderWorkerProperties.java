package web.tosunsaeng.billing.domain.payment.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties("billing.payment.provider.worker")
public class PaymentProviderWorkerProperties {
    private boolean enabled;
    private boolean reconciliationEnabled;
    private boolean alertsEnabled;
    private boolean terminalSyncDeleteEnabled;
    private boolean referenceRetentionEnabled;
    private long pollMillis = 1000;
    private int batch = 100;
    private String apiKey = "";
    private String budgetDatabase = "";
    private String referenceKey = "";
    private String referenceKeyId = "";
    @Override public String toString() { return "PaymentProviderWorkerProperties[REDACTED]"; }
}
