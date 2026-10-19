package web.tosunsaeng.billing.domain.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.global.exception.InternalApiException;

class ReserveContinuationPolicyTest {

    private static final String CONTINUATION_ID = "018f6f36-2f42-4bf5-8c17-0be35de4872c";

    @Test
    void acceptsOnlyExactPhoneRejoinOwnerEpochAndGroup() {
        BillingSubjectLink link = mock(BillingSubjectLink.class);
        when(link.getOwnerTransitionReason()).thenReturn("PHONE_REJOIN");
        when(link.getOwnerTransitionId()).thenReturn(CONTINUATION_ID);
        AttemptGroup group = group("group-1");
        ReserveCommand command = command(CONTINUATION_ID, "group-1");

        assertThat(ReserveContinuationPolicy.validate(
                command, link, Optional.of(group), Reservation.Kind.REPLACEMENT
        )).isEqualTo(Reservation.ContinuationReason.PHONE_REJOIN);
    }

    @Test
    void rejectsWrongContextAndDoesNotAuthorizeInitial() {
        BillingSubjectLink link = mock(BillingSubjectLink.class);
        when(link.getOwnerTransitionReason()).thenReturn("PHONE_REJOIN");
        when(link.getOwnerTransitionId()).thenReturn(CONTINUATION_ID);
        AttemptGroup group = group("group-1");

        assertConflict(command(
                "118f6f36-2f42-4bf5-8c17-0be35de4872c", "group-1"
        ), link, Optional.of(group), Reservation.Kind.REPLACEMENT);
        assertConflict(command(CONTINUATION_ID, "group-other"), link,
                Optional.of(group), Reservation.Kind.REPLACEMENT);
        assertConflict(command(CONTINUATION_ID, "group-1"), link,
                Optional.empty(), Reservation.Kind.INITIAL);
    }

    @Test
    void ordinaryReplacementWithoutContextRemainsAllowed() {
        BillingSubjectLink link = mock(BillingSubjectLink.class);
        ReserveCommand command = new ReserveCommand(
                CONTINUATION_ID, "user", "session", "mock", "hash"
        );

        assertThat(ReserveContinuationPolicy.validate(
                command, link, Optional.of(group("group-1")), Reservation.Kind.REPLACEMENT
        )).isNull();
    }

    private static ReserveCommand command(String continuationId, String groupId) {
        return new ReserveCommand(
                "018f6f36-2f42-4bf5-8c17-0be35de4872c", "user", "session", "mock",
                Reservation.ContinuationReason.PHONE_REJOIN, continuationId, groupId, "hash"
        );
    }

    private static AttemptGroup group(String groupId) {
        return AttemptGroup.projection(
                groupId, "subject", "claim", "ledger", "mock",
                AttemptGroup.Status.OPEN, Instant.parse("2026-09-03T00:00:00Z")
        );
    }

    private static void assertConflict(
            ReserveCommand command,
            BillingSubjectLink link,
            Optional<AttemptGroup> group,
            Reservation.Kind kind
    ) {
        assertThatThrownBy(() -> ReserveContinuationPolicy.validate(command, link, group, kind))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("RESERVATION_STATE_CONFLICT");
    }
}
