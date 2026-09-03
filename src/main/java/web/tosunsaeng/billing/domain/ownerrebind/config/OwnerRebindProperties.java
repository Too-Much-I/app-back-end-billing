package web.tosunsaeng.billing.domain.ownerrebind.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.owner-rebind")
public class OwnerRebindProperties {

    private boolean enabled;
    private Duration maxFutureSkew = Duration.ofSeconds(30);
    private Duration inboxRetention = Duration.ofDays(120);
    private Duration legacyFenceRetention = Duration.ofDays(120);
    private boolean cleanupEnabled;
    private Duration cleanupScanInterval = Duration.ofHours(1);
    private int cleanupBatchSize = 100;
    private int maxSubjectsPerEvent = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getMaxFutureSkew() {
        return maxFutureSkew;
    }

    public void setMaxFutureSkew(Duration maxFutureSkew) {
        this.maxFutureSkew = maxFutureSkew;
    }

    public Duration getInboxRetention() {
        return inboxRetention;
    }

    public void setInboxRetention(Duration inboxRetention) {
        this.inboxRetention = inboxRetention;
    }

    public Duration getLegacyFenceRetention() {
        return legacyFenceRetention;
    }

    public void setLegacyFenceRetention(Duration legacyFenceRetention) {
        this.legacyFenceRetention = legacyFenceRetention;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public Duration getCleanupScanInterval() {
        return cleanupScanInterval;
    }

    public void setCleanupScanInterval(Duration cleanupScanInterval) {
        this.cleanupScanInterval = cleanupScanInterval;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public int getMaxSubjectsPerEvent() {
        return maxSubjectsPerEvent;
    }

    public void setMaxSubjectsPerEvent(int maxSubjectsPerEvent) {
        this.maxSubjectsPerEvent = maxSubjectsPerEvent;
    }
}
