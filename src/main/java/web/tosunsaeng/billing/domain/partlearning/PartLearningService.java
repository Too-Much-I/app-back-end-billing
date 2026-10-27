package web.tosunsaeng.billing.domain.partlearning;

import java.time.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;

/** Paid content observation only; no grants, reservations or admission receipts are written. */
public class PartLearningService {
    private final MongoTemplate mongo;
    private final EntitlementSnapshotExecutor snapshots;
    private final PaidEntitlementReader paid;
    private final Clock clock;
    private final String environment, issuer;
    public PartLearningService(MongoTemplate mongo, EntitlementSnapshotExecutor snapshots,
            PaidEntitlementReader paid, Clock clock, String environment, String issuer) {
        this.mongo = mongo; this.snapshots = snapshots; this.paid = paid;
        this.clock = clock; this.environment = environment; this.issuer = issuer;
    }
    public PartLearningResponse query(String user) {
        paid.prepare();
        Instant checkedAt = clock.instant();
        return snapshots.execute(() -> read(user, checkedAt));
    }
    private PartLearningResponse read(String user, Instant now) {
        var state = paid.read(user, now);
        Document life = one("payment_account_lifecycles", user);
        Document account = one("payment_accounts", user);
        if (withdrawn(life) || withdrawn(account))
            return PartLearningResponse.result(user, now, "DENIED", "ACCOUNT_WITHDRAWN", null);
        boolean evidence = account != null && account.get("memberEvidence") != null;
        if (evidence) require(VerifiedMemberEvidence.matches(account.get("memberEvidence", Document.class),
                user, environment, issuer));
        boolean active = Boolean.TRUE.equals(state.active());
        if (active && evidence) {
            require(state.activeEndsAt() != null && state.activeEndsAt().isAfter(now));
            Instant until = now.plusSeconds(5);
            if (state.activeEndsAt().isBefore(until)) until = state.activeEndsAt();
            return PartLearningResponse.result(user, now, "ALLOWED", "ACTIVE_PAID_ENTITLEMENT", until);
        }
        boolean pending = state.status() == PaidQueryResponse.Status.PENDING || active && !evidence;
        return PartLearningResponse.result(user, now, pending ? "PENDING" : "DENIED",
                pending ? "ENTITLEMENT_STATE_UNCONFIRMED" : "PAID_ENTITLEMENT_REQUIRED", null);
    }
    private Document one(String collection, String user) {
        var rows = mongo.find(new BasicQuery(new Document("environment", environment).append("userId", user))
                .limit(2).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, collection);
        require(rows.size() <= 1); return rows.isEmpty() ? null : rows.getFirst();
    }
    private static boolean withdrawn(Document row) { return row != null && "WITHDRAWN".equals(row.getString("state")); }
}
