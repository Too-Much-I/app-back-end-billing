package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventOutcome;
import web.tosunsaeng.billing.domain.attempt.domain.entity.*;
import web.tosunsaeng.billing.domain.attempt.domain.model.AttemptGroupStatusEvent;
import web.tosunsaeng.billing.domain.attempt.exception.AttemptGroupEventException;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.global.exception.InternalApiException;

/** Existing v1 event, immutable source, one transaction for inbox/projection/guard/usage. */
public final class PaidAttemptEventService {
    private final PaidExamTransactions tx;
    private final MongoTemplate mongo;
    private final PaymentRecoveryFence fence;
    private final String environment, paymentEnvironment;
    private final Clock clock;
    public PaidAttemptEventService(PaidExamTransactions tx, MongoTemplate mongo, PaymentStore store, PaymentProperties properties, String environment, Clock clock) {
        this.tx = tx; this.mongo = mongo; this.environment = environment; this.clock = clock;
        paymentEnvironment = properties.getEnvironment(); fence = new PaymentRecoveryFence(store, properties);
    }
    public boolean handles(String groupId) {
        AttemptGroup g = mongo.findById(groupId, AttemptGroup.class);
        return g != null && g.getSourceSnapshot() != null;
    }
    public AttemptGroupEventOutcome process(AttemptGroupStatusEvent event) {
        String usageId = UUID.randomUUID().toString(); Instant now = clock.instant();
        try {
            var ticket = fence.capture();
            return tx.execute(c -> {
                fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                AttemptGroup group = c.groups().findById(event.attemptGroupId()).orElseThrow(AttemptGroupEventException::projectionNotReady);
                AttemptSession session = c.sessions().findBySessionId(event.sessionId()).orElseThrow(AttemptGroupEventException::projectionNotReady);
                if (!group.getAttemptGroupId().equals(session.getAttemptGroupId()) || !group.getSubjectRefId().equals(session.getSubjectRefId())
                        || group.getSourceSnapshot() == null || session.getSourceSnapshot() == null
                        || !group.getSourceSnapshot().equals(session.getSourceSnapshot())) throw AttemptGroupEventException.targetConflict();
                Document groupRow = c.operations().findById(group.getAttemptGroupId(), Document.class, "attempt_groups");
                if (groupRow.getBoolean("deleted", false) || groupRow.getBoolean("lifecycleDenied", false)) throw AttemptGroupEventException.targetConflict();
                Document account = owner(c, group, session, event, now);
                Document inbox = c.operations().findOne(new BasicQuery(new Document("eventId", event.eventId())), Document.class, "inbound_event_inbox");
                if (inbox != null) {
                    if (!event.producer().equals(inbox.getString("producer")) || !event.payloadDigest().equals(inbox.getString("payloadDigest"))) throw AttemptGroupEventException.eventIdConflict();
                    return AttemptGroupEventOutcome.DUPLICATE;
                }
                if (session.getState() == AttemptSession.State.PROPOSED) throw AttemptGroupEventException.projectionNotReady();
                if (session.getState() == AttemptSession.State.ACTIVE && !event.sessionId().equals(group.getActiveSessionId()))
                    throw AttemptGroupEventException.targetConflict();
                boolean applicable = session.getState() == AttemptSession.State.ACTIVE && event.sessionId().equals(group.getActiveSessionId())
                        && (group.getStatus() == AttemptGroup.Status.OPEN || group.getStatus() == AttemptGroup.Status.GRADING && !"GRADING".equals(event.targetStatus().name()));
                String disposition = applicable ? "APPLIED" : "STALE";
                c.operations().insert(new Document("_id", event.eventId()).append("eventId", event.eventId()).append("producer", event.producer())
                        .append("eventType", event.eventType()).append("schemaVersion", event.schemaVersion()).append("payloadDigest", event.payloadDigest())
                        .append("disposition", disposition).append("receivedAt", Date.from(now)).append("purgeAt", Date.from(now.plus(Duration.ofDays(120)))), "inbound_event_inbox");
                if (!applicable) return AttemptGroupEventOutcome.STALE;
                switch (event.targetStatus()) {
                    case GRADING -> c.groups().markGrading(group.getAttemptGroupId(), session.getSessionId(), group.getVersion(), now).orElseThrow(AttemptGroupEventException::temporarilyUnavailable);
                    case COMPLETED -> {
                        c.groups().markCompleted(group.getAttemptGroupId(), session.getSessionId(), group.getVersion(), event.occurredAt(), now).orElseThrow(AttemptGroupEventException::temporarilyUnavailable);
                        c.sessions().completeActive(session.getSessionId(), group.getAttemptGroupId(), group.getSubjectRefId(), session.getVersion(), event.occurredAt()).orElseThrow(AttemptGroupEventException::temporarilyUnavailable);
                        Document guard = c.operations().findOne(new BasicQuery(new Document("environment", environment).append("userId", account.getString("userId"))), Document.class, PaidExamStore.GUARDS);
                        if (guard != null) c.paid().completeCurrent(environment, account.getString("userId"), guard.getLong("version"), group.getAttemptGroupId(), session.getSessionId());
                    }
                    case RETAKE_AVAILABLE -> {
                        c.groups().markRetakeAvailable(group.getAttemptGroupId(), session.getSessionId(), group.getVersion(), now).orElseThrow(AttemptGroupEventException::temporarilyUnavailable);
                        c.sessions().failActive(session.getSessionId(), group.getAttemptGroupId(), group.getSubjectRefId(), session.getVersion(), event.occurredAt()).orElseThrow(AttemptGroupEventException::temporarilyUnavailable);
                    }
                }
                if (event.targetStatus() != web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget.GRADING)
                    new web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository(c.operations())
                            .markTerminalDue(group.getSubjectRefId(), group.getAttemptGroupId(), session.getSessionId(), event.occurredAt());
                var source = group.getSourceSnapshot();
                if (source.type() == EntitlementSourceSnapshot.Type.SUBSCRIPTION) {
                    // Source snapshot is retained across refunds; no answer/audio/result body is copied.
                    c.operations().insert(new Document("_id", usageId).append("effectKey", "EXAM_STATUS:" + environment + ":" + event.eventId())
                            .append("type", "EXAM_STATUS").append("purchaseId", source.purchaseId()).append("entitlementId", source.entitlementId())
                            .append("occurredAt", Date.from(now)).append("sourceKind", "EXAM_EVENT").append("eventId", event.eventId())
                            .append("detail", new Document("attemptGroupId", group.getAttemptGroupId()).append("sessionId", session.getSessionId())
                                    .append("targetStatus", event.targetStatus().name()).append("occurredAt", Date.from(event.occurredAt()))), "payment_ledger");
                }
                return AttemptGroupEventOutcome.APPLIED;
            });
        } catch (InternalApiException expected) { throw expected; }
        catch (RuntimeException unavailable) { throw AttemptGroupEventException.temporarilyUnavailable(); }
    }
    private Document owner(PaidExamTransactions.Context c, AttemptGroup group, AttemptSession session, AttemptGroupStatusEvent event, Instant now) {
        var source = group.getSourceSnapshot(); Document account; boolean legacy = false;
        if (source.trialClaimId() != null) {
            Document link = c.operations().findById(group.getSubjectRefId(), Document.class, "billing_subject_links");
            if (link == null || !link.getBoolean("active", false) || link.getDate("retentionExpiresAt") == null
                    || !link.getDate("retentionExpiresAt").toInstant().isAfter(now)) throw AttemptGroupEventException.targetConflict();
            if (!source.trialClaimId().equals(link.getString("trialClaimId"))) throw AttemptGroupEventException.targetConflict();
            if (!event.userId().equals(link.getString("userId"))) {
                var rebind = new web.tosunsaeng.billing.domain.ownerrebind.repository.SubjectOwnerRebindRepository(c.operations())
                        .findActiveFence(group.getSubjectRefId(), event.userId(), group.getAttemptGroupId(), session.getSessionId(), now);
                legacy = rebind.isPresent() && session.getProposedAt() != null && session.getProposedAt().isBefore(rebind.get().getAppliedAt());
                // A terminal duplicate needs only its already committed inbox proof; it cannot advance state.
                Document existing = c.operations().findOne(new BasicQuery(new Document("eventId", event.eventId())
                        .append("producer", event.producer()).append("payloadDigest", event.payloadDigest())), Document.class, "inbound_event_inbox");
                if (!legacy && existing == null) throw AttemptGroupEventException.targetConflict();
                legacy = true;
            } else if (!Objects.equals(session.getSessionOwnerEpoch(), link.get("sessionOwnerEpoch"))) {
                throw AttemptGroupEventException.targetConflict();
            }
            account = c.operations().findOne(new BasicQuery(new Document("environment", paymentEnvironment).append("userId", link.getString("userId"))), Document.class, "payment_accounts");
        } else if (source.type() == EntitlementSourceSnapshot.Type.SUBSCRIPTION) {
            account = c.operations().findById(source.accountId(), Document.class, "payment_accounts");
        } else {
            Document grant = c.operations().findById(source.grantId(), Document.class, "entitlement_grants");
            if (grant == null || !environment.equals(grant.getString("environment")) || !event.userId().equals(grant.getString("ownerUserId"))) throw AttemptGroupEventException.targetConflict();
            account = c.operations().findOne(new BasicQuery(new Document("environment", paymentEnvironment).append("userId", grant.getString("ownerUserId"))), Document.class, "payment_accounts");
        }
        if (account == null || !paymentEnvironment.equals(account.getString("environment")) || (!legacy && !event.userId().equals(account.getString("userId")))
                || !"ACTIVE".equals(account.getString("state"))) throw AttemptGroupEventException.targetConflict();
        var query = new Document("environment", paymentEnvironment).append("userId", account.getString("userId")).append("state", "ACTIVE");
        if (c.operations().updateFirst(new BasicQuery(query), new Update().inc("version", 1), "payment_account_lifecycles").getModifiedCount() != 1)
            throw AttemptGroupEventException.targetConflict();
        return account;
    }
}
