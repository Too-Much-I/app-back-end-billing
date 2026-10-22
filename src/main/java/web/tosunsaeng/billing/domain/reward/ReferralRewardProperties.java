package web.tosunsaeng.billing.domain.reward;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rewards.referral")
public class ReferralRewardProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
