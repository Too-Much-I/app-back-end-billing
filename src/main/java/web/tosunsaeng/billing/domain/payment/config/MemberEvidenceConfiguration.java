package web.tosunsaeng.billing.domain.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;

@Configuration
@ConditionalOnProperty(prefix = "billing.payment", name = "member-evidence-enabled", havingValue = "true")
public class MemberEvidenceConfiguration {
    public MemberEvidenceConfiguration(PaymentProperties payment, EntitlementQueryProperties jwt) {
        if (!payment.isPurchaseAccountEnabled() || !payment.getRecovery().isFenceRequired()
                || !java.util.Set.of("SANDBOX", "PRODUCTION").contains(payment.getEnvironment())
                || jwt.getIssuer() == null || jwt.getIssuer().isBlank() || jwt.getJwksUri() == null || jwt.getJwksUri().isBlank())
            throw new IllegalStateException("Member evidence requires purchase-account, trusted Identity JWT settings and recovery fence");
    }
}
