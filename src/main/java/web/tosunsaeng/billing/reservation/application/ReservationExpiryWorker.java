package web.tosunsaeng.billing.reservation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.config.ReservationProperties;
import web.tosunsaeng.billing.reservation.domain.Reservation;
import web.tosunsaeng.billing.reservation.infrastructure.ReservationRepository;

@Component
@ConditionalOnProperty(prefix = "billing.reservation", name = "expiry-enabled", havingValue = "true")
public class ReservationExpiryWorker {

    private final ReservationRepository reservationRepository;
    private final ReservationLifecycleService lifecycleService;
    private final ReservationProperties properties;
    private final ReservationLifecycleMetrics metrics;
    private final Clock clock;

    public ReservationExpiryWorker(
            ReservationRepository reservationRepository,
            ReservationLifecycleService lifecycleService,
            ReservationProperties properties,
            ReservationLifecycleMetrics metrics,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.lifecycleService = lifecycleService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${billing.reservation.expiry-scan-interval:10s}")
    public void scan() {
        runOnce(clock.instant());
    }

    public int runOnce(Instant now) {
        List<Reservation> due = reservationRepository.findDue(
                now, properties.getExpiryBatchSize()
        );
        metrics.recordExpiryBatch(due.size());
        if (!due.isEmpty()) {
            metrics.recordOldestDueLag(now.toEpochMilli() - due.getFirst().getExpiresAt().toEpochMilli());
        }
        int expired = 0;
        for (Reservation reservation : due) {
            try {
                if (lifecycleService.expire(reservation.getReservationId(), now)) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                metrics.record("EXPIRE", reservation.getReservationKind(), "FAILED");
            }
        }
        return expired;
    }
}
