package web.tosunsaeng.billing.domain.payment.recovery;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.api.PaymentWithdrawalDecoder;
import web.tosunsaeng.billing.domain.payment.domain.*;

/**
 * recovery-r1 / scalar-end-1. Not a transport, worker or authorization boundary.
 * Transport must bound reads before allocating the body and never follow redirects.
 */
public final class RecoveryPayloadDecoder {
    public static final int MAX_BYTES = 16384;
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final PaymentWithdrawalDecoder events;
    public RecoveryPayloadDecoder(Clock clock) { events = new PaymentWithdrawalDecoder(clock); }

    public enum ErrorCode {
        INVALID_REQUEST(400, false, "Invalid recovery request."),
        SNAPSHOT_NOT_FOUND(404, false, "Snapshot not found."),
        REQUEST_TOO_LARGE(413, false, "Recovery request exceeds the byte limit."),
        UNSUPPORTED_CONTRACT(422, false, "Unsupported recovery contract."),
        OPERATION_CONFLICT(409, false, "Operation is bound to a different request."),
        STREAM_REPLACED(409, false, "Withdrawal stream has been replaced."),
        SNAPSHOT_NOT_READY(409, true, "Snapshot is still building."),
        SNAPSHOT_FAILED(409, false, "Snapshot build failed. Check snapshot status."),
        COVERAGE_GAP(409, false, "Required withdrawal source is missing."),
        REPLAY_UNAVAILABLE(410, false, "Required withdrawal source is no longer replayable."),
        SNAPSHOT_EXPIRED(410, false, "Snapshot has expired."),
        CURSOR_EXPIRED(410, false, "Recovery cursor has expired."),
        SOURCE_AVAILABILITY_UNCERTAIN(503, true, "Withdrawal source availability is uncertain."),
        SOURCE_UNAVAILABLE(503, true, "Withdrawal source is temporarily unavailable."),
        RESPONSE_LIMIT_EXCEEDED(500, false, "Recovery response exceeds the byte limit.");
        public final int status;
        public final boolean retryable;
        private final String message;
        ErrorCode(int status, boolean retryable, String message) {
            this.status = status; this.retryable = retryable; this.message = message;
        }
    }
    public record SourceFailure(String streamId, long replayFloor, long observedThroughSequence,
                                long missingSequence, Long fromSequence, Long toSequence, Instant observedAt) {}
    public record RemoteError(ErrorCode code, SourceFailure details) {}

    /** Only authenticated application errors. Infrastructure 401/403/429 need not carry JSON. */
    public RemoteError error(byte[] body, int httpStatus, String expectedStreamId, long after, Long target) {
        try {
            JsonNode root = parse(body);
            ErrorCode code = ErrorCode.valueOf(text(root.get("code")));
            boolean source = code == ErrorCode.COVERAGE_GAP || code == ErrorCode.REPLAY_UNAVAILABLE;
            fields(root, source ? Set.of("code", "message", "retryable", "correlationId", "details")
                    : Set.of("code", "message", "retryable", "correlationId"));
            check(httpStatus == code.status && bool(root.get("retryable")) == code.retryable);
            check(code.message.equals(text(root.get("message"))));
            uuid(root.get("correlationId"), true);
            SourceFailure details = null;
            if (source) {
                check(after >= 0);
                var d = root.get("details");
                fields(d, Set.of("streamId", "replayFloor", "observedThroughSequence",
                        "missingSequence", "unavailableRange", "observedAt"));
                String stream = uuid(d.get("streamId"), true);
                check(stream.equals(expectedStreamId));
                long floor = decimal(d.get("replayFloor"), false), head = decimal(d.get("observedThroughSequence"), false);
                long missing = decimal(d.get("missingSequence"), true);
                long bound = target == null ? head : target;
                check(floor <= head && after <= bound && bound <= head);
                long end = after + Math.min(20, bound - after);
                check(missing > after && missing <= end);
                Long from = null, to = null;
                if (code == ErrorCode.COVERAGE_GAP) {
                    check(d.get("unavailableRange").isNull() && missing > floor);
                } else {
                    var range = d.get("unavailableRange");
                    fields(range, Set.of("fromSequence", "toSequence"));
                    from = decimal(range.get("fromSequence"), true);
                    to = decimal(range.get("toSequence"), true);
                    check(from <= missing && missing <= to && to <= head);
                }
                details = new SourceFailure(stream, floor, head, missing, from, to, instant(d.get("observedAt")));
            }
            return new RemoteError(code, details);
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }

    public record SnapshotPage(List<WithdrawalFact> items, String nextCursor, boolean done) {
        public SnapshotPage { items = List.copyOf(items); }
        @Override public String toString() { return "SnapshotPage[REDACTED]"; }
    }
    public record FeedItem(long sequence, PaymentWithdrawal event) {
        @Override public String toString() { return "FeedItem[REDACTED]"; }
    }
    public record Availability(String streamId, long replayFloor, long observedThroughSequence,
                               long checkedFromExclusive, long checkedThroughSequence, Instant observedAt) {}
    public record FeedPage(List<FeedItem> items, String scanCursor, long targetThroughSequence,
                           long nextAfterSequence, long scannedThrough, boolean done, Availability availability) {
        public FeedPage { items = List.copyOf(items); }
        @Override public String toString() { return "FeedPage[REDACTED]"; }
    }
    public record Gap(String kind, Long fromSequence, Long toSequence) {}
    public record Manifest(String snapshotId, String streamId, long seconds, long increment, long highWater,
                           long itemCount, long pageCount, String contentDigest, String legacyCoverage,
                           List<Gap> knownGaps, Instant expiresAt) {
        public Manifest { knownGaps = List.copyOf(knownGaps); }
        @Override public String toString() { return "Manifest[REDACTED]"; }
    }
    public record SnapshotStatus(String snapshotId, String status, Manifest manifest, String failureCode) {
        @Override public String toString() { return "SnapshotStatus[REDACTED]"; }
    }

    public SnapshotStatus snapshotStatus(byte[] body, String expectedSnapshotId, String expectedStreamId) {
        try {
            JsonNode root = parse(body);
            String status = text(root.get("status"));
            fields(root, switch (status) {
                case "READY" -> Set.of("snapshotId", "status", "manifest");
                case "FAILED" -> Set.of("snapshotId", "status", "failureCode");
                case "BUILDING", "EXPIRED" -> Set.of("snapshotId", "status");
                default -> throw new RecoveryContractException();
            });
            String id = uuid(root.get("snapshotId"), true);
            if (expectedSnapshotId != null) check(id.equals(expectedSnapshotId));
            Manifest manifest = null;
            String failure = null;
            if (status.equals("READY")) {
                manifest = manifest(root.get("manifest"));
                check(manifest.snapshotId().equals(id) && manifest.streamId().equals(expectedStreamId));
            }
            if (status.equals("FAILED")) {
                failure = text(root.get("failureCode"));
                check(Set.of("SNAPSHOT_HISTORY_LOST", "SNAPSHOT_BUILD_TIMEOUT", "SNAPSHOT_BUILD_FAILED").contains(failure));
            }
            return new SnapshotStatus(id, status, manifest, failure);
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }

    public SnapshotPage snapshotPage(byte[] body) {
        try {
            JsonNode root = parse(body);
            fields(root, Set.of("items", "nextCursor", "done"));
            List<WithdrawalFact> items = new ArrayList<>();
            for (JsonNode row : array(root.get("items"), 20)) {
                fields(row, Set.of("userId", "withdrawnAt"));
                String id = uuid(row.get("userId"), false);
                check(items.isEmpty() || items.getLast().userId().compareTo(id) < 0);
                items.add(new WithdrawalFact(id, instant(row.get("withdrawnAt"))));
            }
            String cursor = nullableCursor(root.get("nextCursor"));
            boolean done = bool(root.get("done"));
            check(done == (cursor == null));
            check(done || items.size() == 20);
            return new SnapshotPage(items, cursor, done);
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }

    /** expectedTarget is null only on the first request of a new scan. */
    public FeedPage feedPage(byte[] body, String expectedStreamId, long after, Long expectedTarget) {
        try {
            check(after >= 0);
            JsonNode root = parse(body);
            fields(root, Set.of("items", "scanCursor", "targetThroughSequence", "nextAfterSequence",
                    "scannedThrough", "done", "availability"));
            long target = decimal(root.get("targetThroughSequence"), false);
            check(target >= after && (expectedTarget == null || target == expectedTarget));
            long end = after + Math.min(20L, target - after);
            long next = decimal(root.get("nextAfterSequence"), false);
            long scanned = decimal(root.get("scannedThrough"), false);
            boolean done = bool(root.get("done"));
            check(next == end && scanned == end && done == (end == target));
            String cursor = cursor(root.get("scanCursor"));
            var a = root.get("availability");
            fields(a, Set.of("streamId", "replayFloor", "observedThroughSequence",
                    "checkedFromExclusive", "checkedThroughSequence", "observedAt"));
            var availability = new Availability(uuid(a.get("streamId"), true),
                    decimal(a.get("replayFloor"), false), decimal(a.get("observedThroughSequence"), false),
                    decimal(a.get("checkedFromExclusive"), false), decimal(a.get("checkedThroughSequence"), false),
                    instant(a.get("observedAt")));
            check(availability.streamId().equals(expectedStreamId));
            check(availability.observedThroughSequence() >= target
                    && availability.replayFloor() <= availability.observedThroughSequence());
            check(availability.checkedFromExclusive() == after && availability.checkedThroughSequence() == end);
            check(end == after || after >= availability.replayFloor());
            var rows = array(root.get("items"), 20);
            check(rows.size() == end - after);
            List<FeedItem> items = new ArrayList<>();
            Set<String> eventIds = new HashSet<>();
            long sequence = after;
            for (JsonNode row : rows) {
                fields(row, Set.of("sequence", "event"));
                long value = decimal(row.get("sequence"), true);
                check(value == ++sequence);
                // Additional canonical-time requirement of recovery-r1; push normalization remains unchanged.
                instant(row.path("event").get("withdrawnAt"));
                PaymentWithdrawal event = events.decode(json.writeValueAsBytes(row.get("event")));
                check(eventIds.add(event.eventId()));
                items.add(new FeedItem(value, event));
            }
            return new FeedPage(items, cursor, target, next, scanned, done, availability);
        } catch (Exception invalid) { throw new RecoveryContractException(); }
    }

    private Manifest manifest(JsonNode root) {
        fields(root, Set.of("snapshotId", "streamId", "T", "H", "itemCount", "pageCount",
                "contentDigest", "legacyCoverage", "knownGaps", "expiresAt"));
        var t = root.get("T");
        fields(t, Set.of("seconds", "increment"));
        long seconds = decimal(t.get("seconds"), false), increment = decimal(t.get("increment"), false);
        check(seconds <= 4294967295L && increment <= 4294967295L);
        long high = decimal(root.get("H"), false);
        long count = decimal(root.get("itemCount"), false), pages = decimal(root.get("pageCount"), true);
        check(pages == Math.max(1, count / 20 + (count % 20 == 0 ? 0 : 1)));
        String digest = text(root.get("contentDigest"));
        check(digest.matches("[0-9a-f]{64}"));
        if (count == 0) check(digest.equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        String coverage = text(root.get("legacyCoverage"));
        check(Set.of("COMPLETE", "PARTIAL", "UNKNOWN").contains(coverage));
        List<Gap> gaps = new ArrayList<>();
        // Body bound, not an invented protocol-level count limit.
        for (var gap : array(root.get("knownGaps"), MAX_BYTES)) {
            fields(gap, Set.of("kind", "fromSequence", "toSequence"));
            String kind = text(gap.get("kind"));
            if (kind.equals("LEGACY_HISTORY")) {
                check(gap.get("fromSequence").isNull() && gap.get("toSequence").isNull());
                gaps.add(new Gap(kind, null, null));
            } else {
                check(kind.equals("SOURCE_RANGE"));
                long from = decimal(gap.get("fromSequence"), true), to = decimal(gap.get("toSequence"), true);
                check(from <= to && to <= high);
                gaps.add(new Gap(kind, from, to));
            }
        }
        return new Manifest(uuid(root.get("snapshotId"), true), uuid(root.get("streamId"), true),
                seconds, increment, high, count, pages, digest, coverage, gaps, instant(root.get("expiresAt")));
    }

    private JsonNode parse(byte[] body) throws Exception {
        check(body != null && body.length > 0 && body.length <= MAX_BYTES);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
        check(!text.startsWith("\uFEFF"));
        try (var parser = json.createParser(text)) {
            JsonNode root = json.readTree(parser);
            check(root != null && root.isObject() && parser.nextToken() == null);
            return root;
        }
    }
    private static void fields(JsonNode node, Set<String> expected) {
        check(node != null && node.isObject());
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        check(actual.equals(expected));
    }
    private static JsonNode array(JsonNode node, int max) {
        check(node != null && node.isArray() && node.size() <= max);
        return node;
    }
    private static String text(JsonNode node) {
        check(node != null && node.isTextual());
        return node.textValue();
    }
    private static boolean bool(JsonNode node) {
        check(node != null && node.isBoolean());
        return node.booleanValue();
    }
    private static long decimal(JsonNode node, boolean positive) {
        String value = text(node);
        check(value.matches(positive ? "[1-9][0-9]{0,18}" : "0|[1-9][0-9]{0,18}"));
        return Long.parseLong(value);
    }
    private static String uuid(JsonNode node, boolean v4) {
        String value = text(node);
        UUID id = UUID.fromString(value);
        check(id.toString().equals(value) && (!v4 || (id.version() == 4 && id.variant() == 2)));
        return value;
    }
    private static Instant instant(JsonNode node) {
        String value = text(node);
        Instant parsed = Instant.parse(value);
        check(parsed.toString().equals(value));
        return parsed;
    }
    private static String nullableCursor(JsonNode node) {
        check(node != null);
        return node.isNull() ? null : cursor(node);
    }
    private static String cursor(JsonNode node) {
        String value = text(node);
        check(value.matches("[!-~]{1,2048}"));
        return value;
    }
    private static void check(boolean condition) { if (!condition) throw new RecoveryContractException(); }
}
