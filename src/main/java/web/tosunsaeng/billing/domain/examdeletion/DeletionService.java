package web.tosunsaeng.billing.domain.examdeletion;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.paid.*;
import web.tosunsaeng.billing.global.exception.InternalApiException;

/** Exact Session denial and event receipt commit together. No learning content or user authorization copy. */
public final class DeletionService {
    public static final String FACTS = "exam_deletion_facts", RECEIPTS = "exam_deletion_receipts";
    private final PaidExamTransactions transactions;
    private final PaymentRecoveryFence fence;
    private final String environment, paymentEnvironment;
    private final Clock clock;

    public DeletionService(PaidExamTransactions transactions, PaymentStore store, PaymentProperties properties,
                           String environment, Clock clock) {
        if (!Set.of("test", "staging", "production").contains(environment)
                || !("production".equals(environment) ? "PRODUCTION" : "SANDBOX").equals(properties.getEnvironment()))
            throw new IllegalArgumentException("Invalid deletion environment");
        this.transactions = transactions; this.environment = environment; this.clock = clock;
        paymentEnvironment = properties.getEnvironment(); fence = new PaymentRecoveryFence(store, properties);
    }

    public void process(DeletionRequestDecoder.Request request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        try {
            var ticket = fence.capture();
            transactions.execute(c -> {
                fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                Document receipt = c.operations().findById(environment + ':' + request.eventId(), Document.class, RECEIPTS);
                if (receipt != null) {
                    if (!request.payloadDigest().equals(receipt.getString("payloadDigest"))) throw conflict();
                    return null;
                }
                Document fact = c.operations().findById(environment + ':' + request.sessionId(), Document.class, FACTS);
                if (fact != null) {
                    if (!request.attemptGroupId().equals(fact.getString("attemptGroupId"))) throw unresolved();
                    receipt(c, request, now, "ALREADY_DELETED");
                    return null;
                }
                Document session = row(c, "attempt_sessions", request.sessionId());
                Document group = row(c, "attempt_groups", request.attemptGroupId());
                if (session == null || group == null || !request.attemptGroupId().equals(session.getString("attemptGroupId"))
                        || group.getString("subjectRefId") == null || !group.getString("subjectRefId").equals(session.getString("subjectRefId"))
                        || !Objects.equals(group.get("sourceSnapshot"), session.get("sourceSnapshot"))) throw unresolved();
                Document creation = session.get("creationOwner", Document.class);
                // Environment/source evidence only. creation.userId is NOT independently re-authorized.
                if (creation == null || !environment.equals(creation.getString("environment"))) throw unresolved();
                if ("PROPOSED".equals(session.getString("state"))) throw unavailable();
                if (session.getBoolean("deleted", false)) throw unresolved();
                String owner = lockOwner(c, group, creation);
                Document guard = c.operations().findOne(new BasicQuery(new Document("environment", environment).append("userId", owner)),
                        Document.class, PaidExamStore.GUARDS);
                if (guard != null) {
                    if (guard.getString("pendingOperationId") != null) throw unavailable();
                }
                if (c.operations().exists(new BasicQuery(new Document("subjectRefId", group.getString("subjectRefId"))
                        .append("status", "RESERVED")), "reservations")) throw unavailable();
                String current = group.getString("activeSessionId");
                if (current == null) current = group.getString("lastConfirmedSessionId");
                // A cleared legacy projection is not proof that this is historical or current.
                if (current == null) throw unresolved();
                boolean currentGroup = owner.equals(request.userId()) && current.equals(request.sessionId());
                Update deny = new Update().set("deleted", true).set("completionAllowed", false).unset("activeGuard").inc("version", 1);
                cas(c, "attempt_sessions", session, deny);
                if (currentGroup) {
                    cas(c, "attempt_groups", group, new Update().set("deleted", true).set("replacementBlocked", true)
                            .unset("openGuard").inc("version", 1));
                    if (guard != null && request.attemptGroupId().equals(guard.getString("currentGroupId"))
                            && request.sessionId().equals(guard.getString("currentSessionId"))) {
                        cas(c, PaidExamStore.GUARDS, guard, new Update().set("currentGroupId", null)
                                .set("currentSessionId", null).inc("version", 1));
                    }
                }
                c.operations().insert(new Document("_id", environment + ':' + request.sessionId()).append("environment", environment)
                        .append("sessionId", request.sessionId()).append("attemptGroupId", request.attemptGroupId())
                        .append("eventId", request.eventId()).append("deletionOperationId", request.deletionOperationId())
                        .append("userId", request.userId()).append("schemaVersion", 1L).append("deletedAt", Date.from(request.deletedAt()))
                        .append("payloadDigest", request.payloadDigest()).append("appliedAt", Date.from(now))
                        .append("disposition", currentGroup ? "CURRENT_GROUP_BLOCKED" : "SESSION_ONLY").append("version", 1L), FACTS);
                receipt(c, request, now, "APPLIED");
                return null;
            });
        } catch (InternalApiException e) {
            // Preserve only this route's public business errors, not another subsystem's messages.
            if (Set.of("EVENT_ID_CONFLICT", "DELETION_TARGET_UNRESOLVED").contains(e.code())) throw e;
            throw unavailable();
        } catch (RuntimeException e) { throw unavailable(); }
    }

    private String lockOwner(PaidExamTransactions.Context c, Document group, Document creation) {
        String subject = group.getString("subjectRefId");
        Document source = group.get("sourceSnapshot", Document.class);
        if ("TRIAL_SUBJECT".equals(creation.getString("kind"))) {
            Document link = row(c, "billing_subject_links", subject);
            if (!subject.equals(creation.getString("reference")) || link == null || link.getString("userId") == null
                    || !Objects.equals(group.get("trialClaimId"), link.get("trialClaimId"))) throw unresolved();
            Object old = link.get("sessionBindingVersion");
            long next = old == null ? 1 : Math.incrementExact(((Number) old).longValue());
            if (c.operations().updateFirst(new BasicQuery(new Document("_id", subject).append("sessionBindingVersion", old)),
                    new Update().set("sessionBindingVersion", next), "billing_subject_links").getModifiedCount() != 1) throw unavailable();
            return link.getString("userId");
        }
        if (!"MEMBER_ACCOUNT".equals(creation.getString("kind")) || source == null) throw unresolved();
        Document account = row(c, "payment_accounts", creation.getString("reference"));
        if (account == null || !paymentEnvironment.equals(account.getString("environment"))) throw unresolved();
        if ("SUBSCRIPTION".equals(source.getString("type"))) {
            if (!creation.getString("reference").equals(source.getString("accountId"))
                    || !subject.equals(account.getString("paidSubjectRefId"))) throw unresolved();
        } else if ("EXAM_PASS".equals(source.getString("type"))) {
            Document grant = row(c, "entitlement_grants", source.getString("grantId"));
            if (grant == null || !environment.equals(grant.getString("environment"))
                    || !account.getString("userId").equals(grant.getString("ownerUserId"))) throw unresolved();
        } else throw unresolved();
        if (c.operations().updateFirst(new BasicQuery(new Document("environment", paymentEnvironment).append("userId", account.getString("userId"))),
                new Update().inc("version", 1), "payment_account_lifecycles").getModifiedCount() != 1) throw unresolved();
        return account.getString("userId");
    }
    private static Document row(PaidExamTransactions.Context c, String collection, String id) {
        return id == null ? null : c.operations().findById(id, Document.class, collection);
    }
    private static void cas(PaidExamTransactions.Context c, String collection, Document row, Update update) {
        if (c.operations().updateFirst(new BasicQuery(new Document("_id", row.get("_id")).append("version", row.get("version"))),
                update, collection).getModifiedCount() != 1) throw unavailable();
    }
    private void receipt(PaidExamTransactions.Context c, DeletionRequestDecoder.Request r, Instant now, String disposition) {
        c.operations().insert(new Document("_id", environment + ':' + r.eventId()).append("environment", environment)
                .append("eventId", r.eventId()).append("payloadDigest", r.payloadDigest()).append("sessionId", r.sessionId())
                .append("attemptGroupId", r.attemptGroupId()).append("processedAt", Date.from(now))
                .append("detailReviewAt", Date.from(now.plus(Duration.ofDays(120)))).append("disposition", disposition).append("version", 1L), RECEIPTS);
    }
    public static InternalApiException unavailable() { return new InternalApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "BILLING_TEMPORARILY_UNAVAILABLE", "Billing is temporarily unavailable.", true, 1); }
    private static InternalApiException unresolved() { return new InternalApiException(HttpStatus.CONFLICT,
            "DELETION_TARGET_UNRESOLVED", "The deletion target cannot be resolved.", false); }
    private static InternalApiException conflict() { return new InternalApiException(HttpStatus.CONFLICT,
            "EVENT_ID_CONFLICT", "The event ID conflicts with an existing event.", false); }
}
