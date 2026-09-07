package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Clock;
import java.util.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.global.infrastructure.mongodb.MongoTransactionExecutor;

/** Explicit operator invocation only; no runner, scheduled job or HTTP endpoint. */
@Service
public class SessionAttributionMigration {
    public record Report(int inspected, int eligible, int alreadyCovered, int blocked, int applied) { }
    private enum Outcome { ELIGIBLE, COVERED, BLOCKED }
    private final MongoTemplate mongo;
    private final BillingSubjectLinkRepository links;
    private final MongoTransactionExecutor transactions;
    private final Clock clock;
    public SessionAttributionMigration(MongoTemplate mongo, BillingSubjectLinkRepository links,
            MongoTransactionExecutor transactions, Clock clock) {
        this.mongo = mongo; this.links = links; this.transactions = transactions; this.clock = clock;
    }
    /** Approved explicit session IDs, <=100 per batch. Never log the input or return actor/session IDs. */
    public Report inspect(List<String> sessionIds) { return run(sessionIds, false); }
    public Report applyApprovedBatch(List<String> sessionIds) { return run(sessionIds, true); }
    private Report run(List<String> ids, boolean apply) {
        if (ids.isEmpty() || ids.size() > 100 || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 128)) {
            throw new IllegalArgumentException("An explicit bounded attribution batch is required");
        }
        int eligible = 0, covered = 0, blocked = 0;
        for (String id : ids) {
            Outcome outcome = transactions.execute(() -> migrateOne(id, apply));
            switch (outcome) { case ELIGIBLE -> eligible++; case COVERED -> covered++; case BLOCKED -> blocked++; }
        }
        return new Report(ids.size(), eligible, covered, blocked, apply ? eligible : 0);
    }
    private Outcome migrateOne(String id, boolean apply) {
        var session = mongo.findById(id, AttemptSession.class);
        if (session == null) { return Outcome.BLOCKED; }
        var link = links.findBySubjectRefId(session.getSubjectRefId()).orElse(null);
        var reservation = mongo.findOne(Query.query(Criteria.where("subjectRefId").is(session.getSubjectRefId())
                .and("operationId").is(session.getOperationId())), Reservation.class);
        if (link == null || !link.isActive() || !link.getRetentionExpiresAt().isAfter(clock.instant())
                || reservation == null || !id.equals(reservation.getProposedSessionId())
                || !session.getAttemptGroupId().equals(reservation.getAttemptGroupId())) { return Outcome.BLOCKED; }
        if (session.getSessionOwnerEpoch() != null && reservation.getSessionOwnerEpoch() != null
                && link.getSessionOwnerEpoch() != null) {
            return session.getSessionOwnerEpoch().equals(reservation.getSessionOwnerEpoch())
                    && session.getSessionOwnerEpoch() > 0 && session.getSessionOwnerEpoch() <= link.getSessionOwnerEpoch()
                    ? Outcome.COVERED : Outcome.BLOCKED;
        }
        // Never infer prior ownership from time or a missing continuation. Repeated rejoin may return to the same actor.
        boolean neverRebound = link.getOwnerVersion() == 1 && link.getOwnerTransitionReason() == null && link.getOwnerTransitionId() == null;
        boolean exactTarget = "PHONE_REJOIN".equals(link.getOwnerTransitionReason()) && link.getOwnerTransitionId() != null
                && reservation.getContinuationReason() == Reservation.ContinuationReason.PHONE_REJOIN
                && link.getOwnerTransitionId().equals(reservation.getContinuationId())
                && reservation.getStatus() == Reservation.Status.CONFIRMED;
        if (!neverRebound && !exactTarget) { return Outcome.BLOCKED; }
        long epoch = link.sessionEpochForWrite();
        if (session.getSessionOwnerEpoch() != null && session.getSessionOwnerEpoch() != epoch
                || reservation.getSessionOwnerEpoch() != null && reservation.getSessionOwnerEpoch() != epoch) { return Outcome.BLOCKED; }
        if (apply) {
            links.bindSession(link, clock.instant()).orElseThrow(() -> new IllegalStateException("Attribution CAS conflict; re-inspect batch"));
            if (session.getSessionOwnerEpoch() == null) {
                requireUpdated(mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("version").is(session.getVersion())
                                .and("sessionOwnerEpoch").is(null)), new Update().set("sessionOwnerEpoch", epoch), AttemptSession.class).getModifiedCount());
            }
            if (reservation.getSessionOwnerEpoch() == null) {
                requireUpdated(mongo.updateFirst(Query.query(Criteria.where("_id").is(reservation.getReservationId())
                                .and("version").is(reservation.getVersion()).and("sessionOwnerEpoch").is(null)),
                        new Update().set("sessionOwnerEpoch", epoch), Reservation.class).getModifiedCount());
            }
        }
        return Outcome.ELIGIBLE;
    }
    private static void requireUpdated(long count) {
        if (count != 1) { throw new IllegalStateException("Attribution CAS conflict; re-inspect batch"); }
    }
}
