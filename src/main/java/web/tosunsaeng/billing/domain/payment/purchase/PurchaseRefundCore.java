package web.tosunsaeng.billing.domain.payment.purchase;

import com.mongodb.client.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.bson.types.Decimal128;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Called only inside PurchaseCore's recovery-fenced transaction; no provider I/O or scheduling. */
final class PurchaseRefundCore {
    private final MongoDatabase db;
    private final Clock clock;
    PurchaseRefundCore(MongoDatabase db, Clock clock) { this.db = db; this.clock = clock; }

    String apply(ClientSession session, Document purchase, PurchaseObservation o) {
        RefundEvidence evidence = Objects.requireNonNull(o.refundEvidence());
        Date now = Date.from(clock.instant());
        Document refund = purchase.get("refund", Document.class);
        Document changes = new Document();
        if (refund == null) {
            refund = new Document("effectId", UUID.randomUUID().toString()).append("appliedAt", now)
                    .append("providerConfirmedAt", evidence.providerConfirmedAt() == null ? null : Date.from(evidence.providerConfirmedAt()))
                    .append("refundedTotal", null).append("currency", null).append("basis", null)
                    .append("amountSource", null).append("amountVersion", 0L);
            changes.append("purchaseState", o.kind() == PurchaseObservation.Kind.REFUND ? "REFUNDED" : "REVOKED")
                    .append("accessState", "REVOKED").append("processingStatus", "READY")
                    .append("refundWork", new Document("phase", "RESERVATIONS").append("state", "READY")
                            .append("afterId", null).append("owner", null).append("until", null).append("leaseVersion", 1L)
                            .append("generation", 1L).append("nextAttemptAt", now).append("retryStartedAt", now)
                            .append("attempts", 0L).append("failure", null));
            Document entitlement = c("subscription_entitlements").find(session, new Document("purchaseId", purchase.getString("_id"))).first();
            if (entitlement != null) {
                require(purchase.getString("accountId").equals(entitlement.getString("accountId")));
                require(entitlement.get("revokedAt") == null);
                update(session, "subscription_entitlements", entitlement, new Document("revokedAt", now));
                reflow(session, purchase, entitlement, refund, o, now);
            }
            ledger(session, purchase, "refund-access:" + purchase.getString("_id"), "ACCESS_REVOKED", o,
                    entitlement == null ? null : entitlement.getString("_id"), new Document("effectId", refund.getString("effectId"))
                            .append("appliedAt", now).append("reason", o.kind().name()));
        }
        String reason = amount(session, purchase, refund, o);
        changes.append("refund", refund).append("lastVerifiedAt", now);
        Date retention = Date.from(now.toInstant().atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
        if (purchase.getDate("retentionExpiresAt") != null && purchase.getDate("retentionExpiresAt").after(retention))
            retention = purchase.getDate("retentionExpiresAt");
        changes.append("retentionExpiresAt", retention);
        // Same root version touched by paid reserve/confirm: snapshot isolation alone is not enough.
        update(session, "purchases", purchase, changes);
        return reason;
    }

    private String amount(ClientSession s, Document p, Document refund, PurchaseObservation o) {
        RefundEvidence e = o.refundEvidence();
        if (e.amount() == null) return refund.get("refundedTotal") == null ? "REFUND_AMOUNT_UNKNOWN" : null;
        Document paid = p.get("money", Document.class);
        if (paid == null || !"STORE_PAID".equals(paid.getString("basis")) || !e.currency().equals(paid.getString("currency")))
            return "REFUND_AMOUNT_CONFLICT";
        BigDecimal total = e.amount(), max = paid.get("amount", Decimal128.class).bigDecimalValue();
        if (total.compareTo(max) > 0) return "REFUND_AMOUNT_CONFLICT";
        Decimal128 recorded = refund.get("refundedTotal", Decimal128.class);
        BigDecimal old = recorded == null ? BigDecimal.ZERO : recorded.bigDecimalValue();
        if (recorded != null && (!e.currency().equals(refund.getString("currency"))
                || !authority(e.source().name()).equals(authority(refund.getString("amountSource")))))
            return "REFUND_AMOUNT_CONFLICT";
        if (total.compareTo(old) < 0) return "REFUND_AMOUNT_CONFLICT";
        if (recorded != null && total.compareTo(old) == 0) return null;
        long version = refund.getLong("amountVersion"); require(version >= 0 && version < Long.MAX_VALUE);
        refund.put("amountVersion", version + 1); refund.put("refundedTotal", new Decimal128(total));
        refund.put("currency", e.currency()); refund.put("basis", "STORE_REFUND_GROSS"); refund.put("amountSource", e.source().name());
        ledger(s, p, "refund-amount:" + p.getString("_id") + ":" + (version + 1), "REFUND_AMOUNT_CONFIRMED", o, null,
                new Document("delta", new Decimal128(total.subtract(old))).append("total", new Decimal128(total))
                        .append("currency", e.currency()).append("basis", "STORE_REFUND_GROSS")
                        .append("source", e.source().name()).append("evidenceKey", e.evidenceKey()));
        return null;
    }
    private static String authority(String source) { return source.startsWith("REVENUECAT_") ? "REVENUECAT" : source; }

    private void reflow(ClientSession s, Document purchase, Document removed, Document refund, PurchaseObservation o, Date now) {
        String account = purchase.getString("accountId");
        Document timeline = c("payment_timelines").find(s, new Document("_id", account)).first();
        require(timeline != null && purchase.getString("environment").equals(timeline.getString("environment")));
        var slots = c("subscription_entitlements").find(s, new Document("accountId", account).append("revokedAt", null))
                .sort(new Document("sequence", 1)).into(new ArrayList<>());
        Date cursor = now;
        for (Document slot : slots) {
            Date start = slot.getDate("startsAt"), end = slot.getDate("endsAt");
            if (slot.getLong("sequence") > removed.getLong("sequence") && start.after(now)) {
                Document source = c("purchases").find(s, new Document("_id", slot.getString("purchaseId"))).first();
                require(source != null && "VERIFIED".equals(source.getString("purchaseState"))
                        && "ALLOWED".equals(source.getString("accessState")) && account.equals(source.getString("accountId")));
                Date shifted = cursor.before(start) ? cursor : start; // Never delay another purchased slot.
                Date shiftedEnd = Date.from(shifted.toInstant().plusSeconds(slot.getLong("durationSeconds")));
                if (!shifted.equals(start)) {
                    update(s, "subscription_entitlements", slot, new Document("startsAt", shifted).append("endsAt", shiftedEnd));
                    ledger(s, purchase, "refund-reflow:" + refund.getString("effectId") + ":" + slot.getString("_id"),
                            "TIMELINE_REFLOWED", o, slot.getString("_id"), new Document("previousStartsAt", start)
                                    .append("previousEndsAt", end).append("startsAt", shifted).append("endsAt", shiftedEnd));
                    end = shiftedEnd;
                }
            }
            if (end.after(cursor)) cursor = end;
        }
        update(s, "payment_timelines", timeline, new Document("timelineEnd", cursor));
    }
    private void ledger(ClientSession s, Document p, String key, String type, PurchaseObservation o, String entitlement, Document detail) {
        c("payment_ledger").insertOne(s, new Document("_id", UUID.randomUUID().toString()).append("effectKey", key).append("type", type)
                .append("purchaseId", p.getString("_id")).append("entitlementId", entitlement).append("occurredAt", Date.from(clock.instant()))
                .append("sourceKind", "VERIFIED_OBSERVATION").append("sourceObservationKey", o.providerEventKey()).append("detail", detail));
    }
    private void update(ClientSession s, String collection, Document old, Document changes) {
        long v = old.getLong("version"); require(v > 0 && v < Long.MAX_VALUE);
        changes.put("version", v + 1);
        require(c(collection).updateOne(s, new Document("_id", old.get("_id")).append("version", v),
                new Document("$set", changes)).getModifiedCount() == 1);
    }
    private MongoCollection<Document> c(String name) { return db.getCollection(name); }
}
