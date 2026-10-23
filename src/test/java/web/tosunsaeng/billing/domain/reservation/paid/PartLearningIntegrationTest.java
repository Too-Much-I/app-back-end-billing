package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import web.tosunsaeng.billing.domain.partlearning.*;
import web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import static org.assertj.core.api.Assertions.*;

class PartLearningIntegrationTest extends PaidReservationTestSupport {
    @BeforeEach void prepare() {
        purchase();
        if (!db.listCollectionNames().into(new ArrayList<>()).contains("payment_sync_commands")) db.createCollection("payment_sync_commands");
        db.getCollection("payment_recovery_controls").updateOne(new Document(),new Document("$set",new Document("schemaVersion",4L)));
    }
    PartLearningService reader(Instant now) {
        return new PartLearningService(mongo,snapshots,new PaidEntitlementReader(mongo,properties),Clock.fixed(now,ZoneOffset.UTC),"SANDBOX",ISSUER);
    }
    Map<String,List<Document>> snapshot() {
        Map<String,List<Document>> rows = new TreeMap<>();
        for (String c : db.listCollectionNames()) rows.put(c,db.getCollection(c).find().into(new ArrayList<>()));
        return rows;
    }
    @Test void activePaidAndPendingSyncReadOnlyWithNanoseconds() {
        db.getCollection("payment_sync_commands").insertOne(new Document("environment","SANDBOX").append("accountId",account().getString("_id")).append("state","PENDING"));
        var before = snapshot();
        var now = NOW.plusNanos(123456789);
        for (int i=0;i<2;i++) {
            var r=reader(now).query(USER);
            assertThat(r.reason()).isEqualTo("ACTIVE_PAID_ENTITLEMENT");
            assertThat(r.checkedAt()).isEqualTo(now.toString());
            assertThat(r.validUntil()).isEqualTo(now.plusSeconds(5).toString());
        }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void allFreePassesExcludedAfterPaidExpiry() {
        mongo.insert(EntitlementGrant.invite("invite","test",USER,"award",1L,NOW));
        mongo.insert(EntitlementGrant.referral("referral","test",USER,"application:APPLICANT",2L,NOW));
        mongo.insert(EntitlementGrant.restoredTrial("restore","test",USER,"consumption",3L,NOW));
        mongo.insert(EntitlementGrant.unitGrant("trial","FREE_EXAM_ONCE","TRIAL_CLAIM","claim","subject",1,NOW));
        var before=snapshot();
        assertThat(reader(NOW.plusSeconds(86400)).query(USER).reason()).isEqualTo("PAID_ENTITLEMENT_REQUIRED");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void exactStartAndEndBoundaries() {
        var slot=db.getCollection("subscription_entitlements").find().first();
        var start=slot.getDate("startsAt").toInstant();
        var end=slot.getDate("endsAt").toInstant();
        assertThat(reader(start.minusNanos(1)).query(USER).decision()).isEqualTo("DENIED");
        assertThat(reader(start).query(USER).decision()).isEqualTo("ALLOWED");
        assertThat(reader(end.minusNanos(1)).query(USER).validUntil()).isEqualTo(end.toString());
        assertThat(reader(end).query(USER).decision()).isEqualTo("DENIED");
    }
    @Test void missingEvidenceAndUnreadyPurchaseArePending() {
        db.getCollection("payment_accounts").updateOne(new Document("userId",USER),new Document("$unset",new Document("memberEvidence","")));
        assertThat(reader(NOW).query(USER).decision()).isEqualTo("PENDING");
        db.getCollection("purchases").updateOne(new Document(),new Document("$set",new Document("processingStatus","REVIEW_REQUIRED")));
        assertThat(reader(NOW).query(USER).decision()).isEqualTo("PENDING");
    }
    @Test void withdrawalAndRefundAreDenied() {
        db.getCollection("purchases").updateOne(new Document(),new Document("$set",new Document("purchaseState","REFUNDED").append("accessState","REVOKED")));
        assertThat(reader(NOW).query(USER).reason()).isEqualTo("PAID_ENTITLEMENT_REQUIRED");
        db.getCollection("payment_account_lifecycles").updateOne(new Document("userId",USER),new Document("$set",new Document("state","WITHDRAWN")));
        assertThat(reader(NOW).query(USER).reason()).isEqualTo("ACCOUNT_WITHDRAWN");
    }
    @Test void overlappingActiveSlotsFailInsteadOfPickingLatestEnd() {
        var purchase=db.getCollection("purchases").find().first();
        var purchaseId=UUID.randomUUID().toString();
        purchase.put("_id",purchaseId); purchase.put("transactionKey","a".repeat(64));
        db.getCollection("purchases").insertOne(purchase);
        var slot=db.getCollection("subscription_entitlements").find().first();
        slot.put("_id",UUID.randomUUID().toString()); slot.put("purchaseId",purchaseId); slot.put("sequence",2L);
        db.getCollection("subscription_entitlements").insertOne(slot);
        db.getCollection("payment_timelines").updateOne(new Document(),new Document("$set",new Document("nextSequence",3L)));
        assertThatThrownBy(()->reader(NOW).query(USER)).isInstanceOf(EntitlementQueryException.class);
    }
    @Test void recoveryBarrierFailsClosed() {
        db.getCollection("payment_recovery_controls").updateOne(new Document(),new Document("$set",new Document("barrier","ALL_BLOCKED")));
        assertThatThrownBy(()->reader(NOW).query(USER)).isInstanceOf(EntitlementQueryException.class);
    }
}
