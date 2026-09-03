package web.tosunsaeng.billing.domain.ownerrebind.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.ownerrebind.domain.entity.SubjectOwnerRebind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository;

@ExtendWith(MockitoExtension.class)
class OwnerRebindCleanupWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-02T05:00:00Z");

    @Mock private SubjectOwnerRebindRepository repository;
    @Mock private OwnerRebindMetrics metrics;

    private OwnerRebindCleanupWorker worker;

    @BeforeEach
    void setUp() {
        OwnerRebindProperties properties = new OwnerRebindProperties();
        worker = new OwnerRebindCleanupWorker(
                repository, properties, metrics, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void unlinksAllDueLegacySourcesAndReportsCounts() {
        SubjectOwnerRebind first = rebind(1);
        SubjectOwnerRebind second = rebind(2);
        when(repository.findDue(NOW, 100)).thenReturn(List.of(first, second));
        when(repository.unlinkSource(first.getId(), NOW)).thenReturn(Optional.of(first));
        when(repository.unlinkSource(second.getId(), NOW)).thenReturn(Optional.of(second));

        assertThat(worker.runOnce(NOW)).isEqualTo(2);

        verify(metrics).recordCleanup("success", 2);
        verify(metrics).recordCleanup("failed", 0);
        verify(metrics).recordCleanupOverdue(0);
    }

    @Test
    void oneCleanupFailureDoesNotBlockRemainingRecords() {
        SubjectOwnerRebind first = rebind(3);
        SubjectOwnerRebind second = rebind(4);
        when(repository.findDue(NOW, 100)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary Mongo failure"))
                .when(repository).unlinkSource(first.getId(), NOW);
        when(repository.unlinkSource(second.getId(), NOW)).thenReturn(Optional.of(second));

        assertThat(worker.runOnce(NOW)).isEqualTo(1);

        verify(metrics).recordCleanup("success", 1);
        verify(metrics).recordCleanup("failed", 1);
        verify(metrics).recordCleanupOverdue(0);
    }

    @Test
    void overdueCleanupEmitsOnlyAggregateOperationalLog() {
        SubjectOwnerRebind overdue = SubjectOwnerRebind.waitingTerminal(
                "00000000-0000-4000-8000-000000000005",
                OwnerRebindEventKind.USER_MERGED,
                "sensitive-subject",
                "sensitive-claim",
                "00000000-0000-4000-8000-000000000001",
                "sensitive-group",
                "sensitive-session",
                1,
                NOW.minusSeconds(26 * 60 * 60L),
                NOW.minusSeconds(25 * 60 * 60L)
        );
        when(repository.findDue(NOW, 100)).thenReturn(List.of(overdue));
        when(repository.unlinkSource(overdue.getId(), NOW)).thenReturn(Optional.of(overdue));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(OwnerRebindCleanupWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            worker.runOnce(NOW);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(metrics).recordCleanupOverdue(1);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains(
                        "service=billing", "operation=owner_rebind_cleanup",
                        "scanned=1", "cleaned=1", "failed=0", "overdue=1",
                        "lagBucket=over_24h", "durationMs="
                )
                .doesNotContain(
                        overdue.getSourceUserId(), overdue.getSubjectRefId(),
                        overdue.getAttemptGroupId(), overdue.getSessionId(),
                        "sensitive-claim"
                );
    }

    private static SubjectOwnerRebind rebind(int suffix) {
        return SubjectOwnerRebind.waitingTerminal(
                "00000000-0000-4000-8000-%012d".formatted(suffix),
                OwnerRebindEventKind.USER_MERGED,
                "subject-" + suffix,
                "claim-" + suffix,
                "00000000-0000-4000-8000-000000000001",
                "group-" + suffix,
                "session-" + suffix,
                1,
                NOW.minusSeconds(3600),
                NOW
        );
    }
}
