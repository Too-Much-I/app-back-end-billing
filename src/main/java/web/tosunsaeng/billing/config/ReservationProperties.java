package web.tosunsaeng.billing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.reservation")
public class ReservationProperties {

    private Duration holdDuration = Duration.ofMinutes(5);
    private boolean expiryEnabled;
    private Duration expiryScanInterval = Duration.ofSeconds(10);
    private int expiryBatchSize = 100;
    private Duration terminalCommandRetention = Duration.ofDays(7);

    public Duration getHoldDuration() {
        return holdDuration;
    }

    public void setHoldDuration(Duration holdDuration) {
        this.holdDuration = holdDuration == null ? Duration.ofMinutes(5) : holdDuration;
    }

    public boolean isExpiryEnabled() {
        return expiryEnabled;
    }

    public void setExpiryEnabled(boolean expiryEnabled) {
        this.expiryEnabled = expiryEnabled;
    }

    public Duration getExpiryScanInterval() {
        return expiryScanInterval;
    }

    public void setExpiryScanInterval(Duration expiryScanInterval) {
        this.expiryScanInterval = expiryScanInterval == null
                ? Duration.ofSeconds(10) : expiryScanInterval;
    }

    public int getExpiryBatchSize() {
        return expiryBatchSize;
    }

    public void setExpiryBatchSize(int expiryBatchSize) {
        this.expiryBatchSize = expiryBatchSize <= 0 ? 100 : expiryBatchSize;
    }

    public Duration getTerminalCommandRetention() {
        return terminalCommandRetention;
    }

    public void setTerminalCommandRetention(Duration terminalCommandRetention) {
        this.terminalCommandRetention = terminalCommandRetention == null
                ? Duration.ofDays(7) : terminalCommandRetention;
    }
}
