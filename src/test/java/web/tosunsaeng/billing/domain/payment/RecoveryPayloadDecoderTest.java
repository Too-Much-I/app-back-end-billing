package web.tosunsaeng.billing.domain.payment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryPayloadDecoderTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String ID = "00000000-0000-4000-8000-000000000001";
    static final String SNAPSHOT = "00000000-0000-4000-8000-000000000002";
    static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
    final RecoveryPayloadDecoder decoder = new RecoveryPayloadDecoder(Clock.fixed(NOW, ZoneOffset.UTC));
    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static JsonNode fixture(String name) throws Exception {
        try (var in = RecoveryPayloadDecoderTest.class.getResourceAsStream(
                "/contracts/payment-lifecycle/recovery-r1/" + name)) {
            return JSON.readTree(Objects.requireNonNull(in));
        }
    }
    static Stream<JsonNode> responseCases() throws Exception {
        List<JsonNode> values = new ArrayList<>();
        fixture("response-cases.json").get("cases").forEach(c -> {
            if (Set.of("SnapshotStatus", "SnapshotPage", "FeedPage").contains(c.get("definition").asText())) values.add(c);
        });
        return values.stream();
    }
    @ParameterizedTest @MethodSource("responseCases")
    void productionDecoderAcceptsAndRejectsSharedSuccessShapes(JsonNode test) throws Exception {
        byte[] body = JSON.writeValueAsBytes(test.get("value"));
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call = () -> {
            switch (test.get("definition").asText()) {
                case "SnapshotStatus" -> decoder.snapshotStatus(body, SNAPSHOT, ID);
                case "SnapshotPage" -> decoder.snapshotPage(body);
                case "FeedPage" -> decoder.feedPage(body, ID, 100L, 100L);
                default -> throw new AssertionError();
            }
        };
        if (test.get("valid").asBoolean()) assertThatCode(call).as(test.get("name").asText()).doesNotThrowAnyException();
        else assertThatThrownBy(call).as(test.get("name").asText()).isInstanceOf(RecoveryContractException.class);
    }
    @Test void rawJsonStrictnessAndSafeErrorMessages() {
        for (byte[] invalid : List.of(bytes("\uFEFF{}"), new byte[]{(byte) 0xc3, 0x28},
                bytes("{\"items\":[],\"items\":[],\"nextCursor\":null,\"done\":true}"),
                bytes("{\"items\":[],\"nextCursor\":null,\"done\":true}{}"),
                bytes("{\"items\":[],\"nextCursor\":null,\"done\":\"true\"}"),
                bytes("{\"items\":[],\"done\":true}"), bytes(" ".repeat(16385)))) {
            assertThatThrownBy(() -> decoder.snapshotPage(invalid))
                    .isInstanceOfSatisfying(RecoveryContractException.class, error -> {
                        assertThat(error.getMessage()).isEqualTo("Invalid withdrawal recovery contract");
                        assertThat(error.getCause()).isNull();
                    });
        }
    }
    ObjectNode feed(long after, int count, long target) {
        ObjectNode page = JSON.createObjectNode();
        var items = page.putArray("items");
        for (int i = 0; i < count; i++) {
            var item = items.addObject().put("sequence", Long.toString(after + i + 1));
            item.putObject("event").put("eventId", String.format("11111111-1111-1111-8111-%012d", i))
                    .put("schemaVersion", 1).put("userId", String.format("22222222-2222-1222-8222-%012d", i))
                    .put("withdrawnAt", "2026-10-12T00:00:00.000000001Z");
        }
        page.put("scanCursor", "synthetic.cursor").put("targetThroughSequence", Long.toString(target))
                .put("nextAfterSequence", Long.toString(after + count)).put("scannedThrough", Long.toString(after + count))
                .put("done", after + count == target);
        page.putObject("availability").put("streamId", ID).put("replayFloor", "0")
                .put("observedThroughSequence", Long.toString(target)).put("checkedFromExclusive", Long.toString(after))
                .put("checkedThroughSequence", Long.toString(after + count)).put("observedAt", NOW.toString());
        return page;
    }
    @Test void fullFeedPreservesLegacyUuidAndNanosAndDetectsContradictions() throws Exception {
        ObjectNode page = feed(0, 20, 25);
        var decoded = decoder.feedPage(JSON.writeValueAsBytes(page), ID, 0, null);
        assertThat(decoded.items()).hasSize(20);
        assertThat(decoded.items().getFirst().event().withdrawnAt().getNano()).isEqualTo(1);
        assertThat(decoded.toString()).doesNotContain(ID, "synthetic.cursor");
        assertThatThrownBy(() -> decoder.feedPage(JSON.writeValueAsBytes(page), ID, 0, 26L))
                .isInstanceOf(RecoveryContractException.class);
        for (String field : List.of("nextAfterSequence", "scannedThrough")) {
            ObjectNode bad = page.deepCopy().put(field, "19");
            assertThatThrownBy(() -> decoder.feedPage(JSON.writeValueAsBytes(bad), ID, 0, null))
                    .isInstanceOf(RecoveryContractException.class);
        }
        ((ObjectNode) page.get("items").get(0).get("event")).put("schemaVersion", 1.0);
        assertThatThrownBy(() -> decoder.feedPage(JSON.writeValueAsBytes(page), ID, 0, null))
                .isInstanceOf(RecoveryContractException.class);
    }
    @Test void gapsDuplicateEventsAndEmptyPrefixNeverAdvance() throws Exception {
        for (String mode : List.of("hole", "duplicate", "empty", "floor")) {
            ObjectNode page = feed(0, 2, 2);
            switch (mode) {
                case "hole" -> ((ObjectNode) page.get("items").get(1)).put("sequence", "3");
                case "duplicate" -> ((ObjectNode) page.get("items").get(1).get("event"))
                        .put("eventId", page.get("items").get(0).get("event").get("eventId").asText());
                case "empty" -> page.putArray("items");
                case "floor" -> ((ObjectNode) page.get("availability")).put("replayFloor", "1");
            }
            assertThatThrownBy(() -> decoder.feedPage(JSON.writeValueAsBytes(page), ID, 0, null))
                    .as(mode).isInstanceOf(RecoveryContractException.class);
        }
        assertThat(decoder.feedPage(JSON.writeValueAsBytes(feed(Long.MAX_VALUE, 0, Long.MAX_VALUE)),
                ID, Long.MAX_VALUE, Long.MAX_VALUE).done()).isTrue();
    }
    @Test void snapshotRejectsNonCanonicalTimeDuplicateUsersAndCursorContradiction() throws Exception {
        ObjectNode page = JSON.createObjectNode().put("done", true).putNull("nextCursor");
        page.putArray("items").addObject().put("userId", ID).put("withdrawnAt", "2026-10-12T00:00:00Z");
        assertThat(decoder.snapshotPage(JSON.writeValueAsBytes(page)).items()).hasSize(1);
        for (String time : List.of("2026-10-12T00:00:00.000Z", "2026-10-12T00:00:00+00:00",
                "2026-02-30T00:00:00Z", "2026-10-12T00:00:00Z\n")) {
            ((ObjectNode) page.get("items").get(0)).put("withdrawnAt", time);
            assertThatThrownBy(() -> decoder.snapshotPage(JSON.writeValueAsBytes(page)))
                    .isInstanceOf(RecoveryContractException.class);
        }
        ((ObjectNode) page.get("items").get(0)).put("withdrawnAt", "2026-10-12T00:00:00Z");
        ((com.fasterxml.jackson.databind.node.ArrayNode) page.get("items")).add(page.get("items").get(0).deepCopy());
        assertThatThrownBy(() -> decoder.snapshotPage(JSON.writeValueAsBytes(page))).isInstanceOf(RecoveryContractException.class);
        page.putArray("items"); page.put("done", false);
        assertThatThrownBy(() -> decoder.snapshotPage(JSON.writeValueAsBytes(page))).isInstanceOf(RecoveryContractException.class);
    }
    @Test void exactErrorsKeepNonRetryable500AndValidateMissingRange() throws Exception {
        for (var row : fixture("errors.json").get("errors")) {
            var body = JSON.createObjectNode().put("code", row.get("code").asText())
                    .put("message", row.get("message").asText()).put("retryable", row.get("retryable").asBoolean())
                    .put("correlationId", ID);
            if (!row.get("details").asText().equals("omitted")) {
                var detail = body.putObject("details").put("streamId", ID).put("replayFloor", "0")
                        .put("observedThroughSequence", "30").put("missingSequence", "1").put("observedAt", NOW.toString());
                if (row.get("code").asText().equals("COVERAGE_GAP")) detail.putNull("unavailableRange");
                else detail.putObject("unavailableRange").put("fromSequence", "1").put("toSequence", "30");
            }
            var result = decoder.error(JSON.writeValueAsBytes(body), row.get("status").asInt(), ID, 0, 20L);
            assertThat(result.code().retryable).isEqualTo(row.get("retryable").asBoolean());
            body.put("message", "untrusted details");
            assertThatThrownBy(() -> decoder.error(JSON.writeValueAsBytes(body), row.get("status").asInt(), ID, 0, 20L))
                    .isInstanceOf(RecoveryContractException.class);
        }
    }
}
