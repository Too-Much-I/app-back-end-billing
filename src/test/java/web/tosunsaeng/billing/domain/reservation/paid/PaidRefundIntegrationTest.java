package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import static org.assertj.core.api.Assertions.*;

class PaidRefundIntegrationTest extends PaidReservationTestSupport {
    PurchaseCore core;
    PurchaseObservation refund;
    void prepareRefund() {
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),new Document("$set",new Document("barrier","ALL_BLOCKED")));
        RefundCoreSchema.upgradeUnderMaintenance(db,true);
        db.getCollection(PaymentRecoveryFence.COLLECTION).updateOne(new Document(),new Document("$set",new Document("barrier","OPEN")));
        properties.getRefund().setProcessingEnabled(true);
        core=new PurchaseCore(payment,properties,clock,Map.of(PurchaseObservation.Store.PLAY_STORE,"fake.app"),"fake-v1");
        var purchase=db.getCollection("purchases").find().first();
        refund=new PurchaseObservation("SANDBOX",PurchaseObservation.Store.PLAY_STORE,"fake.app",purchase.getString("transactionKey"),
                "fake-v1","a".repeat(64),PurchaseObservation.Kind.REFUND,account().getString("activeRefId"),"PREMIUM_1D",NOW.minusSeconds(10),null,
                new RefundEvidence(RefundEvidence.Source.REVENUECAT_API,"b".repeat(64),NOW,null,null));
        core.receive(refund);
    }
    @Test void refundFirstAtSameMillisecondRejectsConfirmAndDoesNotRestoreTrial() {
        purchase();String old=oldTrial();var cmd=transition(old);var result=runtime().reserve(cmd);prepareRefund();
        core.apply(refund.providerEventKey());
        error(()->runtime().confirm(confirm(cmd,result)),"ENTITLEMENT_REVOKED");
        assertThat(service.status(USER,OP).orElseThrow().reservationStatus()).isEqualTo(Reservation.Status.CANCELED);
        assertThat(db.getCollection("entitlement_grants").countDocuments(new Document("sourceType","TRIAL_PAID_RESTORATION"))).isZero();
    }
    @Test void confirmFirstAtSameMillisecondPreservesSessionAndRestoredFreePass() {
        purchase();String old=oldTrial();var cmd=transition(old);var result=runtime().reserve(cmd);
        var confirmed=runtime().confirm(confirm(cmd,result));
        var sessions=db.getCollection("attempt_sessions").find().into(new ArrayList<>());
        var grants=db.getCollection("entitlement_grants").find().into(new ArrayList<>());
        prepareRefund();core.apply(refund.providerEventKey());
        assertThat(runtime().confirm(confirm(cmd,result)).snapshot()).isEqualTo(confirmed.snapshot());
        assertThat(db.getCollection("attempt_sessions").find().into(new ArrayList<>())).isEqualTo(sessions);
        assertThat(db.getCollection("entitlement_grants").find().into(new ArrayList<>())).isEqualTo(grants);
    }
    @Test void concurrentConfirmAndRefundConvergeByRootCommitNotTimestamp() throws Exception {
        purchase();var cmd=command();var result=runtime().reserve(cmd);prepareRefund();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();try {runtime().confirm(confirm(cmd,result));return true;}
                catch(InternalApiException e){assertThat(e.code()).isEqualTo("ENTITLEMENT_REVOKED");return false;}});
            var b=pool.submit(()->{start.await();return core.apply(refund.providerEventKey());});start.countDown();b.get();
            boolean confirmed = a.get();
            var status=service.status(USER,OP).orElseThrow();
            assertThat(status.reservationStatus()).isEqualTo(confirmed?Reservation.Status.CONFIRMED:Reservation.Status.CANCELED);
        }
        assertThat(db.getCollection("purchases").find().first().getString("accessState")).isEqualTo("REVOKED");
        assertThat(db.getCollection("payment_ledger").countDocuments(new Document("type","ACCESS_REVOKED"))).isOne();
    }
}
