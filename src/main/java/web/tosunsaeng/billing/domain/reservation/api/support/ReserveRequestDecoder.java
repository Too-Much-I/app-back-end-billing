package web.tosunsaeng.billing.domain.reservation.api.support;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;

@Component
public class ReserveRequestDecoder {

    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final Set<String> BASE_FIELDS = Set.of("userId", "sessionId", "mockExamId");
    private static final Set<String> CONTINUATION_FIELDS = Set.of(
            "userId", "sessionId", "mockExamId", "continuationReason",
            "continuationId", "expectedAttemptGroupId"
    );

    private final JsonFactory jsonFactory;
    private final ObjectMapper objectMapper;

    public ReserveRequestDecoder() {
        this.jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.objectMapper = new ObjectMapper(jsonFactory);
    }

    public ReserveRequest decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }
        JsonNode root = parse(payload);
        if (!root.isObject()) {
            throw InternalApiException.invalidRequest();
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(BASE_FIELDS) && !actual.equals(CONTINUATION_FIELDS)) {
            throw InternalApiException.invalidRequest();
        }
        String userId = canonicalUuid(requiredText(root, "userId"));
        String sessionId = opaqueToken(root, "sessionId");
        String mockExamId = opaqueToken(root, "mockExamId");
        if (actual.equals(BASE_FIELDS)) {
            return new ReserveRequest(userId, sessionId, mockExamId);
        }
        if (!Reservation.ContinuationReason.PHONE_REJOIN.name().equals(
                requiredText(root, "continuationReason")
        )) {
            throw InternalApiException.invalidRequest();
        }
        return new ReserveRequest(
                userId, sessionId, mockExamId,
                Reservation.ContinuationReason.PHONE_REJOIN,
                canonicalUuidV4(requiredText(root, "continuationId")),
                opaqueToken(root, "expectedAttemptGroupId")
        );
    }

    private JsonNode parse(byte[] payload) {
        try (JsonParser parser = jsonFactory.createParser(payload)) {
            JsonNode root = objectMapper.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw InternalApiException.invalidRequest();
            }
            return root;
        } catch (InternalApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isEmpty()) {
            throw InternalApiException.invalidRequest();
        }
        return node.textValue();
    }

    private static String canonicalUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw InternalApiException.invalidRequest();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    private static String canonicalUuidV4(String value) {
        String canonical = canonicalUuid(value);
        if (UUID.fromString(canonical).version() != 4) {
            throw InternalApiException.invalidRequest();
        }
        return canonical;
    }

    private static String opaqueToken(JsonNode root, String field) {
        String value = requiredText(root, field);
        if (value.length() > 128 || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw InternalApiException.invalidRequest();
        }
        return value;
    }
}
