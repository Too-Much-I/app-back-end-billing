package web.tosunsaeng.billing.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Billing-owned compatibility oracle, not a production decoder or an Identity adapter.
 * Shared synthetic fixtures are preserved separately from the frozen TMI-199 v1 fixtures.
 */
class WithdrawalRecoveryFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String NEW_ID =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    private static JsonNode fixture(String name) throws Exception {
        try (var in = WithdrawalRecoveryFixtureTest.class.getResourceAsStream(
                "/contracts/payment-lifecycle/recovery-r1/" + name)) {
            return JSON.readTree(Objects.requireNonNull(in));
        }
    }

    private static Stream<JsonNode> cases(String file) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        fixture(file).get("cases").forEach(result::add);
        return result.stream();
    }

    static Stream<JsonNode> requests() throws Exception { return cases("request-cases.json"); }
    static Stream<JsonNode> responses() throws Exception { return cases("response-cases.json"); }
    static Stream<JsonNode> states() throws Exception { return cases("snapshot-state-cases.json"); }

    private static JsonSchema schema(String definition) throws Exception {
        ObjectNode root = (ObjectNode) fixture("wire.schema.json");
        root.put("$ref", "#/$defs/" + definition);
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root);
    }

    @ParameterizedTest @MethodSource("requests")
    void requestsMatchIndependentStrictOracle(JsonNode test) {
        boolean accepted;
        try {
            validateRequest(test.get("route").asText(), test.path("header").asText(),
                    test.get("body").asText());
            accepted = true;
        } catch (Exception invalid) {
            accepted = false;
        }
        assertThat(accepted).as(test.get("name").asText())
                .isEqualTo(test.get("expect").asText().equals("ACCEPT"));
    }

    private static void validateRequest(String route, String header, String raw) throws Exception {
        require(raw.getBytes(StandardCharsets.UTF_8).length <= 16384 && !raw.startsWith("\uFEFF"));
        JsonNode body = JSON.readTree(raw);
        require(body != null && body.isObject());
        Set<String> expected = switch (route) {
            case "CREATE" -> Set.of("streamId");
            case "STATUS" -> Set.of("snapshotId");
            case "SNAPSHOT_PAGE" -> Set.of("snapshotId", "cursor");
            case "FEED_PAGE" -> Set.of("streamId", "afterSequence", "scanCursor");
            default -> throw new IllegalArgumentException();
        };
        Set<String> actual = new HashSet<>();
        body.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected));
        String id = route.equals("CREATE") || route.equals("FEED_PAGE") ? "streamId" : "snapshotId";
        require(body.get(id).isTextual() && body.get(id).textValue().matches(NEW_ID));
        if (route.equals("CREATE")) require(header.matches(NEW_ID));
        if (route.equals("FEED_PAGE")) decimal(body.get("afterSequence"), false);
        for (String field : List.of("cursor", "scanCursor")) {
            if (body.has(field) && !body.get(field).isNull()) {
                JsonNode cursor = body.get(field);
                require(cursor.isTextual() && cursor.textValue().matches("[!-~]{1,2048}"));
            }
        }
    }

    private static long decimal(JsonNode node, boolean positive) {
        require(node != null && node.isTextual() && node.textValue().matches("0|[1-9][0-9]*"));
        long value = Long.parseLong(node.textValue());
        require(!positive || value > 0);
        return value;
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid synthetic contract value");
    }

    @ParameterizedTest @MethodSource("responses")
    void responseShapesMatchSchema(JsonNode test) throws Exception {
        assertThat(schema(test.get("definition").asText()).validate(test.get("value")).isEmpty())
                .as(test.get("name").asText()).isEqualTo(test.get("valid").asBoolean());
    }

    @Test
    void numericRegexMatchesBigIntegerOracleAtEveryDecimalBoundary() throws Exception {
        for (String definition : List.of("D0", "D1", "U32")) {
            BigInteger max = new BigInteger(definition.equals("U32") ? "4294967295" : "9223372036854775807");
            var validator = schema(definition);
            Set<BigInteger> probes = new HashSet<>();
            probes.add(BigInteger.ZERO);
            for (int power = 0; power <= 20; power++) {
                BigInteger step = BigInteger.TEN.pow(power);
                BigInteger prefix = max.divide(step).multiply(step);
                for (int delta = -2; delta <= 2; delta++) {
                    probes.add(prefix.add(BigInteger.valueOf(delta)));
                    probes.add(step.add(BigInteger.valueOf(delta)));
                    probes.add(max.add(step.multiply(BigInteger.valueOf(delta))));
                }
            }
            Random random = new Random(204);
            for (int i = 0; i < 500; i++) probes.add(new BigInteger(65, random));
            for (BigInteger value : probes) {
                boolean expected = value.signum() >= (definition.equals("D1") ? 1 : 0)
                        && value.compareTo(max) <= 0;
                assertThat(validator.validate(TextNode.valueOf(value.toString())).isEmpty())
                        .as("%s %s", definition, value).isEqualTo(expected);
            }
            for (String invalid : List.of("", "01", "+1", "-0", "1.0", "1e0", " 1"))
                assertThat(validator.validate(TextNode.valueOf(invalid))).as(invalid).isNotEmpty();
        }
    }

    @Test
    void amendedSchemaAndStrictDecimalBothRejectFinalNewline() throws Exception {
        for (String definition : List.of("D0", "D1", "U32")) {
            assertThat(schema(definition).validate(TextNode.valueOf("1\n"))).isNotEmpty();
        }
        assertThatThrownBy(() -> decimal(TextNode.valueOf("1\n"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    record ScalarCase(String definition, String label, String value, boolean valid) {}

    static Stream<ScalarCase> scalars() throws Exception {
        var input = fixture("scalar-cases.json");
        List<ScalarCase> cases = new ArrayList<>();
        for (var sample : input.get("samples")) {
            String definition = sample.get("definition").asText();
            String value = sample.get("value").asText();
            cases.add(new ScalarCase(definition, "valid", value, true));
            for (var forbidden : input.get("forbidden")) {
                String inserted = forbidden.get("value").asText();
                String name = forbidden.get("name").asText();
                cases.add(new ScalarCase(definition, name + "-prefix", inserted + value, false));
                cases.add(new ScalarCase(definition, name + "-infix",
                        value.substring(0, 1) + inserted + value.substring(1), false));
                cases.add(new ScalarCase(definition, name + "-suffix", value + inserted, false));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{index}: {0}") @MethodSource("scalars")
    void scalarEndAmendmentMatchesSharedCases(ScalarCase test) throws Exception {
        assertThat(schema(test.definition()).validate(TextNode.valueOf(test.value())).isEmpty())
                .as("%s %s", test.definition(), test.label()).isEqualTo(test.valid());
    }

    @Test
    void scalarFixtureCoversAllEightPatternsAndElevenForbiddenStrings() throws Exception {
        var input = fixture("scalar-cases.json");
        Set<String> definitions = new HashSet<>();
        input.get("samples").forEach(s -> definitions.add(s.get("definition").asText()));
        assertThat(definitions).containsExactlyInAnyOrder("D0", "D1", "U32", "UserId", "NewId",
                "Instant", "Cursor", "Manifest/properties/contentDigest");
        assertThat(input.get("samples").size()).isEqualTo(8);
        Set<String> forbidden = new HashSet<>();
        input.get("forbidden").forEach(s -> forbidden.add(s.get("value").asText()));
        assertThat(forbidden).containsExactlyInAnyOrder("\n", "\r", "\r\n", "\u0085",
                "\u2028", "\u2029", "\t", "\u0000", "\uFEFF", " ", "\u007F");
        assertThat(scalars().filter(s -> !s.valid()).count()).isEqualTo(264);
    }

    @Test
    void feedNeedsRelationalValidationBeyondJsonSchema() throws Exception {
        ObjectNode page = (ObjectNode) responses().filter(c -> c.get("name").asText().equals("completed-feed"))
                .findFirst().orElseThrow().get("value").deepCopy();
        validateFeed(page, 100);
        ObjectNode contradiction = page.deepCopy();
        contradiction.put("done", false);
        assertThat(schema("FeedPage").validate(contradiction)).isEmpty();
        assertThatThrownBy(() -> validateFeed(contradiction, 100)).isInstanceOf(IllegalArgumentException.class);
        contradiction.put("done", true).put("targetThroughSequence", "101");
        assertThatThrownBy(() -> validateFeed(contradiction, 100)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode availability = (ObjectNode) page.get("availability");
        availability.put("checkedFromExclusive", Long.toString(Long.MAX_VALUE));
        availability.put("checkedThroughSequence", Long.toString(Long.MAX_VALUE));
        availability.put("observedThroughSequence", Long.toString(Long.MAX_VALUE));
        page.put("targetThroughSequence", Long.toString(Long.MAX_VALUE));
        page.put("nextAfterSequence", Long.toString(Long.MAX_VALUE));
        page.put("scannedThrough", Long.toString(Long.MAX_VALUE));
        validateFeed(page, Long.MAX_VALUE);
    }

    private static void validateFeed(JsonNode page, long after) {
        long target = decimal(page.get("targetThroughSequence"), false);
        var availability = page.get("availability");
        long head = decimal(availability.get("observedThroughSequence"), false);
        long floor = decimal(availability.get("replayFloor"), false);
        require(after <= target && target <= head && floor <= head);
        long end = after + Math.min(20L, target - after);
        require(decimal(availability.get("checkedFromExclusive"), false) == after);
        require(decimal(availability.get("checkedThroughSequence"), false) == end);
        require(decimal(page.get("scannedThrough"), false) == end);
        require(decimal(page.get("nextAfterSequence"), false) == end);
        require(page.get("done").asBoolean() == (end == target));
        require(page.get("items").size() == end - after);
        if (end > after) require(after >= floor);
        long expected = after;
        for (JsonNode item : page.get("items")) require(decimal(item.get("sequence"), true) == ++expected);
    }

    @ParameterizedTest @MethodSource("states")
    void lifecyclePriorityMatchesBillingExpectations(JsonNode test) {
        String action = test.get("action").asText();
        String result;
        if (action.equals("CREATE") && !test.get("bindingMatches").asBoolean()) result = "OPERATION_CONFLICT";
        else if (!test.get("exists").asBoolean()) result = "SNAPSHOT_NOT_FOUND";
        else if (!test.get("active").asBoolean()) result = "STREAM_REPLACED";
        else if (test.get("expired").asBoolean()) result = action.equals("PAGE") ? "SNAPSHOT_EXPIRED" : "EXPIRED";
        else if (action.equals("PAGE")) result = switch (test.get("status").asText()) {
            case "BUILDING" -> "SNAPSHOT_NOT_READY";
            case "FAILED" -> "SNAPSHOT_FAILED";
            default -> test.get("status").asText();
        };
        else result = test.get("status").asText();
        assertThat(result).as(test.get("name").asText()).isEqualTo(test.get("expected").asText());
    }

    @Test
    void errorsDoNotTreatEvery500AsRetryable() throws Exception {
        var errors = fixture("errors.json");
        Set<String> retryable = Set.of("SNAPSHOT_NOT_READY", "SOURCE_AVAILABILITY_UNCERTAIN", "SOURCE_UNAVAILABLE");
        Set<String> seen = new HashSet<>();
        for (JsonNode error : errors.get("errors")) {
            String code = error.get("code").asText();
            assertThat(seen.add(code)).isTrue();
            assertThat(error.get("retryable").asBoolean()).isEqualTo(retryable.contains(code));
            assertThat(error.get("details").asText()).isEqualTo(switch (code) {
                case "COVERAGE_GAP" -> "required-null-range";
                case "REPLAY_UNAVAILABLE" -> "required-range";
                default -> "omitted";
            });
            if (code.equals("RESPONSE_LIMIT_EXCEEDED")) assertThat(error.get("status").asInt()).isEqualTo(500);
        }
        assertThat(seen).hasSize(15);
        assertThat(errors.at("/infrastructure/bodyContract").asBoolean()).isFalse();
        assertThat(errors.at("/infrastructure/statuses").toString()).isEqualTo("[401,403,429]");
    }

    @Test
    void indexManifestHasNoRemoteGenerationOrAutomaticSourceTtl() throws Exception {
        JsonNode manifest = fixture("identity-indexes.json");
        assertThat(manifest.get("indexes").size()).isEqualTo(16);
        Set<String> seen = new HashSet<>();
        for (JsonNode index : manifest.get("indexes")) {
            assertThat(seen.add(index.get("collection").asText() + "/" + index.get("name").asText())).isTrue();
            assertThat(index.has("expireAfterSeconds")).isFalse();
            assertThat(index.get("sparse").asBoolean()).isFalse();
            assertThat(index.at("/collation/locale").asText()).isEqualTo("simple");
            assertThat(index.get("keys").toString()).doesNotContain("generation", "Generation");
            if (index.get("name").asText().equals("ux_withdrawal_snapshot_operation_r1")) {
                assertThat(index.get("unique").asBoolean()).isTrue();
                assertThat(index.get("keys").toString())
                        .isEqualTo("{\"environment\":1,\"consumer\":1,\"requestOperationId\":1}");
            }
        }
        assertThat(manifest.get("productionInitializer").asBoolean()).isFalse();
    }

    @Test
    void rawBodyBoundsAreIndependentOfSchema() {
        assertThatThrownBy(() -> validateRequest("CREATE", "", "\uFEFF{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validateRequest("CREATE", "", " ".repeat(16385))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receivedFixtureBytesMatchIdentityHashManifest() throws Exception {
        var files = fixture("hashes.json").get("files").fields();
        while (files.hasNext()) {
            var file = files.next();
            try (var in = getClass().getResourceAsStream("/contracts/payment-lifecycle/recovery-r1/" + file.getKey())) {
                String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(Objects.requireNonNull(in).readAllBytes()));
                assertThat(hash).as(file.getKey()).isEqualTo(file.getValue().asText());
            }
        }
    }

    @Test
    void rangeLookupMustFilterBeforeLimitingAndHandleMaxSequence() throws Exception {
        for (JsonNode test : fixture("range-cases.json").get("rangeLookupCases")) {
            var generator = test.get("generator");
            List<long[]> ranges = new ArrayList<>();
            for (int i = 0; i < generator.get("count").asInt(); i++) {
                long from = decimal(generator.get("fromStart"), true)
                        + i * decimal(generator.get("fromStep"), true);
                long to = generator.has("toFixed") ? decimal(generator.get("toFixed"), true)
                        : from + decimal(generator.get("width"), false);
                ranges.add(new long[]{from, to});
            }
            long after = decimal(test.get("after"), false);
            long target = decimal(test.get("target"), false);
            int count = (int) Math.min(20, target - after);
            Set<List<String>> witnesses = new LinkedHashSet<>();
            for (int i = 1; i <= count; i++) {
                long point = after + i;
                ranges.stream().filter(r -> r[0] <= point && point <= r[1])
                        .min(Comparator.<long[]>comparingLong(r -> r[0]).thenComparingLong(r -> r[1]))
                        .ifPresent(r -> witnesses.add(List.of(Long.toString(r[0]), Long.toString(r[1]))));
            }
            assertThat(count).isEqualTo(test.get("expectedLookupCount").asInt());
            assertThat(JSON.<JsonNode>valueToTree(witnesses)).as(test.get("name").asText())
                    .isEqualTo(test.get("expectedWitnesses"));
        }
    }

    @Test
    void onlyContiguousMissingPrefixCanAdvanceReplayFloor() throws Exception {
        for (JsonNode test : fixture("range-cases.json").get("normalizationCases")) {
            long floor = decimal(test.get("floor"), false);
            List<long[]> ranges = new ArrayList<>();
            for (var range : test.get("ranges"))
                ranges.add(new long[]{decimal(range.get(0), true), decimal(range.get(1), true)});
            ranges.sort(Comparator.comparingLong(r -> r[0]));
            List<List<String>> remaining = new ArrayList<>();
            for (long[] range : ranges) {
                if (range[0] <= floor || (floor < Long.MAX_VALUE && range[0] == floor + 1))
                    floor = Math.max(floor, range[1]);
                else remaining.add(List.of(Long.toString(range[0]), Long.toString(range[1])));
            }
            assertThat(Long.toString(floor)).as(test.get("name").asText())
                    .isEqualTo(test.get("expectedFloor").asText());
            assertThat(JSON.<JsonNode>valueToTree(remaining)).isEqualTo(test.get("expectedRanges"));
        }
    }
}
