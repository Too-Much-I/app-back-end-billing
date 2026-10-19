package web.tosunsaeng.billing.domain.reservation.api.support;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import web.tosunsaeng.billing.global.exception.InternalApiException;

final class LifecycleJsonDecoder {

    static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final Pattern UTC_MILLIS = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
    );

    private final JsonFactory jsonFactory = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

    JsonNode object(byte[] payload, Set<String> fields) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }
        try (JsonParser parser = jsonFactory.createParser(payload)) {
            JsonNode root = objectMapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                throw InternalApiException.invalidRequest();
            }
            Set<String> actual = new HashSet<>();
            Iterator<String> names = root.fieldNames();
            names.forEachRemaining(actual::add);
            if (!actual.equals(fields)) {
                throw InternalApiException.invalidRequest();
            }
            return root;
        } catch (InternalApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    String uuid(JsonNode root, String field) {
        String value = text(root, field);
        try {
            UUID uuid = UUID.fromString(value);
            if (uuid.version() != 4 || !uuid.toString().equals(value)) {
                throw InternalApiException.invalidRequest();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    String opaque(JsonNode root, String field) {
        String value = text(root, field);
        if (value.length() > 128 || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw InternalApiException.invalidRequest();
        }
        return value;
    }

    Instant utcMillis(JsonNode root, String field) {
        String value = text(root, field);
        if (!UTC_MILLIS.matcher(value).matches()) {
            throw InternalApiException.invalidRequest();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isEmpty()
                || !node.textValue().equals(node.textValue().strip())) {
            throw InternalApiException.invalidRequest();
        }
        return node.textValue();
    }
}
