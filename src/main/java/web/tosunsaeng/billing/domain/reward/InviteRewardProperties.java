package web.tosunsaeng.billing.domain.reward;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rewards.invite-share")
public class InviteRewardProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
