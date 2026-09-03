package web.tosunsaeng.billing.domain.reservation.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.PhoneContinuationRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReservationStatusRequest;
import web.tosunsaeng.billing.global.exception.InternalApiException;

class LifecycleRequestDecoderTest {

    private static final String USER = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    private static final String OPERATION = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
    private final LifecycleRequestDecoder decoder = new LifecycleRequestDecoder();

    @Test
    void decodesExactConfirmContract() {
        ConfirmRequest request = decoder.decodeConfirm(bytes("""
                {"sessionCommittedAt":"2026-08-26T06:30:01.200Z",
                 "sessionId":"session-1","userId":"%s"}
                """.formatted(USER)));

        assertThat(request.userId()).isEqualTo(USER);
        assertThat(request.sessionId()).isEqualTo("session-1");
        assertThat(request.sessionCommittedAt())
                .isEqualTo(Instant.parse("2026-08-26T06:30:01.200Z"));
    }

    @Test
    void rejectsDuplicateUnknownCoercionTrailingAndNonMillisecondTimestamp() {
        assertInvalid("""
                {"userId":"%s","userId":"%s","sessionId":"s",
                 "sessionCommittedAt":"2026-08-26T06:30:01.200Z"}
                """.formatted(USER, USER));
        assertInvalid("""
                {"userId":"%s","sessionId":"s","sessionCommittedAt":
                 "2026-08-26T06:30:01.200Z","extra":true}
                """.formatted(USER));
        assertInvalid("""
                {"userId":"%s","sessionId":1,
                 "sessionCommittedAt":"2026-08-26T06:30:01.200Z"}
                """.formatted(USER));
        assertInvalid("""
                {"userId":"%s","sessionId":"s",
                 "sessionCommittedAt":"2026-08-26T06:30:01.200Z"} {}
                """.formatted(USER));
        assertInvalid("""
                {"userId":"%s","sessionId":"s",
                 "sessionCommittedAt":"2026-08-26T06:30:01Z"}
                """.formatted(USER));
    }

    @Test
    void cancelAndStatusAcceptOnlyApprovedValuesAndUuidV4() {
        CancelRequest cancel = decoder.decodeCancel(bytes("""
                {"reason":"CALLER_ABORTED","userId":"%s"}
                """.formatted(USER)));
        ReservationStatusRequest status = decoder.decodeStatus(bytes("""
                {"operationId":"%s","userId":"%s"}
                """.formatted(OPERATION, USER)));

        assertThat(cancel.reason()).isEqualTo(CancelRequest.Reason.CALLER_ABORTED);
        assertThat(status.operationId()).isEqualTo(OPERATION);
        assertThatThrownBy(() -> decoder.decodeCancel(bytes("""
                {"reason":"OTHER","userId":"%s"}
                """.formatted(USER))))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
        assertThatThrownBy(() -> decoder.decodeStatus(bytes("""
                {"operationId":"00000000-0000-1000-8000-000000000001","userId":"%s"}
                """.formatted(USER))))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void phoneContinuationAcceptsOnlyExactUserField() {
        PhoneContinuationRequest request = decoder.decodePhoneContinuation(bytes("""
                {"userId":"%s"}
                """.formatted(USER)));

        assertThat(request.userId()).isEqualTo(USER);
        assertThatThrownBy(() -> decoder.decodePhoneContinuation(bytes("""
                {"userId":"%s","extra":true}
                """.formatted(USER))))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> decoder.decodeConfirm(bytes(json)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
