package web.tosunsaeng.billing.domain.payment.query;

import java.time.Instant;
import java.util.List;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.Benefit;

/** PLAN-011 implements this on the same snapshot; no guessed paid/free permission in PLAN-010. */
@FunctionalInterface
public interface ExamAdmissionReader {
    ExamAvailability read(String userId, Instant asOf, PaidQueryResponse paid, List<Benefit> benefits);
    static ExamAdmissionReader unavailable() { return (user, now, paid, free) -> ExamAvailability.unavailable(); }
}
