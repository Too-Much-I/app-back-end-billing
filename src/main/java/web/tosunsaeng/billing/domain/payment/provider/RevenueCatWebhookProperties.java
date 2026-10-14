package web.tosunsaeng.billing.domain.payment.provider;

import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation;

@Getter @Setter
@ConfigurationProperties("billing.payment.provider")
public class RevenueCatWebhookProperties {
    private boolean ingressEnabled;
    private String host = "";
    private String project = "";
    private String authorization = "";
    private String signingSecret = "";
    private String lookupKey = "";
    private String lookupKeyVersion = "";
    private Map<String, App> apps = new LinkedHashMap<>();
    @Getter @Setter public static class App {
        private PurchaseObservation.Store store;
        private Set<String> products = new LinkedHashSet<>();
    }
}
