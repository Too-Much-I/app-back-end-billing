package web.tosunsaeng.billing.domain.reservation.application;

import java.util.Optional;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;

final class ReserveContinuationPolicy {

    private ReserveContinuationPolicy() {
    }

    static Reservation.ContinuationReason validate(
            ReserveCommand command,
            BillingSubjectLink link,
            Optional<AttemptGroup> group,
            Reservation.Kind kind
    ) {
        boolean absent = command.continuationReason() == null
                && command.continuationId() == null
                && command.expectedAttemptGroupId() == null;
        if (absent) {
            return null;
        }
        if (command.continuationReason() != Reservation.ContinuationReason.PHONE_REJOIN
                || command.continuationId() == null
                || command.expectedAttemptGroupId() == null
                || kind != Reservation.Kind.REPLACEMENT
                || group.isEmpty()
                || !"PHONE_REJOIN".equals(link.getOwnerTransitionReason())
                || !command.continuationId().equals(link.getOwnerTransitionId())
                || !command.expectedAttemptGroupId().equals(
                        group.orElseThrow().getAttemptGroupId()
                )) {
            throw ReservationException.stateConflict();
        }
        return Reservation.ContinuationReason.PHONE_REJOIN;
    }
}
