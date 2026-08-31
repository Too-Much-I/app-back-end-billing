package web.tosunsaeng.billing.global.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.internal-ingress")
public class InternalIngressProperties {

    public enum Mode {
        DISABLED,
        TEST,
        LATTICE_AWS_IAM
    }

    private Mode mode = Mode.DISABLED;
    private boolean latticeOnly;
    private String environment = "";

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.DISABLED : mode;
    }

    public boolean isLatticeOnly() {
        return latticeOnly;
    }

    public void setLatticeOnly(boolean latticeOnly) {
        this.latticeOnly = latticeOnly;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment == null ? "" : environment;
    }
}
