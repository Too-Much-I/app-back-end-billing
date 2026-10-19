package web.tosunsaeng.billing.domain.entitlement.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("billing.entitlement-query")
public class EntitlementQueryProperties {
    private boolean enabled;
    private boolean publicConnectorEnabled;
    private int publicPort = 8083;
    private String issuer = "";
    private String jwksUri = "";
    private Duration clockSkew = Duration.ofSeconds(60);
    private boolean legacyAttributionVerified;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isPublicConnectorEnabled() { return publicConnectorEnabled; }
    public void setPublicConnectorEnabled(boolean value) { publicConnectorEnabled = value; }
    public int getPublicPort() { return publicPort; }
    public void setPublicPort(int value) { publicPort = value; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer = value; }
    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String value) { jwksUri = value; }
    public Duration getClockSkew() { return clockSkew; }
    public void setClockSkew(Duration value) { clockSkew = value; }
    public boolean isLegacyAttributionVerified() { return legacyAttributionVerified; }
    public void setLegacyAttributionVerified(boolean value) { legacyAttributionVerified = value; }
}
