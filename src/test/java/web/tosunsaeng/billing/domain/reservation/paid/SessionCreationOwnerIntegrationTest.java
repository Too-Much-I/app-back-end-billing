package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptSession;
import web.tosunsaeng.billing.domain.attempt.domain.model.SessionCreationOwner;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import static org.assertj.core.api.Assertions.*;

class SessionCreationOwnerIntegrationTest extends PaidReservationTestSupport {
    private AttemptSession session() { return mongo.findById("session", AttemptSession.class); }

    @Test void paidOwnerSurvivesConfirmReplayAndAccountRefresh() {
        purchase(); var runtime = runtime(); var result = runtime.reserve(command());
        var owner = session().getCreationOwner();
        assertThat(owner).isEqualTo(new SessionCreationOwner("test", SessionCreationOwner.Kind.MEMBER_ACCOUNT, USER, account().getString("_id"), null));
        accounts.getOrCreate(evidence());
        runtime.confirm(confirm(command(), result));
        assertThat(runtime.reserve(command())).isEqualTo(result);
        assertThat(session().getCreationOwner()).isEqualTo(owner);
        assertThatThrownBy(() -> session().withCreationOwner(new SessionCreationOwner("test", owner.kind(),
                "33333333-3333-4333-8333-333333333333", owner.reference(), null))).isInstanceOf(IllegalStateException.class);
        assertThat(owner.toString()).doesNotContain(USER, owner.reference());
    }

    @Test void memberPassHasAccountEvidenceWithoutInventedPhoneEpoch() {
        accounts.getOrCreate(evidence());
        tx.execute(c -> { c.grants().insert(EntitlementGrant.restoredTrial("restored", "test", USER, "original", 1L, NOW)); return true; });
        runtime().reserve(command());
        assertThat(session().getCreationOwner()).isEqualTo(new SessionCreationOwner("test", SessionCreationOwner.Kind.MEMBER_ACCOUNT, USER, account().getString("_id"), null));
    }

    @Test void trialEvidenceUsesOriginalSubjectAndEpochDespiteLaterLinkChange() {
        db.getCollection("trial_eligibility").insertOne(new Document("consumerScopeId", "scope").append("userId", USER)
                .append("state", "VERIFIED").append("lastEventId", "fake-event")
                .append("candidates", List.of(new Document("keyVersion", "fake-v1").append("value", "fake-candidate"))));
        tx.execute(c -> { c.operations().insert(web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition.freeExamOnce(NOW)); return true; });
        runtime().reserve(command());
        var created = session(); var owner = created.getCreationOwner();
        assertThat(owner).isEqualTo(new SessionCreationOwner("test", SessionCreationOwner.Kind.TRIAL_SUBJECT, USER, created.getSubjectRefId(), created.getSessionOwnerEpoch()));
        db.getCollection("billing_subject_links").updateOne(new Document("_id", created.getSubjectRefId()),
                new Document("$set", new Document("userId", "33333333-3333-4333-8333-333333333333")).append("$inc", new Document("sessionOwnerEpoch", 1L)));
        assertThat(session().getCreationOwner()).isEqualTo(owner);
    }

    @Test void legacyEvidenceRemainsAbsentOnReadAndCannotBeInferredFromAccount() {
        purchase(); runtime().reserve(command());
        db.getCollection("attempt_sessions").updateOne(new Document("_id", "session"), new Document("$unset", new Document("creationOwner", "")));
        assertThat(session().getCreationOwner()).isNull();
        runtime().reserve(command());
        assertThat(session().getCreationOwner()).isNull();
    }

    @Test void receiptFailureRollsBackSessionAndCreationEvidenceTogether() {
        purchase();
        db.runCommand(new Document("collMod", PaidExamStore.RECEIPTS).append("validator", new Document("state", "WAITING_MEMBER_EVIDENCE")));
        error(() -> runtime().reserve(command()), "BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(db.getCollection("attempt_sessions").countDocuments()).isZero();
        assertThat(db.getCollection("reservations").countDocuments()).isZero();
    }
}
