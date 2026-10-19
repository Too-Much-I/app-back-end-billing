package web.tosunsaeng.billing.domain.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;

class ReservePayloadHasherTest {

    private final ReservePayloadHasher hasher = new ReservePayloadHasher();

    @Test
    void sameValidatedValuesConvergeAndMeaningChangesDigest() {
        ReserveRequest first = new ReserveRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4", "session-1", "mock-1"
        );
        ReserveRequest same = new ReserveRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4", "session-1", "mock-1"
        );
        ReserveRequest changed = new ReserveRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4", "session-2", "mock-1"
        );

        assertThat(hasher.hash(first))
                .isEqualTo("0f07046d3e5a080fd860d63374b29f17356e11a627de54c33734eb7f83ded4fd")
                .isEqualTo(hasher.hash(same));
        assertThat(hasher.hash(changed)).isNotEqualTo(hasher.hash(first));

        ReserveRequest continuation = new ReserveRequest(
                first.userId(), first.sessionId(), first.mockExamId(),
                web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation
                        .ContinuationReason.PHONE_REJOIN,
                "018f6f36-2f42-4bf5-8c17-0be35de4872c", "group-1"
        );
        assertThat(hasher.hash(continuation)).isNotEqualTo(hasher.hash(first));
    }
}
