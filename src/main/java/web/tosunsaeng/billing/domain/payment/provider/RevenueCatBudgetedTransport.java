package web.tosunsaeng.billing.domain.payment.provider;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Explicit decorator for the bounded read transport. Not a runtime bean and never an in-memory fallback. */
public final class RevenueCatBudgetedTransport implements RevenueCatReadClient.Transport {
    private final RevenueCatProjectBudget budget;
    private final RevenueCatReadClient.Transport delegate;
    private final String prefix;
    public RevenueCatBudgetedTransport(RevenueCatProjectBudget budget, RevenueCatReadClient.Transport delegate) {
        this.budget = Objects.requireNonNull(budget); this.delegate = Objects.requireNonNull(delegate);
        prefix = "/v2/projects/" + URLEncoder.encode(budget.project(), StandardCharsets.UTF_8).replace("+", "%20").replace(".", "%2E") + "/";
    }
    @Override public RevenueCatReadClient.Response get(URI uri, String bearer) {
        if (!"https".equals(uri.getScheme()) || !"api.revenuecat.com".equals(uri.getRawAuthority())
                || uri.getFragment() != null || !uri.getRawPath().startsWith(prefix)) throw RevenueCatFailure.contract();
        var permit = budget.acquire();
        try {
            budget.validate(permit);
            var response = delegate.get(uri, bearer);
            budget.validate(permit);
            if (response.status() == 401 || response.status() == 403) budget.blockAuth(permit);
            return response;
        } finally { budget.release(permit); }
    }
    @Override public String toString() { return "RevenueCatBudgetedTransport[REDACTED]"; }
}
