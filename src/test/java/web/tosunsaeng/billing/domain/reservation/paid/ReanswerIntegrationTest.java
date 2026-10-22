package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import web.tosunsaeng.billing.domain.reanswer.*;
import web.tosunsaeng.billing.domain.entitlement.application.*;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementQueryRepository;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.*;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader;
import static org.assertj.core.api.Assertions.*;

class ReanswerIntegrationTest extends PaidReservationTestSupport {
    @BeforeEach void prepareQuery() {
        purchase();
        var existing = db.listCollectionNames().into(new ArrayList<>());
        for (String name : List.of("payment_sync_commands", "trial_eligibility", "trial_candidate_aliases", "trial_claims",
                "billing_subject_links", "benefit_definitions", "idempotency_commands"))
            if (!existing.contains(name)) db.createCollection(name);
        mongo.insert(BenefitDefinition.freeExamOnce(NOW));
        db.getCollection("payment_recovery_controls").updateOne(new Document(), new Document("$set",new Document("schemaVersion",4L)));
    }
    ReanswerService reader(Instant now) {
        var trial = new TrialEligibilityProperties(); trial.setExpectedConsumerScopeId("scope");
        return new ReanswerService(mongo, snapshots, new PaidEntitlementReader(mongo,properties),
                new EntitlementQueryRepository(mongo,trial), new TrialEntitlementQueryEvaluator(),
                Clock.fixed(now,ZoneOffset.UTC),"test","SANDBOX",ISSUER);
    }
    Instant expired() { return NOW.plusSeconds(86400); }
    void verifiedPhone() {
        db.getCollection("trial_eligibility").insertOne(new Document("consumerScopeId","scope").append("userId",USER)
                .append("state","VERIFIED").append("candidates",List.of(new Document("keyVersion","fake-v1").append("value","fake-candidate"))));
    }
    void issuedTrial() {
        verifiedPhone();
        mongo.insert(TrialClaim.active("claim","FREE_EXAM_ONCE","subject","fake-event",NOW,NOW.plusSeconds(999999)));
        mongo.insert(TrialCandidateAlias.active("alias","FREE_EXAM_ONCE","fake-v1","fake-candidate","claim",NOW,NOW.plusSeconds(999999)));
        mongo.insert(BillingSubjectLink.active("subject","claim","scope",USER,NOW,NOW.plusSeconds(999999)));
        mongo.insert(EntitlementGrant.unitGrant("trial-grant","FREE_EXAM_ONCE","TRIAL_CLAIM","claim","subject",1,NOW));
    }
    Map<String,List<Document>> snapshot() {
        Map<String,List<Document>> rows = new TreeMap<>();
        for (String collection : db.listCollectionNames()) rows.put(collection,db.getCollection(collection).find().into(new ArrayList<>()));
        return rows;
    }
    @Test void paidWithUnknownPhoneAndPendingSyncAllowedWithoutAnyWrites() {
        db.getCollection("payment_sync_commands").insertOne(new Document("environment","SANDBOX").append("accountId",account().getString("_id")).append("state","PENDING"));
        var before = snapshot();
        var result = reader(NOW.plusNanos(123456789)).query(USER);
        assertThat(result.decision()).isEqualTo("ALLOWED");
        assertThat(result.checkedAt()).endsWith(".123456789Z");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void unissuedTrialExcludedAndUnknownPhoneIsPending() {
        assertThat(reader(expired()).query(USER).decision()).isEqualTo("PENDING");
        verifiedPhone();
        var before = snapshot();
        assertThat(reader(expired()).query(USER).reason()).isEqualTo("ENTITLEMENT_INSUFFICIENT");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void issuedTrialIsUsableAndRevokedPhoneCannotUseIt() {
        issuedTrial();
        assertThat(reader(expired()).query(USER).decision()).isEqualTo("ALLOWED");
        db.getCollection("trial_eligibility").updateOne(new Document(),new Document("$set",new Document("state","REVOKED")));
        assertThat(reader(expired()).query(USER).decision()).isEqualTo("DENIED");
    }
    @Test void memberPassAllowsDespiteUncertainPaidAndPhoneButEvidenceRequired() {
        mongo.insert(EntitlementGrant.invite("invite","test",USER,"award",1L,NOW));
        db.getCollection("purchases").updateOne(new Document(),new Document("$set",new Document("processingStatus","REVIEW_REQUIRED")));
        assertThat(reader(NOW).query(USER).decision()).isEqualTo("ALLOWED");
        db.getCollection("payment_accounts").updateOne(new Document("userId",USER),new Document("$unset",new Document("memberEvidence","")));
        assertThat(reader(NOW).query(USER).decision()).isEqualTo("PENDING");
    }
    @Test void withdrawnWinsOverPaidAndFree() {
        mongo.insert(EntitlementGrant.restoredTrial("restored","test",USER,"consumption",1L,NOW));
        db.getCollection("payment_account_lifecycles").updateOne(new Document("userId",USER),new Document("$set",new Document("state","WITHDRAWN")));
        var before = snapshot();
        assertThat(reader(NOW).query(USER).reason()).isEqualTo("ACCOUNT_WITHDRAWN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void referralPassAllowsReadOnlyReanswerAfterPaidExpiry() {
        mongo.insert(EntitlementGrant.referral("referral", "test", USER, "application:APPLICANT", 1L, NOW));
        var before = snapshot();
        assertThat(reader(expired()).query(USER).decision()).isEqualTo("ALLOWED");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void heldOrConsumedMemberPassExcludedAndPendingGuardDoesNotBlockAvailablePass() {
        verifiedPhone();
        mongo.insert(EntitlementGrant.invite("invite","test",USER,"award",1L,NOW));
        tx.execute(c -> {
            c.paid().createGuard("test",USER,new Document("kind","MEMBER_ACCOUNT").append("reference",account().getString("_id"))
                    .append("version",account().getLong("version")).append("epoch",null));
            c.paid().acquire("test",USER,1L,OP,"reservation","group"); return true;
        });
        assertThat(reader(expired()).query(USER).decision()).isEqualTo("ALLOWED");
        for (String field : List.of("heldUnits","consumedUnits")) {
            db.getCollection("entitlement_grants").updateOne(new Document("_id","invite"),
                    new Document("$set",new Document("availableUnits",0).append("heldUnits",0).append("consumedUnits",0).append(field,1)));
            assertThat(reader(expired()).query(USER).decision()).isEqualTo("DENIED");
        }
    }
    @Test void paidExpiryIsStrictAndValidUntilCapped() {
        verifiedPhone();
        Instant end = db.getCollection("subscription_entitlements").find().first().getDate("endsAt").toInstant();
        assertThat(reader(end.minusNanos(1)).query(USER).validUntil()).isEqualTo(end.toString());
        assertThat(reader(end).query(USER).decision()).isEqualTo("DENIED");
    }
    @Test void recoveryBarrierOrCorruptGrantFailClosed() {
        mongo.insert(EntitlementGrant.invite("invite","test",USER,"award",1L,NOW));
        db.getCollection("entitlement_grants").updateOne(new Document("_id","invite"),new Document("$set",new Document("heldUnits",1)));
        assertThatThrownBy(() -> reader(NOW).query(USER)).isInstanceOf(web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.class);
        db.getCollection("payment_recovery_controls").updateOne(new Document(),new Document("$set",new Document("barrier","ALL_BLOCKED")));
        assertThatThrownBy(() -> reader(NOW).query(USER)).isInstanceOf(web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException.class);
    }
}
