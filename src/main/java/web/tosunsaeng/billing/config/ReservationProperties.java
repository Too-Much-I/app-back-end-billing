package web.tosunsaeng.billing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.reservation")
public class ReservationProperties {

    private Duration holdDuration = Duration.ofMinutes(5);

    public Duration getHoldDuration() {
        return holdDuration;
    }

    public void setHoldDuration(Duration holdDuration) {
        this.holdDuration = holdDuration == null ? Duration.ofMinutes(5) : holdDuration;
    }
}
