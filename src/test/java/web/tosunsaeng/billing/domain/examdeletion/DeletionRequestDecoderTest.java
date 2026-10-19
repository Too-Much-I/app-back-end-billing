package web.tosunsaeng.billing.domain.examdeletion;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import static org.assertj.core.api.Assertions.*;

class DeletionRequestDecoderTest {
    private final DeletionRequestDecoder decoder = new DeletionRequestDecoder();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode fixture() throws Exception {
        try (var in = DeletionRequestDecoderTest.class.getResourceAsStream(
                "/contracts/lc-billing-deletion-exact-r2-draft-2/bytes.json")) { return JSON.readTree(in); }
    }
    private static String base() throws Exception { return fixture().get("cases").get(0).get("rawUtf8").asText(); }
    private DeletionRequestDecoder.Request decode(String value) { return decoder.decode(value.getBytes(StandardCharsets.UTF_8)); }
    private static void error(Runnable call, int status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(InternalApiException.class, e -> {
            assertThat(e.status().value()).isEqualTo(status); assertThat(e.code()).isEqualTo(code);
            assertThat(e.getMessage()).doesNotContain("fake-session", "11111111");
        });
    }
    @TestFactory Stream<DynamicTest> suppliedRawVectors() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode vector : fixture().get("cases")) tests.add(DynamicTest.dynamicTest(vector.get("id").asText(), () -> {
            byte[] raw = vector.has("rawHex") ? HexFormat.of().parseHex(vector.get("rawHex").asText())
                    : vector.get("rawUtf8").asText().getBytes(StandardCharsets.UTF_8);
            if (vector.has("http")) error(() -> decoder.decode(raw), vector.get("http").asInt(), vector.get("code").asText());
            else {
                byte[] canonical = DeletionRequestDecoder.canonical(JSON.readTree(raw));
                assertThat(canonical).isEqualTo(vector.get("expectedCanonicalUtf8").asText().getBytes(StandardCharsets.UTF_8));
                assertThat(DeletionRequestDecoder.digest(canonical)).isEqualTo(vector.get("expectedSha256").asText());
                if (vector.get("id").asText().equals("B03")) error(() -> decoder.decode(raw), 400, "INVALID_REQUEST");
                else assertThat(decoder.decode(raw).canonicalBytes()).isEqualTo(canonical);
            }
        }));
        return tests.stream();
    }
    @ParameterizedTest @ValueSource(strings = {"null", "true", "\"1\"", "1.0", "1e0", "{}", "[]"})
    void schemaDoesNotCoerce(String schema) throws Exception {
        error(() -> decode(uncheckedBase().replace("\"schemaVersion\":1", "\"schemaVersion\":" + schema)), 400, "INVALID_REQUEST");
    }
    private static String uncheckedBase() { try { return base(); } catch (Exception e) { throw new AssertionError(e); } }
    @Test void unsupportedSchemaDoesNotMaskInvalidSyntax() throws Exception {
        String unsupported = base().replace("\"schemaVersion\":1", "\"schemaVersion\":9223372036854775808");
        error(() -> decode(unsupported), 422, "UNSUPPORTED_CONTRACT");
        error(() -> decode(unsupported.replace("{", "{\"extra\":true,")), 400, "INVALID_REQUEST");
        error(() -> decode(unsupported.replace("fake-session-1", "")), 400, "INVALID_REQUEST");
    }
    @ParameterizedTest @ValueSource(strings = {"eventId", "schemaVersion", "userId", "deletionOperationId", "attemptGroupId", "sessionId", "deletedAt"})
    void requiredNonNullFields(String field) throws Exception {
        ObjectNode node = (ObjectNode) JSON.readTree(base()); node.remove(field);
        error(() -> decode(node.toString()), 400, "INVALID_REQUEST");
        node.putNull(field); error(() -> decode(node.toString()), 400, "INVALID_REQUEST");
    }
    @ParameterizedTest @ValueSource(strings = {"2026-02-30T00:00:00.000Z", "2026-10-16T00:00:60.000Z", "2026-10-16T24:00:00.000Z",
            "2026-10-16T00:00:00Z", "2026-10-16T00:00:00.0000Z", "2026-10-16T00:00:00.000+00:00", "2026-10-16T00:00:00.000Z\n"})
    void rejectsNonCanonicalDate(String date) throws Exception {
        ObjectNode node = (ObjectNode) JSON.readTree(base()); node.put("deletedAt", date);
        error(() -> decode(node.toString()), 400, "INVALID_REQUEST");
    }
    @Test void byteLimitAndUtf8AreExact() throws Exception {
        String raw = base(); String max = raw + " ".repeat(DeletionRequestDecoder.MAX_BYTES - raw.getBytes(StandardCharsets.UTF_8).length);
        assertThat(decode(max).payloadDigest()).isEqualTo(decode(raw).payloadDigest());
        error(() -> decode(max + " "), 413, "INVALID_REQUEST");
        error(() -> decoder.decode(raw.getBytes(StandardCharsets.UTF_16)), 400, "INVALID_REQUEST");
        error(() -> decode("\ufeff" + raw), 400, "INVALID_REQUEST");
        error(() -> decode(raw + "{}"), 400, "INVALID_REQUEST");
        error(() -> decoder.decode(null), 400, "INVALID_REQUEST");
        error(() -> decode(""), 400, "INVALID_REQUEST");
    }
    @Test void immutableRequestAndNoIdentityInToString() throws Exception {
        var request = decode(base()); byte[] before = request.canonicalBytes(); request.canonicalBytes()[0] = 0;
        assertThat(request.canonicalBytes()).isEqualTo(before);
        assertThat(request.toString()).isEqualTo("DeletionRequest[REDACTED]");
        assertThat(request.schemaVersion()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"", " x", "x ", "x\nx", "x\u0000x"})
    void opaqueRejectsInvalidScalarAndWhitespace(String session) throws Exception {
        ObjectNode node = (ObjectNode) JSON.readTree(base()); node.put("sessionId", session);
        error(() -> decode(node.toString()), 400, "INVALID_REQUEST");
    }
    @Test void escapedUnpairedSurrogatesAreNotReplacedBeforeDecoder() throws Exception {
        String raw = base();
        error(() -> decode(raw.replace("fake-session-1", "\\ud800")), 400, "INVALID_REQUEST");
        error(() -> decode(raw.replace("fake-session-1", "\\udc00")), 400, "INVALID_REQUEST");
    }
    @Test void opaqueBoundaryAndUnicodeAreNotNormalized() throws Exception {
        ObjectNode node = (ObjectNode) JSON.readTree(base()); node.put("sessionId", "a".repeat(128));
        assertThat(decode(node.toString()).sessionId()).hasSize(128);
        node.put("sessionId", "a".repeat(129)); error(() -> decode(node.toString()), 400, "INVALID_REQUEST");
        node.put("sessionId", "한글/😀"); assertThat(decode(node.toString()).sessionId()).isEqualTo("한글/😀");
    }
    @ParameterizedTest @ValueSource(strings = {"11111111-1111-4111-7111-111111111111", "11111111-1111-5111-8111-111111111111",
            "AAAAAAAA-1111-4111-8111-111111111111", "1-1111-4111-8111-111111111111", "11111111-1111-4111-8111-111111111111\n"})
    void rejectsInvalidEventUuid(String id) throws Exception {
        ObjectNode node = (ObjectNode) JSON.readTree(base()); node.put("eventId", id);
        error(() -> decode(node.toString()), 400, "INVALID_REQUEST");
    }
}
