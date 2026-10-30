package web.tosunsaeng.billing.domain.reanswer;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import web.tosunsaeng.billing.domain.entitlement.application.*;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementQueryRepository;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;

/** Observation only. All business reads share one Spring-bound Mongo snapshot; no writes or reservations. */
public class ReanswerService {
    private final MongoTemplate mongo;
    private final EntitlementSnapshotExecutor snapshots;
    private final PaidEntitlementReader paid;
    private final EntitlementQueryRepository trial;
    private final TrialEntitlementQueryEvaluator evaluator;
    private final Clock clock;
    private final String environment, paymentEnvironment, issuer;
    public ReanswerService(MongoTemplate mongo, EntitlementSnapshotExecutor snapshots, PaidEntitlementReader paid,
            EntitlementQueryRepository trial, TrialEntitlementQueryEvaluator evaluator, Clock clock,
            String environment, String paymentEnvironment, String issuer) {
        this.mongo = mongo; this.snapshots = snapshots; this.paid = paid; this.trial = trial;
        this.evaluator = evaluator; this.clock = clock; this.environment = environment;
        this.paymentEnvironment = paymentEnvironment; this.issuer = issuer;
    }
    public ReanswerResponse query(String user) {
        paid.prepare();
        require(mongo.getCollectionNames().containsAll(List.of("entitlement_grants", "trial_eligibility",
                "trial_candidate_aliases", "trial_claims", "billing_subject_links", "benefit_definitions",
                "attempt_groups", "attempt_sessions", "reservations", "idempotency_commands")));
        Instant checkedAt = clock.instant(); // Snapshot retries must not renew the observation window.
        return snapshots.execute(() -> read(user, checkedAt));
    }
    private ReanswerResponse read(String user, Instant now) {
        var paidState = paid.read(user, now); // Includes the recovery barrier and paid provenance checks.
        Document life = one("payment_account_lifecycles", paymentOwner(user));
        Document account = one("payment_accounts", paymentOwner(user));
        if (withdrawn(life) || withdrawn(account))
            return ReanswerResponse.result(user, now, "DENIED", "ACCOUNT_WITHDRAWN", null);
        boolean evidence = account != null && account.get("memberEvidence") != null;
        if (evidence) require(VerifiedMemberEvidence.matches(account.get("memberEvidence", Document.class),
                user, paymentEnvironment, issuer));
        var grants = mongo.find(new BasicQuery(new Document("environment", environment).append("ownerUserId", user)
                .append("benefitCode", "FREE_EXAM_ONCE").append("state", "ACTIVE")
                .append("availableUnits", new Document("$gt", 0))).limit(1001)
                .maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, "entitlement_grants");
        require(grants.size() <= 1000);
        for (var grant : grants) {
            require(Set.of("INVITE", "REFERRAL", "TRIAL_PAID_RESTORATION").contains(grant.getString("sourceType"))
                    && grant.get("grantOrder") instanceof Long order && order > 0
                    && grant.get("totalUnits") instanceof Integer total && total == 1
                    && grant.get("heldUnits") instanceof Integer held && held == 0
                    && grant.get("consumedUnits") instanceof Integer consumed && consumed == 0
                    && grant.get("availableUnits") instanceof Integer n && n == 1);
        }
        boolean free = evidence && !grants.isEmpty();
        boolean active = evidence && Boolean.TRUE.equals(paidState.active());
        boolean pending = paidState.status() == PaidQueryResponse.Status.PENDING
                || !evidence && (!grants.isEmpty() || Boolean.TRUE.equals(paidState.active()));
        // A known member pass is enough; uncertain phone eligibility does not cancel it.
        if (!free) {
            var snapshot = trial.read(user, now);
            var benefit = evaluator.evaluate(snapshot);
            // The public reader projects an unissued trial. Only a selected, already-issued Claim counts here.
            free = snapshot.candidateClaimId() != null && benefit.availableQuantity() != null && benefit.availableQuantity() > 0;
            pending |= benefit.availableQuantity() == null;
        }
        return decide(user, now, free, active, paidState.activeEndsAt(), pending);
    }
    static ReanswerResponse decide(String user, Instant now, boolean free, boolean active, Instant endsAt, boolean pending) {
        if (active) require(endsAt != null && endsAt.isAfter(now));
        if (free || active) {
            Instant until = now.plusSeconds(5);
            if (!free && endsAt.isBefore(until)) until = endsAt;
            return ReanswerResponse.result(user, now, "ALLOWED", "ENTITLEMENT_AVAILABLE", until);
        }
        return ReanswerResponse.result(user, now, pending ? "PENDING" : "DENIED",
                pending ? "ENTITLEMENT_STATE_UNCONFIRMED" : "ENTITLEMENT_INSUFFICIENT", null);
    }
    private static boolean withdrawn(Document row) { return row != null && "WITHDRAWN".equals(row.getString("state")); }
    private Document paymentOwner(String user) { return new Document("environment", paymentEnvironment).append("userId", user); }
    private Document one(String collection, Document query) {
        var rows = mongo.find(new BasicQuery(query).limit(2).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, collection);
        require(rows.size() <= 1); return rows.isEmpty() ? null : rows.getFirst();
    }
}
