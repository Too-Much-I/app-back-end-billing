package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;

@Service
public class EntitlementQueryService {
    private final List<FreeBenefitQueryReader> readers;
    private final EntitlementSnapshotExecutor snapshots;
    private final Clock clock;
    private PaymentProperties payments = new PaymentProperties();
    private ObjectProvider<PaidEntitlementReader> paidReader;
    private ObjectProvider<ExamAdmissionReader> admission;
    public EntitlementQueryService(List<FreeBenefitQueryReader> readers, EntitlementSnapshotExecutor snapshots, Clock clock) {
        this.readers = List.copyOf(readers); this.snapshots = snapshots; this.clock = clock;
    }
    @Autowired
    public EntitlementQueryService(List<FreeBenefitQueryReader> readers, EntitlementSnapshotExecutor snapshots, Clock clock,
            PaymentProperties payments, ObjectProvider<PaidEntitlementReader> paidReader, ObjectProvider<ExamAdmissionReader> admission) {
        this(readers, snapshots, clock); this.payments = payments; this.paidReader = paidReader; this.admission = admission;
    }
    public EntitlementQueryResponse query(String userId) {
        if (readers.isEmpty() || readers.size() > 20) { throw new EntitlementQueryException(EntitlementQueryException.Failure.LIMIT); }
        PaidEntitlementReader paid = payments.isEntitlementQueryEnabled() ? paidReader.getIfAvailable() : null;
        if (payments.isEntitlementQueryEnabled()) { EntitlementQueryException.require(paid != null); paid.prepare(); }
        return snapshots.execute(() -> {
            var now = clock.instant();
            var benefits = readers.stream().map(reader -> reader.read(userId, now))
                    .sorted(Comparator.comparing(Benefit::benefitCode)).toList();
            EntitlementQueryException.require(benefits.stream().map(Benefit::benefitCode).distinct().count() == benefits.size());
            Status status = benefits.stream().anyMatch(Benefit::pending) ? Status.PENDING : Status.READY;
            if (paid == null) return new EntitlementQueryResponse(now, Consistency.LOCAL_PROJECTION, status, benefits);
            var paidResult = paid.read(userId, now);
            var reader = admission.getIfAvailable(ExamAdmissionReader::unavailable);
            var availability = reader.read(userId, now, paidResult, benefits);
            EntitlementQueryException.require(availability != null);
            return new EntitlementQueryResponse(now, Consistency.LOCAL_PROJECTION, status, benefits, paidResult, availability);
        });
    }
}
