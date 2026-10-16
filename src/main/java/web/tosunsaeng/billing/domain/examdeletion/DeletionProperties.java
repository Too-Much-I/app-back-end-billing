package web.tosunsaeng.billing.domain.examdeletion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing.exam-deletion")
public class DeletionProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
