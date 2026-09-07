package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Instant;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Benefit;

/** Explicit registry: catalog insertion alone never enables an unapproved benefit policy. */
public interface FreeBenefitQueryReader {
    Benefit read(String userId, Instant asOf);
}
