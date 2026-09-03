package web.tosunsaeng.billing.domain.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.global.exception.InternalApiException;

class PhoneContinuationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final String USER = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    private static final String CONTINUATION = "018f6f36-2f42-4bf5-8c17-0be35de4872c";

    private final BillingSubjectLinkRepository linkRepository = mock(
            BillingSubjectLinkRepository.class
    );
    private final AttemptGroupRepository groupRepository = mock(AttemptGroupRepository.class);
    private final TrialClaimRepository claimRepository = mock(TrialClaimRepository.class);
    private final TrialEligibilityProperties properties = properties();
    private final PhoneContinuationService service = new PhoneContinuationService(
            linkRepository, groupRepository, claimRepository, properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void returnsAuthoritativePhoneRejoinGroupAndMockExam() {
        BillingSubjectLink link = phoneLink();
        AttemptGroup group = AttemptGroup.projection(
                "group-1", "subject-1", "claim-1", "ledger-1", "mock-original",
                AttemptGroup.Status.RETAKE_AVAILABLE, NOW.minusSeconds(60)
        );
        when(linkRepository.findActiveByOwnerAndScope(USER, "opaque-scope-v1", NOW, 101))
                .thenReturn(List.of(link));
        when(groupRepository.findNonTerminalBySubject("subject-1"))
                .thenReturn(Optional.of(group));
        arrangeActiveClaim();

        PhoneContinuationResult result = service.resolve(USER).orElseThrow();

        assertThat(result.continuationReason())
                .isEqualTo(Reservation.ContinuationReason.PHONE_REJOIN);
        assertThat(result.continuationId()).isEqualTo(CONTINUATION);
        assertThat(result.attemptGroupId()).isEqualTo("group-1");
        assertThat(result.mockExamId()).isEqualTo("mock-original");
    }

    @Test
    void returnsEmptyForInitialOrUnrelatedOwnerTransition() {
        when(linkRepository.findActiveByOwnerAndScope(USER, "opaque-scope-v1", NOW, 101))
                .thenReturn(List.of());
        assertThat(service.resolve(USER)).isEmpty();

        BillingSubjectLink guestLink = mock(BillingSubjectLink.class);
        when(guestLink.getOwnerTransitionReason()).thenReturn("USER_MERGED");
        when(linkRepository.findActiveByOwnerAndScope(USER, "opaque-scope-v1", NOW, 101))
                .thenReturn(List.of(guestLink));
        assertThat(service.resolve(USER)).isEmpty();
    }

    @Test
    void gradingContextIsRetryable() {
        BillingSubjectLink first = phoneLink();
        AttemptGroup grading = AttemptGroup.projection(
                "group-1", "subject-1", "claim-1", "ledger-1", "mock-1",
                AttemptGroup.Status.GRADING, NOW.minusSeconds(60)
        );
        when(linkRepository.findActiveByOwnerAndScope(USER, "opaque-scope-v1", NOW, 101))
                .thenReturn(List.of(first));
        when(groupRepository.findNonTerminalBySubject("subject-1"))
                .thenReturn(Optional.of(grading));
        arrangeActiveClaim();

        assertThatThrownBy(() -> service.resolve(USER))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("COMMAND_PROCESSING");
    }

    private static BillingSubjectLink phoneLink() {
        BillingSubjectLink link = mock(BillingSubjectLink.class);
        when(link.getSubjectRefId()).thenReturn("subject-1");
        when(link.getTrialClaimId()).thenReturn("claim-1");
        when(link.getOwnerTransitionReason()).thenReturn("PHONE_REJOIN");
        when(link.getOwnerTransitionId()).thenReturn(CONTINUATION);
        return link;
    }

    private static TrialEligibilityProperties properties() {
        TrialEligibilityProperties value = new TrialEligibilityProperties();
        value.setExpectedConsumerScopeId("opaque-scope-v1");
        return value;
    }

    private void arrangeActiveClaim() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(TrialClaim.active(
                "claim-1", "FREE_EXAM_ONCE", "subject-1", "event-1",
                NOW.minusSeconds(60), NOW.plusSeconds(3600)
        )));
    }
}
