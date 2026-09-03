package web.tosunsaeng.billing.domain.reservation.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.global.exception.InternalApiException;

class ReserveRequestDecoderTest {

    private final ReserveRequestDecoder decoder = new ReserveRequestDecoder();

    @Test
    void decodesExactContract() {
        ReserveRequest request = decoder.decode(json("session-1", "mock-1"));

        assertThat(request.userId()).isEqualTo("e8b37a41-bae6-47f1-a770-052e6c5786d4");
        assertThat(request.sessionId()).isEqualTo("session-1");
        assertThat(request.mockExamId()).isEqualTo("mock-1");
        assertThat(request.toString()).doesNotContain("session-1", "mock-1", "e8b37a41");
    }

    @Test
    void decodesExactPhoneContinuationContract() {
        ReserveRequest request = decoder.decode(("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"session-new","mockExamId":"mock-original",
                 "continuationReason":"PHONE_REJOIN",
                 "continuationId":"018f6f36-2f42-4bf5-8c17-0be35de4872c",
                 "expectedAttemptGroupId":"group-original"}
                """).getBytes(StandardCharsets.UTF_8));

        assertThat(request.continuationReason().name()).isEqualTo("PHONE_REJOIN");
        assertThat(request.continuationId())
                .isEqualTo("018f6f36-2f42-4bf5-8c17-0be35de4872c");
        assertThat(request.expectedAttemptGroupId()).isEqualTo("group-original");
    }

    @Test
    void rejectsPartialOrUnknownContinuation() {
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"session-new","mockExamId":"mock-original",
                 "continuationReason":"PHONE_REJOIN"}
                """);
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"session-new","mockExamId":"mock-original",
                 "continuationReason":"PHONE_REJOIN",
                 "continuationId":"00000000-0000-1000-8000-000000000001",
                 "expectedAttemptGroupId":"group-original"}
                """);
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"session-new","mockExamId":"mock-original",
                 "continuationReason":"OTHER",
                 "continuationId":"018f6f36-2f42-4bf5-8c17-0be35de4872c",
                 "expectedAttemptGroupId":"group-original"}
                """);
    }

    @Test
    void rejectsDuplicateUnknownCoercionAndTrailingToken() {
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"a","sessionId":"b","mockExamId":"m"}
                """);
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"a","mockExamId":"m","extra":true}
                """);
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":1,"mockExamId":"m"}
                """);
        assertInvalid("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"a","mockExamId":"m"} {}
                """);
    }

    @Test
    void rejectsNonCanonicalUuidAndOpaqueTokenBoundaries() {
        assertInvalid("""
                {"userId":"E8B37A41-BAE6-47F1-A770-052E6C5786D4",
                 "sessionId":"a","mockExamId":"m"}
                """);
        assertThatThrownBy(() -> decoder.decode(json(" a", "m")))
                .isInstanceOf(InternalApiException.class);
        assertThatThrownBy(() -> decoder.decode(json("a".repeat(129), "m")))
                .isInstanceOf(InternalApiException.class);
    }

    private static byte[] json(String sessionId, String mockExamId) {
        return ("""
                {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "sessionId":"%s","mockExamId":"%s"}
                """).formatted(sessionId, mockExamId).getBytes(StandardCharsets.UTF_8);
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> decoder.decode(value.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
    }
}
