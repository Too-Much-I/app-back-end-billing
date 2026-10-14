package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import org.bson.Document;

/** Internal immutable source, not a new HTTP DTO. Consumption binds only at confirm. */
public record EntitlementSourceSnapshot(Type type, String grantId, String consumptionId, String trialClaimId,
        String accountId, String purchaseId, String entitlementId,
        Instant selectedAt, Instant startsAt, Instant endsAt) {
    public enum Type { EXAM_PASS, SUBSCRIPTION }
    public EntitlementSourceSnapshot {
        Objects.requireNonNull(type); Objects.requireNonNull(selectedAt);
        if (type == Type.EXAM_PASS) {
            required(grantId);
            if (accountId != null || purchaseId != null || entitlementId != null || startsAt != null || endsAt != null)
                throw new IllegalArgumentException("Invalid exam pass source");
            optional(consumptionId); optional(trialClaimId);
        } else {
            required(accountId); required(purchaseId); required(entitlementId);
            if (grantId != null || consumptionId != null || trialClaimId != null || startsAt == null || endsAt == null
                    || !startsAt.isBefore(endsAt) || selectedAt.isBefore(startsAt) || !selectedAt.isBefore(endsAt))
                throw new IllegalArgumentException("Invalid subscription source");
        }
    }
    public EntitlementSourceSnapshot bindConsumption(String id) {
        required(id);
        if (type != Type.EXAM_PASS || consumptionId != null && !consumptionId.equals(id))
            throw new IllegalStateException("Source consumption cannot be replaced");
        return new EntitlementSourceSnapshot(type, grantId, id, trialClaimId, null, null, null, selectedAt, null, null);
    }
    public Document toDocument() {
        return new Document("type", type.name()).append("grantId", grantId).append("consumptionId", consumptionId)
                .append("trialClaimId", trialClaimId).append("accountId", accountId).append("purchaseId", purchaseId)
                .append("entitlementId", entitlementId).append("selectedAt", Date.from(selectedAt))
                .append("startsAt", startsAt == null ? null : Date.from(startsAt))
                .append("endsAt", endsAt == null ? null : Date.from(endsAt));
    }
    private static void required(String value) {
        if (value == null || value.isBlank() || value.length() > 128) throw new IllegalArgumentException("Invalid source reference");
    }
    private static void optional(String value) { if (value != null) required(value); }
    @Override public String toString() { return "EntitlementSourceSnapshot[type=" + type + ", references=REDACTED]"; }
}
