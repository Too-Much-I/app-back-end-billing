package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;

/** Read-only and bound to the SAME snapshot as paid/trial query readers. */
public final class PaidExamQueryReader implements ExamAdmissionReader {
    private final MongoTemplate mongo;
    private final String environment, paymentEnvironment, issuer;
    public PaidExamQueryReader(MongoTemplate mongo, String environment, String paymentEnvironment, String issuer) {
        this.mongo = mongo; this.environment = environment; this.paymentEnvironment = paymentEnvironment; this.issuer = issuer;
    }
    public void prepare() {
        require(mongo.getCollectionNames().containsAll(List.of(PaidExamStore.RECEIPTS, PaidExamStore.GUARDS,
                "entitlement_grants", "attempt_groups", "payment_accounts", "payment_account_lifecycles")));
    }
    public List<Benefit> augment(String user, List<Benefit> benefits) {
        require(TransactionSynchronizationManager.isActualTransactionActive());
        Document life = one("payment_account_lifecycles", new Document("environment", paymentEnvironment).append("userId", user));
        boolean withdrawn = life != null && "WITHDRAWN".equals(life.getString("state"));
        int units = 0;
        if (!withdrawn) for (Document grant : grants(user)) units = Math.addExact(units, grant.getInteger("availableUnits"));
        List<Benefit> result = new ArrayList<>();
        for (Benefit b : benefits) {
            if (!"FREE_EXAM_ONCE".equals(b.benefitCode())) { result.add(b); continue; }
            List<Group> groups = new ArrayList<>(b.attemptGroups().stream().map(g -> {
                Document stored = one("attempt_groups", new Document("_id", g.attemptGroupId()));
                if (withdrawn || stored != null && (stored.getBoolean("replacementBlocked", false) || stored.getBoolean("deleted", false) || stored.getBoolean("lifecycleDenied", false)))
                    return new Group(g.attemptGroupId(), g.state(), g.hasInProgress(), Action.BLOCKED, List.of(Reason.NO_NEW_UNITS));
                return g;
            }).toList());
            Group member = withdrawn ? null : memberGroup(user);
            if (member != null && groups.stream().noneMatch(g -> g.attemptGroupId().equals(member.attemptGroupId()))) groups.add(member);
            Action retake = groups.stream().anyMatch(g -> g.retake() == Action.ALLOWED) ? Action.ALLOWED
                    : groups.stream().anyMatch(g -> g.retake() == Action.PENDING) ? Action.PENDING : Action.BLOCKED;
            result.add(new Benefit(b.benefitCode(), b.displayName(), b.unit(), withdrawn ? 0 : b.availableQuantity() == null ? null : Math.addExact(b.availableQuantity(), units),
                    b.eligibility(), b.usageState(), withdrawn ? Action.BLOCKED : units > 0 ? Action.ALLOWED : b.newAttempt(), groups.stream().anyMatch(g -> Boolean.TRUE.equals(g.hasInProgress())) ? true : b.hasInProgress(), retake, b.reasonCodes(), List.copyOf(groups)));
        }
        return List.copyOf(result);
    }
    @Override public ExamAvailability read(String user, Instant now, PaidQueryResponse paid, List<Benefit> benefits) {
        require(TransactionSynchronizationManager.isActualTransactionActive());
        Document account = one("payment_accounts", new Document("environment", paymentEnvironment).append("userId", user));
        Document life = one("payment_account_lifecycles", new Document("environment", paymentEnvironment).append("userId", user));
        if (life != null && "WITHDRAWN".equals(life.getString("state")) || account != null && "WITHDRAWN".equals(account.getString("state")))
            return availability(Action.BLOCKED, Action.BLOCKED, null, false, "ACCOUNT_WITHDRAWN");
        Document guard = one(PaidExamStore.GUARDS, new Document("environment", environment).append("userId", user));
        boolean progress = guard != null && guard.getString("currentGroupId") != null || benefits.stream().anyMatch(b -> Boolean.TRUE.equals(b.hasInProgress()));
        if (guard != null && guard.getString("pendingOperationId") != null)
            return availability(Action.PENDING, Action.PENDING, null, progress, "RESERVATION_PENDING");
        if (exists(PaidExamStore.RECEIPTS, new Document("environment", environment).append("userId", user).append("state", "WAITING_MEMBER_EVIDENCE")))
            return availability(Action.PENDING, Action.PENDING, null, progress, "MEMBER_EVIDENCE_PENDING");
        if (paid.status() == PaidQueryResponse.Status.PENDING) return availability(Action.PENDING, Action.PENDING, null, progress, "RECONCILIATION_PENDING");
        var memberGrants = grants(user);
        if (Boolean.TRUE.equals(paid.active()) || !memberGrants.isEmpty()) {
            if (!hasEvidence(account, user)) return availability(Action.PENDING, Action.PENDING, null, progress, "MEMBER_EVIDENCE_PENDING");
        }
        if (Boolean.TRUE.equals(paid.active())) return availability(Action.ALLOWED, Action.BLOCKED, ExamAvailability.Source.PAID, progress, "PAID_AVAILABLE");
        Action fresh = !memberGrants.isEmpty() || benefits.stream().anyMatch(b -> b.newAttempt() == Action.ALLOWED) ? Action.ALLOWED
                : benefits.stream().anyMatch(b -> b.newAttempt() == Action.PENDING) ? Action.PENDING : Action.BLOCKED;
        Action retake = benefits.stream().anyMatch(b -> b.retake() == Action.ALLOWED) ? Action.ALLOWED
                : benefits.stream().anyMatch(b -> b.retake() == Action.PENDING) ? Action.PENDING : Action.BLOCKED;
        if (guard != null && guard.getString("currentGroupId") != null) {
            Document group = one("attempt_groups", new Document("_id", guard.getString("currentGroupId")));
            require(group != null);
            Document source = group.get("sourceSnapshot", Document.class);
            if (source != null && "EXAM_PASS".equals(source.getString("type")) && !group.getBoolean("replacementBlocked", false)
                    && !group.getBoolean("deleted", false) && !group.getBoolean("lifecycleDenied", false)
                    && Set.of("OPEN", "RETAKE_AVAILABLE").contains(group.getString("status"))) {
                Document grant = one("entitlement_grants", new Document("_id", source.getString("grantId")));
                if (grant != null && user.equals(grant.getString("ownerUserId")) && environment.equals(grant.getString("environment")) && "ACTIVE".equals(grant.getString("state"))) {
                    if (!hasEvidence(account, user)) return availability(Action.PENDING, Action.PENDING, null, progress, "MEMBER_EVIDENCE_PENDING");
                    retake = Action.ALLOWED;
                }
            }
        }
        var preferred = retake == Action.ALLOWED ? ExamAvailability.Source.FREE_RETAKE : fresh == Action.ALLOWED ? ExamAvailability.Source.FREE_NEW : null;
        return availability(fresh, retake, preferred, progress, preferred == null ? "NO_NEW_UNITS" : "FREE_AVAILABLE");
    }
    private Group memberGroup(String user) {
        Document guard = one(PaidExamStore.GUARDS, new Document("environment", environment).append("userId", user));
        if (guard == null || guard.getString("currentGroupId") == null) return null;
        Document group = one("attempt_groups", new Document("_id", guard.getString("currentGroupId"))); require(group != null);
        Document source = group.get("sourceSnapshot", Document.class); require(source != null);
        if (!"EXAM_PASS".equals(source.getString("type")) || source.getString("trialClaimId") != null) return null;
        Document grant = one("entitlement_grants", new Document("_id", source.getString("grantId")));
        require(grant != null && user.equals(grant.getString("ownerUserId")) && environment.equals(grant.getString("environment")));
        var state = web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup.Status.valueOf(group.getString("status"));
        boolean progress = state == web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup.Status.OPEN
                || state == web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup.Status.GRADING;
        boolean blocked = !"ACTIVE".equals(grant.getString("state")) || group.getBoolean("replacementBlocked", false)
                || group.getBoolean("deleted", false) || group.getBoolean("lifecycleDenied", false);
        Action retake = blocked ? Action.BLOCKED : switch (state) {
            case OPEN, RETAKE_AVAILABLE -> Action.ALLOWED;
            case GRADING -> Action.PENDING;
            default -> Action.BLOCKED;
        };
        if (!blocked && guard.getString("pendingOperationId") != null) retake = Action.PENDING;
        return new Group(group.getString("_id"), state, progress, retake,
                List.of(retake == Action.ALLOWED ? Reason.RETAKE_AVAILABLE : retake == Action.PENDING ? Reason.RECONCILIATION_PENDING : Reason.NO_NEW_UNITS));
    }
    private List<Document> grants(String user) {
        var rows = mongo.find(new BasicQuery(new Document("environment", environment).append("ownerUserId", user).append("benefitCode", "FREE_EXAM_ONCE").append("state", "ACTIVE")
                .append("availableUnits", new Document("$gt", 0))).limit(1001).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, "entitlement_grants");
        require(rows.size() <= 1000);
        for (var row : rows) require(row.get("availableUnits") instanceof Integer n && n > 0 && row.get("grantOrder") instanceof Long);
        return rows;
    }
    private boolean hasEvidence(Document account, String user) {
        if (account == null || account.get("memberEvidence") == null) return false;
        require(VerifiedMemberEvidence.matches(account.get("memberEvidence", Document.class), user, paymentEnvironment, issuer));
        return true;
    }
    private boolean exists(String collection, Document query) {
        return !mongo.find(new BasicQuery(query).limit(1).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, collection).isEmpty();
    }
    private Document one(String collection, Document query) {
        var rows = mongo.find(new BasicQuery(query).limit(2).maxTime(EntitlementSnapshotExecutor.remaining()), Document.class, collection);
        require(rows.size() <= 1); return rows.isEmpty() ? null : rows.getFirst();
    }
    private static ExamAvailability availability(Action fresh, Action retake, ExamAvailability.Source source, boolean progress, String reason) {
        Action action = fresh == Action.ALLOWED || retake == Action.ALLOWED ? Action.ALLOWED : fresh == Action.PENDING || retake == Action.PENDING ? Action.PENDING : Action.BLOCKED;
        return new ExamAvailability(null, action, fresh, retake, source, progress, List.of(reason));
    }
}
