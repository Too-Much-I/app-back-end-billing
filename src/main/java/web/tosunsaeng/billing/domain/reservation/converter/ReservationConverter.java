package web.tosunsaeng.billing.domain.reservation.converter;

import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.reservation.application.CancelCommand;
import web.tosunsaeng.billing.domain.reservation.application.ConfirmCommand;
import web.tosunsaeng.billing.domain.reservation.application.LifecyclePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReservationStatusResult;
import web.tosunsaeng.billing.domain.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;
import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.domain.reservation.dto.response.CancelResponse;
import web.tosunsaeng.billing.domain.reservation.dto.response.ConfirmResponse;
import web.tosunsaeng.billing.domain.reservation.dto.response.ReservationStatusResponse;
import web.tosunsaeng.billing.domain.reservation.dto.response.ReserveResponse;

@Component
public class ReservationConverter {

    private final ReservePayloadHasher reservePayloadHasher;
    private final LifecyclePayloadHasher lifecyclePayloadHasher;

    public ReservationConverter(
            ReservePayloadHasher reservePayloadHasher,
            LifecyclePayloadHasher lifecyclePayloadHasher
    ) {
        this.reservePayloadHasher = reservePayloadHasher;
        this.lifecyclePayloadHasher = lifecyclePayloadHasher;
    }

    public ReserveCommand toReserveCommand(String operationId, ReserveRequest request) {
        return new ReserveCommand(
                operationId,
                request.userId(),
                request.sessionId(),
                request.mockExamId(),
                reservePayloadHasher.hash(request)
        );
    }

    public ConfirmCommand toConfirmCommand(
            String operationId,
            String reservationId,
            ConfirmRequest request
    ) {
        return new ConfirmCommand(
                operationId,
                reservationId,
                request.userId(),
                request.sessionId(),
                request.sessionCommittedAt(),
                lifecyclePayloadHasher.hashConfirm(reservationId, request)
        );
    }

    public CancelCommand toCancelCommand(
            String operationId,
            String reservationId,
            CancelRequest request
    ) {
        return new CancelCommand(
                operationId,
                reservationId,
                request.userId(),
                request.reason(),
                lifecyclePayloadHasher.hashCancel(reservationId, request)
        );
    }

    public ReserveResponse toReserveResponse(IdempotencyCommand.ResponseSnapshot snapshot) {
        return new ReserveResponse(
                snapshot.operationId(), snapshot.reservationId(), snapshot.reservationKind(),
                snapshot.reservationStatus(), snapshot.attemptGroupId(), snapshot.sessionId(),
                snapshot.mockExamId(), snapshot.expiresAt()
        );
    }

    public ConfirmResponse toConfirmResponse(
            IdempotencyCommand.LifecycleResponseSnapshot snapshot
    ) {
        return new ConfirmResponse(
                snapshot.operationId(), snapshot.reservationId(), snapshot.reservationStatus(),
                snapshot.attemptGroupId(), snapshot.attemptGroupStatus(), snapshot.sessionId(),
                snapshot.terminalAt()
        );
    }

    public CancelResponse toCancelResponse(
            IdempotencyCommand.LifecycleResponseSnapshot snapshot
    ) {
        return new CancelResponse(
                snapshot.operationId(), snapshot.reservationId(),
                snapshot.reservationStatus(), snapshot.terminalAt()
        );
    }

    public ReservationStatusResponse toStatusResponse(ReservationStatusResult result) {
        return new ReservationStatusResponse(
                result.operationId(), result.reservationId(), result.reservationKind(),
                result.reservationStatus(), result.attemptGroupId(), result.attemptGroupStatus(),
                result.sessionId(), result.mockExamId(), result.expiresAt(), result.terminalAt()
        );
    }
}
