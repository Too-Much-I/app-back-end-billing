package web.tosunsaeng.billing.domain.ownerrebind.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.global.exception.InternalApiException;

@Component
class OwnerRebindJsonSupport {

    private static final long MAX_REVISION = 9_007_199_254_740_991L;

    private final JsonFactory jsonFactory = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

    JsonNode parse(byte[] payload, int maxBytes) {
        if (payload == null || payload.length == 0 || payload.length > maxBytes) {
            throw InternalApiException.invalidRequest();
        }
        try (JsonParser parser = jsonFactory.createParser(payload)) {
            JsonNode root = objectMapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                throw InternalApiException.invalidRequest();
            }
            return root;
        } catch (InternalApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    void requireExactFields(JsonNode root, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            actual.add(names.next());
        }
        if (!actual.equals(expected)) {
            throw InternalApiException.invalidRequest();
        }
    }

    String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw InternalApiException.invalidRequest();
        }
        return value.textValue();
    }

    int requiredInt(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw InternalApiException.invalidRequest();
        }
        return value.intValue();
    }

    long requiredRevision(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw InternalApiException.invalidRequest();
        }
        long revision = value.longValue();
        if (revision < 1 || revision > MAX_REVISION) {
            throw InternalApiException.invalidRequest();
        }
        return revision;
    }

    String canonicalUuid(JsonNode root, String field, boolean requireVersionFour) {
        String value = requiredText(root, field);
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value) || (requireVersionFour && uuid.version() != 4)) {
                throw InternalApiException.invalidRequest();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    Instant requiredInstant(JsonNode root, String field) {
        String value = requiredText(root, field);
        if (!value.endsWith("Z")) {
            throw InternalApiException.invalidRequest();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    String phoneDigest(
            String eventId,
            String eventType,
            int schemaVersion,
            String producer,
            String consumerScopeId,
            Instant occurredAt,
            String sourceUserId,
            String targetUserId,
            String lifecycleReason,
            long sourceBindingRevision,
            long targetBindingRevision
    ) {
        return digest(generator -> {
            generator.writeStartObject();
            generator.writeStringField("eventId", eventId);
            generator.writeStringField("eventType", eventType);
            generator.writeNumberField("schemaVersion", schemaVersion);
            generator.writeStringField("producer", producer);
            generator.writeStringField("consumerScopeId", consumerScopeId);
            generator.writeStringField("occurredAt", occurredAt.toString());
            generator.writeStringField("sourceUserId", sourceUserId);
            generator.writeStringField("targetUserId", targetUserId);
            generator.writeStringField("lifecycleReason", lifecycleReason);
            generator.writeNumberField("sourceBindingRevision", sourceBindingRevision);
            generator.writeNumberField("targetBindingRevision", targetBindingRevision);
            generator.writeEndObject();
        });
    }

    String userMergedDigest(
            String eventId,
            int schemaVersion,
            String sourceUserId,
            String targetUserId,
            Instant occurredAt
    ) {
        return digest(generator -> {
            generator.writeStartObject();
            generator.writeStringField("eventId", eventId);
            generator.writeNumberField("schemaVersion", schemaVersion);
            generator.writeStringField("sourceUserId", sourceUserId);
            generator.writeStringField("targetUserId", targetUserId);
            generator.writeStringField("occurredAt", occurredAt.toString());
            generator.writeEndObject();
        });
    }

    private String digest(JsonWriter writer) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                writer.write(generator);
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(output.toByteArray())
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create owner rebind digest.", exception);
        }
    }

    @FunctionalInterface
    private interface JsonWriter {
        void write(JsonGenerator generator) throws IOException;
    }
}
