package web.tosunsaeng.billing.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Executable contract oracle only. Not a production decoder, publisher or recovery implementation. */
class PaymentLifecycleContractFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static JsonNode fixture(String name) throws Exception {
        try (var input = PaymentLifecycleContractFixtureTest.class.getResourceAsStream(
                "/contracts/payment-lifecycle/v1/" + name + ".json")) {
            if (input == null) throw new IllegalStateException("Missing fixture: " + name);
            return JSON.readTree(input);
        }
    }

    static Stream<JsonNode> cases(String field) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        fixture("protocol-cases").get(field).forEach(rows::add);
        return rows.stream();
    }
    static Stream<JsonNode> decoderCases() throws Exception { return cases("decoderCases"); }
    static Stream<JsonNode> ackCases() throws Exception { return cases("ackCases"); }
    static Stream<JsonNode> feedCases() throws Exception { return cases("feedCases"); }
    static Stream<JsonNode> httpCases() throws Exception { return cases("httpCases"); }

    static Stream<JsonNode> numericCases() throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        fixture("numeric-boundaries").get("cases").forEach(rows::add);
        return rows.stream();
    }

    @ParameterizedTest
    @MethodSource("numericCases")
    void canonicalNumbersRejectCoercionAndOverflow(JsonNode test) {
        String kind = test.get("kind").asText();
        long min = kind.equals("SEQUENCE") ? 1L : 0L;
        long max = kind.equals("TIMESTAMP_PART") ? 4294967295L : Long.MAX_VALUE;
        if (test.get("accepted").asBoolean()) {
            assertThat(parseDecimal(test.get("value"), min, max))
                    .isEqualTo(Long.parseLong(test.get("value").textValue()));
        } else {
            assertThatThrownBy(() -> parseDecimal(test.get("value"), min, max))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static long parseDecimal(JsonNode value, long min, long max) {
        if (!value.isTextual() || !value.textValue().matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Noncanonical decimal");
        long parsed = Long.parseLong(value.textValue());
        if (parsed < min || parsed > max) throw new IllegalArgumentException("Out of range");
        return parsed;
    }

    @Test
    void sequenceIncrementCannotWrapAtLongMax() {
        assertThat(Math.incrementExact(Long.MAX_VALUE - 1)).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> Math.incrementExact(Long.MAX_VALUE))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void wireExampleUsesRowDigestAndHeaderGenerationNotAnUndefinedManifestDigest() throws Exception {
        JsonNode wire = fixture("wire-examples");
        String digest = fixture("snapshot-digest").get("expectedDigest").asText();
        assertThat(wire.get("snapshotManifest").get("contentDigest").asText()).isEqualTo(digest);
        assertThat(wire.get("baselineAck").get("contentDigest").asText()).isEqualTo(digest);
        assertThat(wire.get("baselineAck").has("manifestDigest")).isFalse();
        assertThat(wire.get("baselineAck").get("throughSequence").asText())
                .isEqualTo(wire.get("snapshotManifest").get("H").asText());
        assertThat(wire.get("snapshotManifest").get("consumerRecoveryGeneration").asText())
                .isEqualTo(wire.get("generationHeader").get("X-Consumer-Recovery-Generation").asText());
        assertThat(wire.get("feedFirstRequest").get("afterSequence").isTextual()).isTrue();
        assertThat(wire.get("billingErrorExample").has("isSuccess")).isFalse();
    }

    @Test
    void snapshotGoldenDigestPreservesNanosAndIsIndependentOfPageBoundaries() throws Exception {
        JsonNode fixture = fixture("snapshot-digest");
        List<JsonNode> rows = new ArrayList<>();
        fixture.get("canonicalRows").forEach(rows::add);
        assertThat(digest(List.of(rows))).isEqualTo(fixture.get("expectedDigest").asText());
        assertThat(digest(List.of(rows.subList(0, 2), rows.subList(2, 4))))
                .isEqualTo(fixture.get("expectedDigest").asText());
        assertThat(digest(List.of(List.of()))).isEqualTo(fixture.get("expectedEmptyDigest").asText());
        assertThatThrownBy(() -> digest(List.of(List.of(rows.get(1), rows.get(0)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> digest(List.of(List.of(rows.get(0), rows.get(0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String digest(List<List<JsonNode>> pages) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String previous = "";
        for (var page : pages) {
            for (var row : page) {
                String user = row.get("userId").asText();
                if (!UUID.fromString(user).toString().equals(user) || user.compareTo(previous) <= 0)
                    throw new IllegalArgumentException("Noncanonical order or UUID");
                ObjectNode canonical = JSON.createObjectNode().put("userId", user)
                        .put("withdrawnAt", Instant.parse(row.get("withdrawnAt").asText()).toString());
                byte[] bytes = JSON.writeValueAsBytes(canonical);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
                previous = user;
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @ParameterizedTest
    @MethodSource("decoderCases")
    void strictWithdrawalFixture(JsonNode test) throws Exception {
        ObjectNode event = (ObjectNode) fixture("protocol-cases").get("withdrawal").deepCopy();
        String suffix = "";
        switch (test.get("mutation").asText()) {
            case "UNKNOWN_FIELD" -> event.put("producer", "identity");
            case "STRING_VERSION" -> event.put("schemaVersion", "1");
            case "VERSION_TWO" -> event.put("schemaVersion", 2);
            case "UPPERCASE_ID" -> event.put("eventId", event.get("eventId").asText().toUpperCase());
            case "MISSING_USER" -> event.remove("userId");
            case "TRAILING_TOKEN" -> suffix = " {}";
            default -> { }
        }
        String payload = JSON.writeValueAsString(event) + suffix;
        if (test.get("mutation").asText().equals("DUPLICATE_FIELD"))
            payload = payload.substring(0, payload.length() - 1) + ",\"schemaVersion\":1}";
        assertThat(decode(payload)).as(test.get("name").asText()).isEqualTo(test.get("expected").asText());
    }

    private static String decode(String payload) {
        try {
            if (payload.getBytes(StandardCharsets.UTF_8).length > 16 * 1024) return "INVALID_REQUEST";
            JsonNode event = JSON.readTree(payload);
            if (!event.isObject() || event.size() != 4) return "INVALID_REQUEST";
            var fields = Set.of("eventId", "schemaVersion", "userId", "withdrawnAt");
            var names = event.fieldNames();
            while (names.hasNext()) if (!fields.contains(names.next())) return "INVALID_REQUEST";
            for (String key : List.of("eventId", "userId", "withdrawnAt"))
                if (!event.path(key).isTextual()) return "INVALID_REQUEST";
            for (String key : List.of("eventId", "userId"))
                if (!UUID.fromString(event.get(key).asText()).toString().equals(event.get(key).asText()))
                    return "INVALID_REQUEST";
            Instant.parse(event.get("withdrawnAt").asText());
            if (!event.path("schemaVersion").isIntegralNumber()) return "INVALID_REQUEST";
            return event.get("schemaVersion").asLong() == 1 ? "ACCEPT" : "UNSUPPORTED_CONTRACT";
        } catch (Exception invalid) { return "INVALID_REQUEST"; }
    }

    @ParameterizedTest
    @MethodSource("ackCases")
    void generationIsCheckedBeforeDuplicateOrStaleAck(JsonNode test) {
        String actual;
        if (!test.get("sameGeneration").asBoolean()) actual = "RECOVERY_GENERATION_MISMATCH";
        else if (test.get("mode").asText().equals("FEED")) {
            actual = test.get("current").isNull() ? "RECOVERY_NOT_READY"
                    : test.get("target").asLong() <= test.get("current").asLong() ? "NOOP" : "APPLIED";
        } else {
            actual = test.get("current").isNull() && test.get("digestMatches").asBoolean()
                    && test.get("target").asLong() == test.get("snapshotH").asLong()
                    ? "APPLIED" : "CONFLICT";
        }
        assertThat(actual).as(test.get("name").asText()).isEqualTo(test.get("expected").asText());
    }

    @ParameterizedTest
    @MethodSource("feedCases")
    void feedCannotSkipMissingRowsOrMoveFixedTarget(JsonNode test) {
        long after = test.get("after").asLong(), target = test.get("target").asLong();
        long through = after;
        String actual = after < test.get("floor").asLong() ? "REPLAY_UNAVAILABLE" : null;
        for (var item : test.get("items")) {
            if (actual != null) break;
            long next = item.asLong();
            if (next > target) actual = "INVALID_PAGE";
            else if (next != through + 1) actual = "COVERAGE_GAP";
            else through = next;
        }
        if (actual == null) actual = through == target ? "DONE"
                : through == after ? "COVERAGE_GAP" : "MORE";
        assertThat(actual).as(test.get("name").asText()).isEqualTo(test.get("expected").asText());
    }

    @ParameterizedTest
    @MethodSource("httpCases")
    void only204IsDeliverySuccess(JsonNode test) {
        int status = test.get("status").asInt();
        String actual = status == 204 ? "DELIVERED" : status == 401 || status == 403 ? "BLOCKED_AUTH"
                : Set.of(408, 425, 429).contains(status) || status >= 500 ? "RETRY" : "DEAD_LETTER";
        assertThat(actual).isEqualTo(test.get("expected").asText());
    }
}
