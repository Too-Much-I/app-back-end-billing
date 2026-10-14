package web.tosunsaeng.billing.domain.payment.purchase;

import java.util.Objects;

/** Authenticated, normalized receipt; raw JSON and provider identifiers are never persisted here. */
public record WebhookReceipt(String environment, String eventKey, String receptionDigest, String lookupKeyVersion,
                             PurchaseObservation observation, String ownerRefId, Disposition disposition, String reason) {
    public enum Disposition { PURCHASE, IGNORED, REVIEW }
    public WebhookReceipt {
        if (!java.util.Set.of("SANDBOX", "PRODUCTION").contains(environment)) throw PurchaseObservation.invalid();
        PurchaseObservation.hex(eventKey); PurchaseObservation.hex(receptionDigest); PurchaseObservation.label(lookupKeyVersion);
        Objects.requireNonNull(disposition); PurchaseObservation.label(reason);
        if (ownerRefId != null) PurchaseObservation.uuid(ownerRefId);
        if ((observation != null) != (disposition == Disposition.PURCHASE)) throw PurchaseObservation.invalid();
        if (observation != null && (!eventKey.equals(observation.providerEventKey()) || !environment.equals(observation.environment())
                || !lookupKeyVersion.equals(observation.lookupKeyVersion()) || !Objects.equals(ownerRefId, observation.ownerRefId())))
            throw PurchaseObservation.invalid();
    }
    @Override public String toString() { return "WebhookReceipt[REDACTED]"; }
}
