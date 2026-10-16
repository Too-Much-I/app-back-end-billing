package web.tosunsaeng.billing.domain.examdeletion;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import web.tosunsaeng.billing.global.exception.InternalApiException;

/** Frozen deletion R2 wire boundary. Not a Spring endpoint or an authorization boundary. */
public final class DeletionRequestDecoder {
    public static final int MAX_BYTES = 16_384;
    private static final List<String> FIELDS = List.of("eventId", "schemaVersion", "userId",
            "deletionOperationId", "attemptGroupId", "sessionId", "deletedAt");
    private static final DateTimeFormatter MILLIS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private final JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper json = new ObjectMapper(factory);

    public Request decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw InternalApiException.invalidRequest();
        if (bytes.length > MAX_BYTES) throw new InternalApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                "INVALID_REQUEST", "The request is invalid.", false);
        try {
            // Decode explicitly: Jackson byte autodetection must not admit UTF-16/32 or repair malformed UTF-8.
            String input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root;
            try (JsonParser parser = factory.createParser(input)) {
                root = json.readTree(parser);
                if (root == null || !root.isObject() || parser.nextToken() != null) throw InternalApiException.invalidRequest();
            }
            var actual = new HashSet<String>();
            root.fieldNames().forEachRemaining(actual::add);
            if (!actual.equals(new HashSet<>(FIELDS))) throw InternalApiException.invalidRequest();
            JsonNode schema = root.get("schemaVersion");
            if (!schema.isIntegralNumber()) throw InternalApiException.invalidRequest();
            // Validate all syntax before classifying a syntactically valid unsupported schema.
            String event = uuid(text(root, "eventId"), true);
            String user = uuid(text(root, "userId"), false);
            String operation = uuid(text(root, "deletionOperationId"), true);
            String group = uuid(text(root, "attemptGroupId"), true);
            String session = text(root, "sessionId");
            if (session.isEmpty() || session.length() > 128 || !session.equals(session.strip())
                    || session.chars().anyMatch(Character::isISOControl)) throw InternalApiException.invalidRequest();
            String timestamp = text(root, "deletedAt");
            if (!timestamp.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"))
                throw InternalApiException.invalidRequest();
            Instant deletedAt = Instant.parse(timestamp);
            if (!MILLIS.format(deletedAt).equals(timestamp)) throw InternalApiException.invalidRequest();
            if (!schema.bigIntegerValue().equals(java.math.BigInteger.ONE))
                throw new InternalApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_CONTRACT",
                        "The contract is not supported.", false);
            byte[] canonical = canonical(root);
            return new Request(event, user, operation, group, session, deletedAt, canonical, digest(canonical));
        } catch (InternalApiException expected) {
            throw expected;
        } catch (IOException | IllegalArgumentException exception) {
            throw InternalApiException.invalidRequest();
        } catch (java.time.DateTimeException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (!value.isTextual()) throw InternalApiException.invalidRequest();
        String text = value.textValue();
        validScalars(text);
        return text;
    }

    private static String uuid(String value, boolean v4) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value) || v4 && (id.version() != 4 || id.variant() != 2))
            throw InternalApiException.invalidRequest();
        return value;
    }

    // Separate serializer test seam: control-character examples are not valid session IDs.
    static byte[] canonical(JsonNode root) {
        StringBuilder out = new StringBuilder("{");
        for (String field : FIELDS) {
            if (out.length() > 1) out.append(',');
            quote(out, field);
            out.append(':');
            if (field.equals("schemaVersion")) out.append('1');
            else quote(out, text(root, field));
        }
        return out.append('}').toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void quote(StringBuilder out, String text) {
        validScalars(text);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch < 0x20) out.append("\\u00").append("0123456789abcdef".charAt(ch >>> 4))
                    .append("0123456789abcdef".charAt(ch & 15));
            else out.append(ch);
        }
        out.append('"');
    }

    private static void validScalars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw InternalApiException.invalidRequest();
            } else if (Character.isLowSurrogate(ch)) throw InternalApiException.invalidRequest();
        }
    }

    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    /** Only this decoder can construct a validated request; no body-bearing generated toString. */
    public static final class Request {
        private final String eventId, userId, deletionOperationId, attemptGroupId, sessionId, payloadDigest;
        private final Instant deletedAt;
        private final byte[] canonical;
        private Request(String eventId, String userId, String operation, String group, String session,
                Instant deletedAt, byte[] canonical, String digest) {
            this.eventId = eventId; this.userId = userId; this.deletionOperationId = operation;
            this.attemptGroupId = group; this.sessionId = session; this.deletedAt = deletedAt;
            this.canonical = canonical.clone(); this.payloadDigest = digest;
        }
        public String eventId() { return eventId; }
        public int schemaVersion() { return 1; }
        public String userId() { return userId; }
        public String deletionOperationId() { return deletionOperationId; }
        public String attemptGroupId() { return attemptGroupId; }
        public String sessionId() { return sessionId; }
        public Instant deletedAt() { return deletedAt; }
        public String payloadDigest() { return payloadDigest; }
        public byte[] canonicalBytes() { return canonical.clone(); }
        @Override public String toString() { return "DeletionRequest[REDACTED]"; }
    }
}
