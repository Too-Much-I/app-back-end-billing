package web.tosunsaeng.billing.domain.attempt.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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

import web.tosunsaeng.billing.domain.attempt.config.AttemptGroupEventProperties;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupFailureCode;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupCompletionEvidence;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.exception.AttemptGroupEventException;
import web.tosunsaeng.billing.global.exception.InternalApiException;

@Component
public class AttemptGroupEventDecoder {

    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final String EVENT_TYPE = "AttemptGroupStatusChanged";
    private static final int SCHEMA_VERSION = 1;
    private static final String PRODUCER = "learning-core";
    private static final int EVIDENCE_VERSION = 1;
    private static final DateTimeFormatter MILLIS_INSTANT = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();
    private static final Set<String> BASE_FIELDS = Set.of(
            "eventId", "eventType", "schemaVersion", "producer", "occurredAt",
            "userId", "attemptGroupId", "sessionId", "targetStatus"
    );
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "eventId", "eventType", "schemaVersion", "producer", "occurredAt",
            "userId", "attemptGroupId", "sessionId", "targetStatus",
            "evidence", "failureCode"
    );
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "requiredFeedbackQueryable", "validScoreQueryable",
            "summaryQueryable", "evidenceVersion"
    );

    private final AttemptGroupEventProperties properties;
    private final Clock clock;
    private final JsonFactory jsonFactory;
    private final ObjectMapper objectMapper;

    public AttemptGroupEventDecoder(AttemptGroupEventProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.objectMapper = new ObjectMapper(jsonFactory);
    }

    public AttemptGroupStatusEvent decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }

        JsonNode root = parse(payload);
        if (!root.isObject()) {
            throw InternalApiException.invalidRequest();
        }
        rejectUnknownFields(root);

        String eventType = requiredText(root, "eventType");
        if (!EVENT_TYPE.equals(eventType)) {
            throw AttemptGroupEventException.unsupportedContract();
        }
        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw AttemptGroupEventException.unsupportedContract();
        }
        String producer = requiredText(root, "producer");
        if (!PRODUCER.equals(producer)) {
            throw AttemptGroupEventException.unsupportedContract();
        }
        AttemptGroupEventTarget target = AttemptGroupEventTarget.fromWireValue(
                requiredText(root, "targetStatus")
        ).orElseThrow(AttemptGroupEventException::unsupportedContract);
        validateExactFields(root, target);

        String eventId = canonicalUuid(requiredText(root, "eventId"), true);
        String userId = canonicalUuid(requiredText(root, "userId"), false);
        String attemptGroupId = canonicalUuid(requiredText(root, "attemptGroupId"), true);
        String sessionId = requiredOpaqueSessionId(root);
        Instant occurredAt = requiredInstant(root, "occurredAt");
        if (occurredAt.isAfter(clock.instant().plus(properties.getMaxFutureSkew()))) {
            throw InternalApiException.invalidRequest();
        }

        AttemptGroupCompletionEvidence evidence = null;
        AttemptGroupFailureCode failureCode = null;
        if (target == AttemptGroupEventTarget.COMPLETED) {
            evidence = requiredEvidence(root.get("evidence"));
        } else if (target == AttemptGroupEventTarget.RETAKE_AVAILABLE) {
            failureCode = AttemptGroupFailureCode.fromWireValue(
                    requiredText(root, "failureCode")
            ).orElseThrow(InternalApiException::invalidRequest);
        }

        String digest = canonicalDigest(
                eventId, eventType, schemaVersion, producer, occurredAt,
                userId, attemptGroupId, sessionId, target, evidence, failureCode
        );
        return new AttemptGroupStatusEvent(
                eventId, eventType, schemaVersion, producer, occurredAt,
                userId, attemptGroupId, sessionId, target, evidence, failureCode, digest
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

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            if (!KNOWN_FIELDS.contains(names.next())) {
                throw InternalApiException.invalidRequest();
            }
        }
    }

    private static void validateExactFields(JsonNode root, AttemptGroupEventTarget target) {
        Set<String> expected = new HashSet<>(BASE_FIELDS);
        if (target == AttemptGroupEventTarget.COMPLETED) {
            expected.add("evidence");
        } else if (target == AttemptGroupEventTarget.RETAKE_AVAILABLE) {
            expected.add("failureCode");
        }
        Set<String> actual = new HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw InternalApiException.invalidRequest();
        }
    }

    private static String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw InternalApiException.invalidRequest();
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw InternalApiException.invalidRequest();
        }
        return value.intValue();
    }

    private static String canonicalUuid(String value, boolean requireVersionFour) {
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

    private static String requiredOpaqueSessionId(JsonNode root) {
        String value = requiredText(root, "sessionId");
        if (value.length() > 128 || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw InternalApiException.invalidRequest();
        }
        return value;
    }

    private static Instant requiredInstant(JsonNode root, String fieldName) {
        String value = requiredText(root, fieldName);
        if (!value.endsWith("Z")) {
            throw InternalApiException.invalidRequest();
        }
        try {
            Instant instant = Instant.parse(value);
            if (!instant.equals(instant.truncatedTo(ChronoUnit.MILLIS))) {
                throw InternalApiException.invalidRequest();
            }
            return instant;
        } catch (DateTimeException exception) {
            throw InternalApiException.invalidRequest();
        }
    }

    private static AttemptGroupCompletionEvidence requiredEvidence(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw InternalApiException.invalidRequest();
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(EVIDENCE_FIELDS)) {
            throw InternalApiException.invalidRequest();
        }
        boolean feedback = requiredTrue(node, "requiredFeedbackQueryable");
        boolean score = requiredTrue(node, "validScoreQueryable");
        boolean summary = requiredTrue(node, "summaryQueryable");
        int evidenceVersion = requiredInt(node, "evidenceVersion");
        if (evidenceVersion != EVIDENCE_VERSION) {
            throw InternalApiException.invalidRequest();
        }
        return new AttemptGroupCompletionEvidence(
                feedback, score, summary, evidenceVersion
        );
    }

    private static boolean requiredTrue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isBoolean() || !value.booleanValue()) {
            throw InternalApiException.invalidRequest();
        }
        return true;
    }

    private String canonicalDigest(
            String eventId,
            String eventType,
            int schemaVersion,
            String producer,
            Instant occurredAt,
            String userId,
            String attemptGroupId,
            String sessionId,
            AttemptGroupEventTarget target,
            AttemptGroupCompletionEvidence evidence,
            AttemptGroupFailureCode failureCode
    ) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                generator.writeStartObject();
                generator.writeStringField("eventId", eventId);
                generator.writeStringField("eventType", eventType);
                generator.writeNumberField("schemaVersion", schemaVersion);
                generator.writeStringField("producer", producer);
                generator.writeStringField("occurredAt", MILLIS_INSTANT.format(occurredAt));
                generator.writeStringField("userId", userId);
                generator.writeStringField("attemptGroupId", attemptGroupId);
                generator.writeStringField("sessionId", sessionId);
                generator.writeStringField("targetStatus", target.name());
                if (evidence != null) {
                    generator.writeObjectFieldStart("evidence");
                    generator.writeBooleanField(
                            "requiredFeedbackQueryable", evidence.requiredFeedbackQueryable()
                    );
                    generator.writeBooleanField(
                            "validScoreQueryable", evidence.validScoreQueryable()
                    );
                    generator.writeBooleanField(
                            "summaryQueryable", evidence.summaryQueryable()
                    );
                    generator.writeNumberField("evidenceVersion", evidence.evidenceVersion());
                    generator.writeEndObject();
                }
                if (failureCode != null) {
                    generator.writeStringField("failureCode", failureCode.name());
                }
                generator.writeEndObject();
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(output.toByteArray())
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create event digest.", exception);
        }
    }
}
