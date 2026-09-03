package web.tosunsaeng.billing.domain.ownerrebind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.global.exception.InternalApiException;

class OwnerRebindEventDecoderTest {

    private static final Instant NOW = Instant.parse("2026-09-02T05:00:00Z");

    private PhoneOwnerRebindEventDecoder phoneDecoder;
    private UserMergedEventDecoder mergeDecoder;

    @BeforeEach
    void setUp() {
        OwnerRebindJsonSupport json = new OwnerRebindJsonSupport();
        OwnerRebindProperties properties = new OwnerRebindProperties();
        TrialEligibilityProperties eligibility = new TrialEligibilityProperties();
        eligibility.setExpectedConsumerScopeId("opaque-scope-v1");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        phoneDecoder = new PhoneOwnerRebindEventDecoder(
                json, properties, eligibility, clock
        );
        mergeDecoder = new UserMergedEventDecoder(json, properties, clock);
    }

    @Test
    void decodesExactPhoneContractAndCanonicalizesPropertyOrder() {
        OwnerRebindCommand first = phoneDecoder.decode(phoneJson("", 2, 1));
        String reordered = """
                {
                  "targetBindingRevision":1,
                  "sourceBindingRevision":2,
                  "lifecycleReason":"PHONE_REJOIN",
                  "targetUserId":"00000000-0000-4000-8000-000000000003",
                  "sourceUserId":"00000000-0000-4000-8000-000000000002",
                  "occurredAt":"2026-09-02T04:59:00Z",
                  "consumerScopeId":"opaque-scope-v1",
                  "producer":"identity",
                  "schemaVersion":1,
                  "eventType":"TrialOwnerRebindApproved",
                  "eventId":"00000000-0000-4000-8000-000000000001"
                }
                """;
        OwnerRebindCommand second = phoneDecoder.decode(bytes(reordered));

        assertThat(first.eventKind()).isEqualTo(OwnerRebindEventKind.PHONE_REJOIN);
        assertThat(first.sourceBindingRevision()).isEqualTo(2);
        assertThat(first.payloadDigest()).isEqualTo(second.payloadDigest());
    }

    @Test
    void phoneDecoderRejectsDuplicateUnknownCoercionAndWrongScope() {
        assertInvalid(phoneJson(",\"schemaVersion\":1", 2, 1));
        assertInvalid(phoneJson(",\"forceTransfer\":true", 2, 1));
        assertInvalid(bytes(new String(phoneJson("", 2, 1), StandardCharsets.UTF_8)
                .replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")));

        byte[] wrongScope = bytes(new String(phoneJson("", 2, 1), StandardCharsets.UTF_8)
                .replace("opaque-scope-v1", "other-scope"));
        assertThatThrownBy(() -> phoneDecoder.decode(wrongScope))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_CONTRACT");
    }

    @Test
    void userMergedKeepsExistingV1FieldSet() {
        String json = """
                {
                  "eventId":"00000000-0000-4000-8000-000000000011",
                  "schemaVersion":1,
                  "sourceUserId":"00000000-0000-4000-8000-000000000012",
                  "targetUserId":"00000000-0000-4000-8000-000000000013",
                  "occurredAt":"2026-09-02T04:59:00Z"
                }
                """;
        OwnerRebindCommand command = mergeDecoder.decode(bytes(json));
        assertThat(command.eventKind()).isEqualTo(OwnerRebindEventKind.USER_MERGED);
        assertThat(command.consumerScopeId()).isNull();

        assertThatThrownBy(() -> mergeDecoder.decode(bytes(
                json.replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"eventType\":\"UserMerged\",")
        ))).isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_REQUEST");
    }

    @Test
    void rejectsSameOwnerFutureEventTrailingTokenAndOversize() {
        byte[] sameOwner = bytes(new String(phoneJson("", 2, 1), StandardCharsets.UTF_8)
                .replace("00000000-0000-4000-8000-000000000003",
                        "00000000-0000-4000-8000-000000000002"));
        assertInvalid(sameOwner);

        byte[] future = bytes(new String(phoneJson("", 2, 1), StandardCharsets.UTF_8)
                .replace("2026-09-02T04:59:00Z", "2026-09-02T05:01:00Z"));
        assertInvalid(future);
        assertInvalid(bytes(new String(phoneJson("", 2, 1), StandardCharsets.UTF_8) + "{}"));
        assertInvalid(new byte[PhoneOwnerRebindEventDecoder.MAX_PAYLOAD_BYTES + 1]);
    }

    @Test
    void distinguishesUnsupportedContractFromMalformedValues() {
        String valid = new String(phoneJson("", 2, 1), StandardCharsets.UTF_8);
        assertUnsupported(bytes(valid.replace(
                "TrialOwnerRebindApproved", "UnexpectedOwnerEvent"
        )));
        assertUnsupported(bytes(valid.replace("\"producer\":\"identity\"",
                "\"producer\":\"other\"")));
        assertUnsupported(bytes(valid.replace("PHONE_REJOIN", "ACCOUNT_MERGE")));
        assertUnsupported(bytes(valid.replace("\"schemaVersion\":1",
                "\"schemaVersion\":2")));

        assertInvalid(phoneJson("", 0, 1));
        assertInvalid(bytes(valid.replace(
                "00000000-0000-4000-8000-000000000002",
                "00000000-0000-4000-8000-00000000000A"
        )));
    }

    private void assertInvalid(byte[] payload) {
        assertThatThrownBy(() -> phoneDecoder.decode(payload))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_REQUEST");
    }

    private void assertUnsupported(byte[] payload) {
        assertThatThrownBy(() -> phoneDecoder.decode(payload))
                .isInstanceOf(InternalApiException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_CONTRACT");
    }

    private static byte[] phoneJson(String extra, long sourceRevision, long targetRevision) {
        return bytes("""
                {
                  "eventId":"00000000-0000-4000-8000-000000000001",
                  "eventType":"TrialOwnerRebindApproved",
                  "schemaVersion":1,
                  "producer":"identity",
                  "consumerScopeId":"opaque-scope-v1",
                  "occurredAt":"2026-09-02T04:59:00Z",
                  "sourceUserId":"00000000-0000-4000-8000-000000000002",
                  "targetUserId":"00000000-0000-4000-8000-000000000003",
                  "lifecycleReason":"PHONE_REJOIN",
                  "sourceBindingRevision":%d,
                  "targetBindingRevision":%d%s
                }
                """.formatted(sourceRevision, targetRevision, extra));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
