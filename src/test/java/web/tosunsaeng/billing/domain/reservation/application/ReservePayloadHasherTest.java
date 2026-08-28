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

        assertThat(hasher.hash(first)).hasSize(64).isEqualTo(hasher.hash(same));
        assertThat(hasher.hash(changed)).isNotEqualTo(hasher.hash(first));
    }
}
