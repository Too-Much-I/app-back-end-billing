package web.tosunsaeng.billing.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.config.ReservationProperties;
import web.tosunsaeng.billing.reservation.domain.Reservation;
import web.tosunsaeng.billing.reservation.infrastructure.ReservationRepository;

class ReservationExpiryWorkerTest {

    @Test
    void scansBoundedBatchAndContinuesAfterOneReservationFailure() {
        Instant now = Instant.parse("2026-08-28T00:05:01Z");
        Reservation first = reservation("00000000-0000-4000-8000-000000000001", "session-1");
        Reservation second = reservation("00000000-0000-4000-8000-000000000002", "session-2");
        ReservationRepository repository = mock(ReservationRepository.class);
        ReservationLifecycleService lifecycle = mock(ReservationLifecycleService.class);
        ReservationLifecycleMetrics metrics = mock(ReservationLifecycleMetrics.class);
        ReservationProperties properties = new ReservationProperties();
        properties.setExpiryBatchSize(2);
        when(repository.findDue(now, 2)).thenReturn(List.of(first, second));
        when(lifecycle.expire(first.getReservationId(), now))
                .thenThrow(new RuntimeException("simulated"));
        when(lifecycle.expire(second.getReservationId(), now)).thenReturn(true);
        ReservationExpiryWorker worker = new ReservationExpiryWorker(
                repository, lifecycle, properties, metrics,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(worker.runOnce(now)).isOne();
        verify(metrics).recordExpiryBatch(2);
        verify(metrics).recordOldestDueLag(1000);
        verify(lifecycle).expire(first.getReservationId(), now);
        verify(lifecycle).expire(second.getReservationId(), now);
    }

    private static Reservation reservation(String id, String sessionId) {
        return Reservation.reserved(
                id, "subject", "00000000-0000-4000-8000-000000000003", "hash",
                Reservation.Kind.INITIAL, "group", sessionId, "mock",
                Instant.parse("2026-08-28T00:00:00Z"),
                Instant.parse("2026-08-28T00:05:00Z")
        );
    }
}
