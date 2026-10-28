package web.tosunsaeng.billing.domain.promotion;

import java.time.Clock;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.require;

public class PromotionEligibilityService {
    private final MongoTemplate mongo;
    private final EntitlementSnapshotExecutor snapshots;
    private final PaidEntitlementReader readiness;
    private final Clock clock;
    private final String environment, issuer;
    public PromotionEligibilityService(MongoTemplate mongo, EntitlementSnapshotExecutor snapshots,
            PaidEntitlementReader readiness, Clock clock, String environment, String issuer) {
        this.mongo=mongo; this.snapshots=snapshots; this.readiness=readiness; this.clock=clock;
        this.environment=environment; this.issuer=issuer;
    }
    public PromotionEligibilityResponse query(String user) {
        readiness.prepare();
        return snapshots.execute(() -> new PromotionEligibilityResponse(user, eligible(user), clock.instant().toString()));
    }
    private boolean eligible(String user) {
        // Reuse account/recovery validation, but never require a currently active paid period.
        if (readiness.read(user, clock.instant()).status()
                != web.tosunsaeng.billing.domain.payment.query.PaidQueryResponse.Status.READY) return false;
        Document account = one("payment_accounts", new Document("environment",environment).append("userId",user));
        Document life = one("payment_account_lifecycles", new Document("environment",environment).append("userId",user));
        if (account == null || life == null || !"ACTIVE".equals(account.getString("state"))
                || !"ACTIVE".equals(life.getString("state")) || account.get("memberEvidence") == null) return false;
        require(VerifiedMemberEvidence.matches(account.get("memberEvidence",Document.class),user,environment,issuer));
        var query = new BasicQuery(new Document("environment",environment).append("accountId",account.getString("_id"))
                .append("purchaseState",new Document("$in",List.of("VERIFIED","REFUNDED"))));
        query.fields().include("_id","processingStatus","firstVerifiedAt","productSnapshot","purchaseState","accessState","money","refund");
        var purchases = mongo.find(query.limit(1001).maxTime(EntitlementSnapshotExecutor.remaining()),Document.class,"purchases");
        require(purchases.size() <= 1000);
        for (Document purchase : purchases) if (PromotionPurchasePolicy.qualifies(purchase)
                && mongo.exists(new BasicQuery(new Document("purchaseId",purchase.getString("_id"))
                    .append("accountId",account.getString("_id"))).maxTime(EntitlementSnapshotExecutor.remaining()),"subscription_entitlements")) return true;
        return false;
    }
    private Document one(String collection, Document filter) {
        var rows=mongo.find(new BasicQuery(filter).limit(2).maxTime(EntitlementSnapshotExecutor.remaining()),Document.class,collection);
        require(rows.size() <= 1); return rows.isEmpty()?null:rows.getFirst();
    }
}
