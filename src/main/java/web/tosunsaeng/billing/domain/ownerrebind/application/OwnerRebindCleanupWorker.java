package web.tosunsaeng.billing.domain.ownerrebind.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.SubjectOwnerRebind;
import web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository;

@Component
@ConditionalOnProperty(prefix = "billing.owner-rebind", name = "cleanup-enabled", havingValue = "true")
public class OwnerRebindCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(OwnerRebindCleanupWorker.class);

    private final SubjectOwnerRebindRepository repository;
    private final OwnerRebindProperties properties;
    private final OwnerRebindMetrics metrics;
    private final Clock clock;

    public OwnerRebindCleanupWorker(
            SubjectOwnerRebindRepository repository,
            OwnerRebindProperties properties,
            OwnerRebindMetrics metrics,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${billing.owner-rebind.cleanup-scan-interval:1h}")
    public void scan() {
        runOnce(clock.instant());
    }

    public int runOnce(Instant now) {
        long startedAt = System.nanoTime();
        List<SubjectOwnerRebind> due = repository.findDue(
                now, properties.getCleanupBatchSize()
        );
        int cleaned = 0;
        int failed = 0;
        int overdue = 0;
        Duration maxLag = Duration.ZERO;
        for (SubjectOwnerRebind rebind : due) {
            Duration lag = Duration.between(rebind.getCleanupDueAt(), now);
            if (lag.compareTo(maxLag) > 0) {
                maxLag = lag;
            }
            if (lag.compareTo(Duration.ofHours(24)) > 0) {
                overdue++;
            }
            try {
                if (repository.unlinkSource(rebind.getId(), now).isPresent()) {
                    cleaned++;
                }
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        metrics.recordCleanup("success", cleaned);
        metrics.recordCleanup("failed", failed);
        metrics.recordCleanupOverdue(overdue);
        long durationNanos = Math.max(0, System.nanoTime() - startedAt);
        log.info(
                "service=billing operation=owner_rebind_cleanup scanned={} cleaned={} failed={} "
                        + "overdue={} lagBucket={} durationMs={}",
                due.size(), cleaned, failed, overdue, lagBucket(maxLag),
                TimeUnit.NANOSECONDS.toMillis(durationNanos)
        );
        return cleaned;
    }

    private static String lagBucket(Duration lag) {
        if (lag.compareTo(Duration.ofHours(24)) > 0) {
            return "over_24h";
        }
        if (lag.compareTo(Duration.ofHours(1)) > 0) {
            return "1h_to_24h";
        }
        return "up_to_1h";
    }
}
