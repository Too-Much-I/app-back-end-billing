package web.tosunsaeng.billing.domain.partlearning;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing.part-learning-eligibility")
public class PartLearningProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
