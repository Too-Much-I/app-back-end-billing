package web.tosunsaeng.billing.domain.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;

class LifecyclePayloadHasherTest {

    private static final String RESERVATION = "36c2356c-29d1-443f-b8f1-298345ee4e89";
    private static final String USER = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    private final LifecyclePayloadHasher hasher = new LifecyclePayloadHasher();

    @Test
    void canonicalConfirmUsesFixedUtcMillisecondsAndEveryValidatedValue() {
        String first = hasher.hashConfirm(RESERVATION, new ConfirmRequest(
                USER, "session-1", Instant.parse("2026-08-26T06:30:01.200Z")
        ));
        String sameInstant = hasher.hashConfirm(RESERVATION, new ConfirmRequest(
                USER, "session-1", Instant.parse("2026-08-26T06:30:01.200000Z")
        ));
        String differentSession = hasher.hashConfirm(RESERVATION, new ConfirmRequest(
                USER, "session-2", Instant.parse("2026-08-26T06:30:01.200Z")
        ));

        assertThat(first).hasSize(64).isEqualTo(sameInstant).isNotEqualTo(differentSession);
    }

    @Test
    void confirmAndCancelNamespacesCannotCollide() {
        String confirm = hasher.hashConfirm(RESERVATION, new ConfirmRequest(
                USER, "CALLER_ABORTED", Instant.parse("2026-08-26T06:30:01.200Z")
        ));
        String cancel = hasher.hashCancel(RESERVATION, new CancelRequest(
                USER, CancelRequest.Reason.CALLER_ABORTED
        ));

        assertThat(confirm).isNotEqualTo(cancel);
    }
}
