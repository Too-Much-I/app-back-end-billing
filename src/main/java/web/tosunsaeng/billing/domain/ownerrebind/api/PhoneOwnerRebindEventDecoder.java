package web.tosunsaeng.billing.domain.ownerrebind.api;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.ownerrebind.exception.OwnerRebindException;
import web.tosunsaeng.billing.global.exception.InternalApiException;

@Component
public class PhoneOwnerRebindEventDecoder {

    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final String EVENT_TYPE = "TrialOwnerRebindApproved";
    private static final String PRODUCER = "identity";
    private static final String REASON = "PHONE_REJOIN";
    private static final int SCHEMA_VERSION = 1;
    private static final Pattern SCOPE_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> FIELDS = Set.of(
            "eventId", "eventType", "schemaVersion", "producer", "consumerScopeId",
            "occurredAt", "sourceUserId", "targetUserId", "lifecycleReason",
            "sourceBindingRevision", "targetBindingRevision"
    );

    private final OwnerRebindJsonSupport json;
    private final OwnerRebindProperties properties;
    private final TrialEligibilityProperties eligibilityProperties;
    private final Clock clock;

    public PhoneOwnerRebindEventDecoder(
            OwnerRebindJsonSupport json,
            OwnerRebindProperties properties,
            TrialEligibilityProperties eligibilityProperties,
            Clock clock
    ) {
        this.json = json;
        this.properties = properties;
        this.eligibilityProperties = eligibilityProperties;
        this.clock = clock;
    }

    public OwnerRebindCommand decode(byte[] payload) {
        JsonNode root = json.parse(payload, MAX_PAYLOAD_BYTES);
        json.requireExactFields(root, FIELDS);

        String eventType = json.requiredText(root, "eventType");
        int schemaVersion = json.requiredInt(root, "schemaVersion");
        String producer = json.requiredText(root, "producer");
        String lifecycleReason = json.requiredText(root, "lifecycleReason");
        if (!EVENT_TYPE.equals(eventType) || schemaVersion != SCHEMA_VERSION
                || !PRODUCER.equals(producer) || !REASON.equals(lifecycleReason)) {
            throw OwnerRebindException.unsupportedContract();
        }

        String scope = json.requiredText(root, "consumerScopeId");
        if (!SCOPE_PATTERN.matcher(scope).matches()) {
            throw InternalApiException.invalidRequest();
        }
        if (!scope.equals(eligibilityProperties.getExpectedConsumerScopeId())) {
            throw OwnerRebindException.unsupportedContract();
        }

        String eventId = json.canonicalUuid(root, "eventId", true);
        String sourceUserId = json.canonicalUuid(root, "sourceUserId", false);
        String targetUserId = json.canonicalUuid(root, "targetUserId", false);
        if (sourceUserId.equals(targetUserId)) {
            throw InternalApiException.invalidRequest();
        }
        Instant occurredAt = json.requiredInstant(root, "occurredAt");
        if (occurredAt.isAfter(clock.instant().plus(properties.getMaxFutureSkew()))) {
            throw InternalApiException.invalidRequest();
        }
        long sourceRevision = json.requiredRevision(root, "sourceBindingRevision");
        long targetRevision = json.requiredRevision(root, "targetBindingRevision");
        String digest = json.phoneDigest(
                eventId, eventType, schemaVersion, producer, scope, occurredAt,
                sourceUserId, targetUserId, lifecycleReason,
                sourceRevision, targetRevision
        );
        return new OwnerRebindCommand(
                eventId, OwnerRebindEventKind.PHONE_REJOIN, schemaVersion, occurredAt,
                sourceUserId, targetUserId, scope, sourceRevision, targetRevision, digest
        );
    }
}
