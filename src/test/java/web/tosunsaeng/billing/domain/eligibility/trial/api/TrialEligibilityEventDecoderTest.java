package web.tosunsaeng.billing.domain.eligibility.trial.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityEvent;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.enums.TrialEligibilityEventType;

class TrialEligibilityEventDecoderTest {

    private TrialEligibilityEventDecoder decoder;

    @BeforeEach
    void setUp() {
        TrialEligibilityProperties properties = new TrialEligibilityProperties();
        properties.setExpectedConsumerScopeId("opaque-scope-v1");
        decoder = new TrialEligibilityEventDecoder(properties);
    }

    @Test
    void decodesIdentityVerifiedAndRevokedFixtures() throws IOException {
        TrialEligibilityEvent verified = decoder.decode(fixture("trial-eligibility-verified-v1.json"));
        TrialEligibilityEvent revoked = decoder.decode(fixture("trial-eligibility-revoked-v1.json"));

        assertThat(verified.eventType()).isEqualTo(TrialEligibilityEventType.VERIFIED);
        assertThat(verified.candidates()).extracting("keyVersion").containsExactly("v1", "v2");
        assertThat(verified.payloadDigest()).matches("[0-9a-f]{64}");
        assertThat(revoked.eventType()).isEqualTo(TrialEligibilityEventType.REVOKED);
        assertThat(revoked.candidates()).isEmpty();
        assertThat(revoked.revokedAt()).isNotNull();
    }

    @Test
    void canonicalDigestIgnoresPropertyCandidateOrderWhitespaceAndTimestampZeros() throws IOException {
        TrialEligibilityEvent original = decoder.decode(fixture("trial-eligibility-verified-v1.json"));
        String reordered = """
                {"fingerprintCandidates":[
                  {"value":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","keyVersion":"v1"},
                  {"value":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","keyVersion":"v2"}],
                 "bindingRevision":1,
                 "verifiedAt":"2026-08-14T04:59:58Z",
                 "userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                 "consumerScopeId":"opaque-scope-v1",
                 "occurredAt":"2026-08-14T05:00:00Z",
                 "producer":"identity","schemaVersion":1,
                 "eventType":"PhoneEligibilityBindingVerified",
                 "eventId":"018f6f36-2f42-4bf5-8c17-0be35de4872c"}
                """;

        TrialEligibilityEvent alternative = decoder.decode(reordered.getBytes(StandardCharsets.UTF_8));

        assertThat(alternative.payloadDigest()).isEqualTo(original.payloadDigest());
    }

    @Test
    void meaningChangeProducesDifferentDigest() throws IOException {
        byte[] original = fixture("trial-eligibility-verified-v1.json");
        byte[] changed = new String(original, StandardCharsets.UTF_8)
                .replace("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(decoder.decode(changed).payloadDigest())
                .isNotEqualTo(decoder.decode(original).payloadDigest());
    }

    @Test
    void rejectsDuplicateFieldTrailingTokenCoercionAndUnknownField() throws IOException {
        String valid = new String(fixture("trial-eligibility-revoked-v1.json"), StandardCharsets.UTF_8);
        assertInvalid(valid.replaceFirst("\"schemaVersion\": 1,",
                "\"schemaVersion\": 1, \"schemaVersion\": 1,"));
        assertInvalid(valid + " {}");
        assertInvalid(valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""));
        assertInvalid(valid.replaceFirst("\\{", "{\"unknown\":true,"));
    }

    @Test
    void rejectsWrongScopeAndUnknownContractWithoutPersistableDetails() throws IOException {
        String valid = new String(fixture("trial-eligibility-revoked-v1.json"), StandardCharsets.UTF_8);

        assertUnsupported(valid.replace("opaque-scope-v1", "other-scope"));
        assertUnsupported(valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
        assertUnsupported(valid.replace("PhoneEligibilityBindingRevoked", "UnknownEvent"));
        assertUnsupported(valid.replace("\"producer\": \"identity\"", "\"producer\": \"other\""));
    }

    @Test
    void rejectsInvalidCandidateShapeRevisionUuidAndTimeOrder() throws IOException {
        String verified = new String(fixture("trial-eligibility-verified-v1.json"), StandardCharsets.UTF_8);
        assertInvalid(verified.replace("\"bindingRevision\": 1", "\"bindingRevision\": 0"));
        assertInvalid(verified.replace("018f6f36-2f42-4bf5-8c17-0be35de4872c",
                "018F6F36-2F42-4BF5-8C17-0BE35DE4872C"));
        assertInvalid(verified.replace("2026-08-14T04:59:58.000Z", "2026-08-14T05:00:01Z"));
        assertInvalid(verified.replace("\"keyVersion\": \"v2\"", "\"keyVersion\": \"v1\""));
        assertInvalid(verified.replace("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "short"));
    }

    @Test
    void acceptsEightCandidatesAndRejectsZeroOrNine() {
        assertThat(decoder.decode(verifiedWithCandidates(8).getBytes(StandardCharsets.UTF_8))
                .candidates()).hasSize(8);
        assertInvalid(verifiedWithCandidates(0));
        assertInvalid(verifiedWithCandidates(9));
    }

    @Test
    void rejectsOversizeAndRedactsSensitiveToString() throws IOException {
        assertThatThrownBy(() -> decoder.decode(new byte[TrialEligibilityEventDecoder.MAX_PAYLOAD_BYTES + 1]))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");

        TrialEligibilityEvent event = decoder.decode(fixture("trial-eligibility-verified-v1.json"));
        assertThat(event.toString())
                .doesNotContain(event.eventId(), event.userId(), event.consumerScopeId(), event.payloadDigest())
                .doesNotContain("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertThat(event.candidates().getFirst().toString())
                .doesNotContain(event.candidates().getFirst().value());
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> decoder.decode(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
    }

    private void assertUnsupported(String json) {
        assertThatThrownBy(() -> decoder.decode(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("UNSUPPORTED_CONTRACT");
    }

    private byte[] fixture(String name) throws IOException {
        return new ClassPathResource("contracts/identity/" + name).getInputStream().readAllBytes();
    }

    private static String verifiedWithCandidates(int count) {
        StringBuilder candidates = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                candidates.append(',');
            }
            char marker = (char) ('A' + index);
            candidates.append("{\"keyVersion\":\"v")
                    .append(index)
                    .append("\",\"value\":\"")
                    .append(String.valueOf(marker).repeat(43))
                    .append("\"}");
        }
        return """
                {
                  "eventId":"018f6f36-2f42-4bf5-8c17-0be35de4872c",
                  "eventType":"PhoneEligibilityBindingVerified",
                  "schemaVersion":1,
                  "producer":"identity",
                  "occurredAt":"2026-08-14T05:00:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
                  "verifiedAt":"2026-08-14T04:59:58Z",
                  "bindingRevision":1,
                  "fingerprintCandidates":[%s]
                }
                """.formatted(candidates);
    }
}
