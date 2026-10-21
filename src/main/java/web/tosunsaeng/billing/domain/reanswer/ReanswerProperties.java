package web.tosunsaeng.billing.domain.reanswer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing.reanswer-eligibility")
public class ReanswerProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
