package web.tosunsaeng.billing.domain.ownerrebind.api;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.ownerrebind.config.OwnerRebindProperties;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.ownerrebind.exception.OwnerRebindException;
import web.tosunsaeng.billing.global.exception.InternalApiException;

@Component
public class UserMergedEventDecoder {

    public static final int MAX_PAYLOAD_BYTES = 4 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> FIELDS = Set.of(
            "eventId", "schemaVersion", "sourceUserId", "targetUserId", "occurredAt"
    );

    private final OwnerRebindJsonSupport json;
    private final OwnerRebindProperties properties;
    private final Clock clock;

    public UserMergedEventDecoder(
            OwnerRebindJsonSupport json,
            OwnerRebindProperties properties,
            Clock clock
    ) {
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    public OwnerRebindCommand decode(byte[] payload) {
        JsonNode root = json.parse(payload, MAX_PAYLOAD_BYTES);
        json.requireExactFields(root, FIELDS);

        int schemaVersion = json.requiredInt(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
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
        String digest = json.userMergedDigest(
                eventId, schemaVersion, sourceUserId, targetUserId, occurredAt
        );
        return new OwnerRebindCommand(
                eventId, OwnerRebindEventKind.USER_MERGED, schemaVersion, occurredAt,
                sourceUserId, targetUserId, null, null, null, digest
        );
    }
}
