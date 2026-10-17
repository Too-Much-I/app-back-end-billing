package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CleanupProviderReaderTest {
    final Instant now = Instant.parse("2026-10-17T00:00:00Z");
    final RevenueCatReadClient client = mock(RevenueCatReadClient.class);
    final CleanupProviderReader reader = new CleanupProviderReader(client, Clock.fixed(now, ZoneOffset.UTC), "SANDBOX");
    Subject subject(List<String> refs) {
        return new Subject("SANDBOX", "user", "life", 1, "account", 1L, refs, now, 0, now, "epoch");
    }
    @Test void allReferencesAndPagesAreRequiredButEmptyIsStillNotProof() {
        when(client.purchases(eq("old"), isNull(), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(List.of(), "p2"));
        when(client.purchases(eq("old"), eq("p2"), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(List.of(), null));
        when(client.purchases(eq("current"), isNull(), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(List.of(), null));
        var s = subject(List.of("old", "current"));
        var result = reader.read(s, now.plusSeconds(5));
        assertThat(result).isEqualTo(new Provider(Observation.ABSENT_OBSERVED, true, null));
        assertThat(new CleanupEligibilityEvaluator().evaluate(s, Local.NONE, result, null, now).reason())
                .isEqualTo(Reason.COVERAGE_UNPROVEN);
        verify(client).purchases(eq("old"), eq("p2"), any(Duration.class));
        verify(client).purchases(eq("current"), isNull(), any(Duration.class));
    }
    @ParameterizedTest @EnumSource(RevenueCatFailure.Kind.class)
    void failuresIncluding404AreNeverAbsence(RevenueCatFailure.Kind kind) {
        when(client.purchases(eq("ref"), isNull(), any(Duration.class))).thenThrow(new RevenueCatFailure(kind, null));
        assertThat(reader.read(subject(List.of("ref")), now.plusSeconds(5)).observation()).isEqualTo(Observation.UNKNOWN);
    }
    @Test void unrecognizedTransactionRequiresReview() {
        when(client.purchases(eq("ref"), isNull(), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(
                List.of(new RevenueCatPurchaseDecoder.Item("p", null, "PRODUCT_UNMAPPED")), null));
        assertThat(reader.read(subject(List.of("ref")), now.plusSeconds(5)).failure()).isEqualTo(Reason.PROVIDER_CONTRACT_ERROR);
    }
    @Test void verifiedTransactionIsRetainedWithoutReadingMorePages() {
        when(client.purchases(eq("ref"), isNull(), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(
                List.of(new RevenueCatPurchaseDecoder.Item("p", mock(PurchaseObservation.class), null)), "next"));
        assertThat(reader.read(subject(List.of("ref")), now.plusSeconds(5)).observation()).isEqualTo(Observation.FOUND);
        verify(client, times(1)).purchases(anyString(), nullable(String.class), any(Duration.class));
    }
    @Test void cyclesAndPageLimitAreIncomplete() {
        when(client.purchases(eq("ref"), nullable(String.class), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(List.of(), "loop"));
        assertThat(reader.read(subject(List.of("ref")), now.plusSeconds(5)).failure()).isEqualTo(Reason.PROVIDER_INCOMPLETE);
        reset(client);
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        when(client.purchases(eq("ref"), nullable(String.class), any(Duration.class))).thenAnswer(i -> new RevenueCatReadClient.Page(List.of(), "p" + counter.incrementAndGet()));
        assertThat(reader.read(subject(List.of("ref")), now.plusSeconds(5)).complete()).isFalse();
        assertThat(counter.get()).isEqualTo(100);
    }
    @Test void missingDuplicateReferencesAndExpiredDeadlineDoNotCallProvider() {
        assertThat(reader.read(subject(List.of()), now.plusSeconds(5)).complete()).isFalse();
        assertThat(reader.read(subject(List.of("ref", "ref")), now.plusSeconds(5)).complete()).isFalse();
        assertThat(reader.read(subject(List.of("ref")), now).complete()).isFalse();
        verifyNoInteractions(client);
    }
    @Test void resultArrivingAtDeadlineIsRejected() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now, now, now.plusSeconds(5));
        when(client.purchases(eq("ref"), isNull(), any(Duration.class))).thenReturn(new RevenueCatReadClient.Page(List.of(), null));
        assertThat(new CleanupProviderReader(client, clock, "SANDBOX").read(subject(List.of("ref")), now.plusSeconds(5)).complete()).isFalse();
    }
}
