package web.tosunsaeng.billing.domain.ownerrebind.domain.model;

import java.time.Instant;

import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;

public record OwnerRebindCommand(
        String eventId,
        OwnerRebindEventKind eventKind,
        int schemaVersion,
        Instant occurredAt,
        String sourceUserId,
        String targetUserId,
        String consumerScopeId,
        Long sourceBindingRevision,
        Long targetBindingRevision,
        String payloadDigest
) {

    public boolean isPhoneRejoin() {
        return eventKind == OwnerRebindEventKind.PHONE_REJOIN;
    }

    @Override
    public String toString() {
        return "OwnerRebindCommand[eventId=" + eventId
                + ", eventKind=" + eventKind
                + ", sensitiveFields=[REDACTED]]";
    }
}
