package web.tosunsaeng.billing.domain.payment.purchase;

import java.util.Set;

/** No client-controlled environment selection. Workload/account scope is unchanged. */
public record StoreEnvironmentPolicy(String deployment, boolean sandboxPurchasesEnabled) {
    public StoreEnvironmentPolicy {
        if (!Set.of("SANDBOX", "PRODUCTION").contains(deployment)
                || sandboxPurchasesEnabled && !"PRODUCTION".equals(deployment))
            throw new IllegalArgumentException("Invalid Store environment policy");
    }
    public boolean accepts(String value) {
        return deployment.equals(value) || sandboxPurchasesEnabled && "SANDBOX".equals(value);
    }
}
