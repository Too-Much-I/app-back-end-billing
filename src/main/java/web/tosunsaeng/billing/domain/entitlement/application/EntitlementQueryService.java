package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;

@Service
public class EntitlementQueryService {
    private final List<FreeBenefitQueryReader> readers;
    private final EntitlementSnapshotExecutor snapshots;
    private final Clock clock;
    public EntitlementQueryService(List<FreeBenefitQueryReader> readers, EntitlementSnapshotExecutor snapshots, Clock clock) {
        this.readers = List.copyOf(readers); this.snapshots = snapshots; this.clock = clock;
    }
    public EntitlementQueryResponse query(String userId) {
        if (readers.isEmpty() || readers.size() > 20) { throw new EntitlementQueryException(EntitlementQueryException.Failure.LIMIT); }
        return snapshots.execute(() -> {
            var now = clock.instant();
            var benefits = readers.stream().map(reader -> reader.read(userId, now))
                    .sorted(Comparator.comparing(Benefit::benefitCode)).toList();
            EntitlementQueryException.require(benefits.stream().map(Benefit::benefitCode).distinct().count() == benefits.size());
            return new EntitlementQueryResponse(now, Consistency.LOCAL_PROJECTION,
                    benefits.stream().anyMatch(Benefit::pending) ? Status.PENDING : Status.READY, benefits);
        });
    }
}
