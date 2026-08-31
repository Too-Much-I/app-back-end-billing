package web.tosunsaeng.billing.domain.attempt.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupFailureCode;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.global.exception.InternalApiException;

class AttemptGroupEventDecoderTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00.000Z");
    private AttemptGroupEventDecoder decoder;

    @BeforeEach
    void setUp() {
        AttemptGroupEventProperties properties = new AttemptGroupEventProperties();
        properties.setMaxFutureSkew(Duration.ofSeconds(30));
        decoder = new AttemptGroupEventDecoder(
                properties, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void decodesAllApprovedTargets() {
        AttemptGroupStatusEvent grading = decode(baseFields()
                + "\"targetStatus\":\"GRADING\"}");
        AttemptGroupStatusEvent completed = decode(baseFields()
                + "\"targetStatus\":\"COMPLETED\","
                + "\"evidence\":{"
                + "\"requiredFeedbackQueryable\":true,"
                + "\"validScoreQueryable\":true,"
                + "\"summaryQueryable\":true,"
                + "\"evidenceVersion\":1}}");
        AttemptGroupStatusEvent retake = decode(baseFields()
                + "\"targetStatus\":\"RETAKE_AVAILABLE\","
                + "\"failureCode\":\"SUMMARY_UNAVAILABLE\"}");

        assertThat(grading.targetStatus()).isEqualTo(AttemptGroupEventTarget.GRADING);
        assertThat(grading.evidence()).isNull();
        assertThat(completed.evidence().summaryQueryable()).isTrue();
        assertThat(retake.failureCode()).isEqualTo(
                AttemptGroupFailureCode.SUMMARY_UNAVAILABLE
        );
    }

    @Test
    void canonicalDigestIgnoresPropertyOrderAndWhitespace() {
        AttemptGroupStatusEvent compact = decode(baseFields()
                + "\"targetStatus\":\"GRADING\"}");
        String reordered = """
                {
                  "targetStatus":"GRADING",
                  "sessionId":"ex_a1b2c3d4e5_0826_1530",
                  "attemptGroupId":"be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
                  "userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                  "occurredAt":"2026-08-31T11:59:00.000Z",
                  "producer":"learning-core",
                  "schemaVersion":1,
                  "eventType":"AttemptGroupStatusChanged",
                  "eventId":"8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442"
                }
                """;

        assertThat(decode(reordered).payloadDigest()).isEqualTo(compact.payloadDigest());
    }

    @Test
    void meaningfulValueChangeChangesDigest() {
        AttemptGroupStatusEvent first = decode(baseFields()
                + "\"targetStatus\":\"GRADING\"}");
        AttemptGroupStatusEvent second = decode(baseFields().replace(
                "ex_a1b2c3d4e5_0826_1530", "ex_other"
        ) + "\"targetStatus\":\"GRADING\"}");

        assertThat(second.payloadDigest()).isNotEqualTo(first.payloadDigest());
    }

    @Test
    void equivalentUtcTimestampTextNormalizesToSameDigest() {
        String json = baseFields() + "\"targetStatus\":\"GRADING\"}";

        assertThat(decode(json.replace("11:59:00.000Z", "11:59:00Z")).payloadDigest())
                .isEqualTo(decode(json).payloadDigest());
    }

    @Test
    void rejectsDuplicateUnknownTrailingAndScalarCoercion() {
        assertInvalid(baseFields() + "\"targetStatus\":\"GRADING\","
                + "\"targetStatus\":\"GRADING\"}");
        assertInvalid(baseFields() + "\"targetStatus\":\"GRADING\",\"extra\":1}");
        assertInvalid(baseFields() + "\"targetStatus\":\"GRADING\"}{}");
        assertInvalid(baseFields().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")
                + "\"targetStatus\":\"GRADING\"}");
    }

    @Test
    void rejectsTargetSpecificFieldViolations() {
        assertInvalid(baseFields() + "\"targetStatus\":\"GRADING\","
                + "\"failureCode\":\"SUMMARY_UNAVAILABLE\"}");
        assertInvalid(baseFields() + "\"targetStatus\":\"COMPLETED\","
                + "\"evidence\":{"
                + "\"requiredFeedbackQueryable\":true,"
                + "\"validScoreQueryable\":true,"
                + "\"summaryQueryable\":false,"
                + "\"evidenceVersion\":1}}");
        assertInvalid(baseFields() + "\"targetStatus\":\"RETAKE_AVAILABLE\","
                + "\"failureCode\":\"PROVIDER_TIMEOUT\"}");
    }

    @Test
    void rejectsNonCanonicalIdentifiersTimestampAndSession() {
        assertInvalid(baseFields().replace(
                "8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442",
                "8D19E341-EC9C-4EFD-B4C0-B1F3AD4C4442"
        ) + "\"targetStatus\":\"GRADING\"}");
        assertInvalid(baseFields().replace(
                "2026-08-31T11:59:00.000Z", "2026-08-31T11:59:00.000+00:00"
        ) + "\"targetStatus\":\"GRADING\"}");
        assertInvalid(baseFields().replace(
                "ex_a1b2c3d4e5_0826_1530", " ex_a1b2c3d4e5_0826_1530"
        ) + "\"targetStatus\":\"GRADING\"}");
    }

    @Test
    void rejectsFutureEventBeyondConfiguredSkew() {
        assertInvalid(baseFields().replace(
                "2026-08-31T11:59:00.000Z", "2026-08-31T12:00:30.001Z"
        ) + "\"targetStatus\":\"GRADING\"}");
    }

    @Test
    void unsupportedContractUsesStableCode() {
        assertThatThrownBy(() -> decode(baseFields().replace(
                "learning-core", "other-service"
        ) + "\"targetStatus\":\"GRADING\"}"))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_CONTRACT");
    }

    @Test
    void rejectsEmptyAndOversizedPayload() {
        assertThatThrownBy(() -> decoder.decode(new byte[0]))
                .isInstanceOf(InternalApiException.class);
        assertThatThrownBy(() -> decoder.decode(
                new byte[AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES + 1]
        )).isInstanceOf(InternalApiException.class);
    }

    @Test
    void acceptsValidPayloadAtExactSixteenKibBoundary() {
        byte[] valid = (baseFields() + "\"targetStatus\":\"GRADING\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] exactBoundary = java.util.Arrays.copyOf(
                valid, AttemptGroupEventDecoder.MAX_PAYLOAD_BYTES
        );
        java.util.Arrays.fill(exactBoundary, valid.length, exactBoundary.length, (byte) ' ');

        assertThat(decoder.decode(exactBoundary).targetStatus())
                .isEqualTo(AttemptGroupEventTarget.GRADING);
    }

    private AttemptGroupStatusEvent decode(String json) {
        return decoder.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> decode(json))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_REQUEST");
    }

    private static String baseFields() {
        return "{" +
                "\"eventId\":\"8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442\"," +
                "\"eventType\":\"AttemptGroupStatusChanged\"," +
                "\"schemaVersion\":1," +
                "\"producer\":\"learning-core\"," +
                "\"occurredAt\":\"2026-08-31T11:59:00.000Z\"," +
                "\"userId\":\"e8b37a41-bae6-47f1-a770-052e6c5786d4\"," +
                "\"attemptGroupId\":\"be07ae1d-f877-4ae4-82df-c5f442e9bb8e\"," +
                "\"sessionId\":\"ex_a1b2c3d4e5_0826_1530\",";
    }
}
