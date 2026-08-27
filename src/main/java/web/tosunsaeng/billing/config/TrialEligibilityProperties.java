package web.tosunsaeng.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.trial-eligibility")
public class TrialEligibilityProperties {

    private String expectedConsumerScopeId = "";

    public String getExpectedConsumerScopeId() {
        return expectedConsumerScopeId;
    }

    public void setExpectedConsumerScopeId(String expectedConsumerScopeId) {
        this.expectedConsumerScopeId = expectedConsumerScopeId == null ? "" : expectedConsumerScopeId;
    }
}
