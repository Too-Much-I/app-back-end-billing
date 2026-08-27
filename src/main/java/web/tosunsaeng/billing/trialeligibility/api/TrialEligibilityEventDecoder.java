package web.tosunsaeng.billing.trialeligibility.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityCandidate;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityEvent;
import web.tosunsaeng.billing.trialeligibility.domain.TrialEligibilityEventType;

@Component
public class TrialEligibilityEventDecoder {

    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final String PRODUCER = "identity";
    private static final long MAX_BINDING_REVISION = 9_007_199_254_740_991L;
    private static final Pattern SCOPE_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern KEY_VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final Pattern CANDIDATE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Set<String> COMMON_FIELDS = Set.of(
            "eventId", "eventType", "schemaVersion", "producer", "occurredAt",
            "consumerScopeId", "userId", "bindingRevision"
    );
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "eventId", "eventType", "schemaVersion", "producer", "occurredAt",
            "consumerScopeId", "userId", "verifiedAt", "revokedAt", "bindingRevision",
            "fingerprintCandidates"
    );

    private final TrialEligibilityProperties properties;
    private final JsonFactory jsonFactory;
    private final ObjectMapper objectMapper;

    public TrialEligibilityEventDecoder(TrialEligibilityProperties properties) {
        this.properties = properties;
        this.jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.objectMapper = new ObjectMapper(jsonFactory);
    }

    public TrialEligibilityEvent decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw InternalApiException.invalidRequest();
        }

        JsonNode root = parse(payload);
        if (!root.isObject()) {
            throw InternalApiException.invalidRequest();
        }
        rejectUnknownFields(root);

        String eventTypeValue = requiredText(root, "eventType");
        TrialEligibilityEventType eventType = TrialEligibilityEventType.fromWireName(eventTypeValue)
                .orElseThrow(InternalApiException::unsupportedContract);
        validateExactFields(root, eventType);

        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw InternalApiException.unsupportedContract();
        }
        String producer = requiredText(root, "producer");
        if (!PRODUCER.equals(producer)) {
            throw InternalApiException.unsupportedContract();
        }

        String consumerScopeId = requiredText(root, "consumerScopeId");
        if (!SCOPE_PATTERN.matcher(consumerScopeId).matches()) {
            throw InternalApiException.invalidRequest();
        }
        if (!consumerScopeId.equals(properties.getExpectedConsumerScopeId())) {
            throw InternalApiException.unsupportedContract();
        }

        String eventId = canonicalUuid(requiredText(root, "eventId"), true);
        String userId = canonicalUuid(requiredText(root, "userId"), false);
        Instant occurredAt = requiredInstant(root, "occurredAt");
        long bindingRevision = requiredRevision(root, "bindingRevision");

        Instant verifiedAt = null;
        Instant revokedAt = null;
        List<TrialEligibilityCandidate> candidates = List.of();
        if (eventType == TrialEligibilityEventType.VERIFIED) {
            verifiedAt = requiredInstant(root, "verifiedAt");
            if (verifiedAt.isAfter(occurredAt)) {
                throw InternalApiException.invalidRequest();
            }
            candidates = requiredCandidates(root.get("fingerprintCandidates"));
        } else {
            revokedAt = requiredInstant(root, "revokedAt");
            if (revokedAt.isAfter(occurredAt)) {
                throw InternalApiException.invalidRequest();
            }
        }

        String digest = canonicalDigest(
                eventId, eventType, schemaVersion, producer, occurredAt, consumerScopeId,
                userId, verifiedAt, revokedAt, bindingRevision, candidates
        );
        return new TrialEligibilityEvent(
                eventId, eventType, schemaVersion, producer, occurredAt, consumerScopeId,
                userId, verifiedAt, revokedAt, bindingRevision, candidates, digest
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

    private static void validateExactFields(JsonNode root, TrialEligibilityEventType eventType) {
        Set<String> expected = new HashSet<>(COMMON_FIELDS);
        if (eventType == TrialEligibilityEventType.VERIFIED) {
            expected.add("verifiedAt");
            expected.add("fingerprintCandidates");
        } else {
            expected.add("revokedAt");
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

    private static long requiredRevision(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw InternalApiException.invalidRequest();
        }
        long revision = value.longValue();
        if (revision < 1 || revision > MAX_BINDING_REVISION) {
            throw InternalApiException.invalidRequest();
        }
        return revision;
    }

    private static Instant requiredInstant(JsonNode root, String fieldName) {
        String value = requiredText(root, fieldName);
        if (!value.endsWith("Z")) {
            throw InternalApiException.invalidRequest();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw InternalApiException.invalidRequest();
        }
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

    private static List<TrialEligibilityCandidate> requiredCandidates(JsonNode node) {
        if (node == null || !node.isArray() || node.size() < 1 || node.size() > 8) {
            throw InternalApiException.invalidRequest();
        }
        List<TrialEligibilityCandidate> candidates = new ArrayList<>();
        Set<String> keyVersions = new HashSet<>();
        for (JsonNode candidate : node) {
            if (!candidate.isObject() || candidate.size() != 2
                    || !candidate.has("keyVersion") || !candidate.has("value")) {
                throw InternalApiException.invalidRequest();
            }
            String keyVersion = requiredText(candidate, "keyVersion");
            String value = requiredText(candidate, "value");
            if (!KEY_VERSION_PATTERN.matcher(keyVersion).matches()
                    || !CANDIDATE_PATTERN.matcher(value).matches()
                    || !keyVersions.add(keyVersion)) {
                throw InternalApiException.invalidRequest();
            }
            candidates.add(new TrialEligibilityCandidate(keyVersion, value));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(TrialEligibilityCandidate::keyVersion)
                        .thenComparing(TrialEligibilityCandidate::value))
                .toList();
    }

    private String canonicalDigest(
            String eventId,
            TrialEligibilityEventType eventType,
            int schemaVersion,
            String producer,
            Instant occurredAt,
            String consumerScopeId,
            String userId,
            Instant verifiedAt,
            Instant revokedAt,
            long bindingRevision,
            List<TrialEligibilityCandidate> candidates
    ) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                generator.writeStartObject();
                generator.writeStringField("eventId", eventId);
                generator.writeStringField("eventType", eventType.wireName());
                generator.writeNumberField("schemaVersion", schemaVersion);
                generator.writeStringField("producer", producer);
                generator.writeStringField("occurredAt", occurredAt.toString());
                generator.writeStringField("consumerScopeId", consumerScopeId);
                generator.writeStringField("userId", userId);
                if (eventType == TrialEligibilityEventType.VERIFIED) {
                    generator.writeStringField("verifiedAt", verifiedAt.toString());
                } else {
                    generator.writeStringField("revokedAt", revokedAt.toString());
                }
                generator.writeNumberField("bindingRevision", bindingRevision);
                if (eventType == TrialEligibilityEventType.VERIFIED) {
                    generator.writeArrayFieldStart("fingerprintCandidates");
                    for (TrialEligibilityCandidate candidate : candidates) {
                        generator.writeStartObject();
                        generator.writeStringField("keyVersion", candidate.keyVersion());
                        generator.writeStringField("value", candidate.value());
                        generator.writeEndObject();
                    }
                    generator.writeEndArray();
                }
                generator.writeEndObject();
            }
            return hex(MessageDigest.getInstance("SHA-256").digest(output.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Canonical event digest could not be created.", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }
}
