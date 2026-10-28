package web.tosunsaeng.billing.domain.promotion;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.global.infrastructure.mongodb.EntitlementSnapshotExecutor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PromotionEligibilityServiceTest {
    static final String USER="30000000-0000-4000-8000-000000000001";
    final MongoTemplate mongo=mock(MongoTemplate.class);
    final PaidEntitlementReader paid=mock(PaidEntitlementReader.class);
    final EntitlementSnapshotExecutor snapshots=mock(EntitlementSnapshotExecutor.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"),ZoneOffset.UTC);
    PromotionEligibilityService service;
    Document account,life;
    List<Document> purchases=List.of();
    @BeforeEach void setup(){
        service=new PromotionEligibilityService(mongo,snapshots,paid,clock,"SANDBOX","fixture-issuer");
        when(snapshots.execute(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
        when(paid.read(eq(USER),any())).thenReturn(PaidQueryResponse.empty(false));
        var evidence=new Document("source","IDENTITY_USER_JWT").append("evidenceSchemaVersion",1L)
                .append("userId",USER).append("environment","SANDBOX").append("issuer","fixture-issuer")
                .append("accountType","MEMBER").append("tokenIssuedAt",clock.instant().toString()).append("verifiedAt",clock.instant().toString());
        account=new Document("_id","account").append("state","ACTIVE").append("memberEvidence",evidence);
        life=new Document("state","ACTIVE");
        when(mongo.find(any(Query.class),eq(Document.class),anyString())).thenAnswer(i->switch((String)i.getArgument(2)){
            case "payment_accounts"->List.of(account);case "payment_account_lifecycles"->List.of(life);
            case "purchases"->purchases;default->throw new AssertionError("Unexpected collection");});
        when(mongo.exists(any(Query.class),eq("subscription_entitlements"))).thenReturn(true);
    }
    Document successful(){return new Document("_id","purchase").append("purchaseState","VERIFIED").append("accessState","ALLOWED")
            .append("processingStatus","READY").append("firstVerifiedAt",new Date(1000)).append("productSnapshot",new Document("offerCode","PREMIUM_1D"));}
    Document refunded(long amount){return successful().append("purchaseState","REFUNDED").append("accessState","REVOKED")
            .append("money",new Document("basis","STORE_PAID").append("currency","KRW").append("amount",new Decimal128(100)))
            .append("refund",new Document("basis","STORE_REFUND_GROSS").append("currency","KRW").append("refundedTotal",new Decimal128(amount)));}
    @Test void historicalExpiredPurchaseQualifiesWithoutActiveEntitlement(){
        purchases=List.of(successful());assertThat(service.query(USER).eligible()).isTrue();
        verify(paid).prepare();
    }
    @Test void refundThenRepurchaseAndPartialRefundQualifyButFullOrUnknownAloneDoNot(){
        purchases=List.of(refunded(100));assertThat(service.query(USER).eligible()).isFalse();
        purchases=List.of(refunded(100),successful().append("_id","new-purchase"));assertThat(service.query(USER).eligible()).isTrue();
        purchases=List.of(refunded(50));assertThat(service.query(USER).eligible()).isTrue();
        purchases=List.of(refunded(50).append("refund",new Document("basis","UNKNOWN")));assertThat(service.query(USER).eligible()).isFalse();
    }
    @Test void missingEntitlementUncertainRecoveryAndWithdrawnAccountFailClosed(){
        purchases=List.of(successful());when(mongo.exists(any(Query.class),eq("subscription_entitlements"))).thenReturn(false);
        assertThat(service.query(USER).eligible()).isFalse();
        when(mongo.exists(any(Query.class),eq("subscription_entitlements"))).thenReturn(true);
        when(paid.read(eq(USER),any())).thenReturn(PaidQueryResponse.unknown(true));assertThat(service.query(USER).eligible()).isFalse();
        when(paid.read(eq(USER),any())).thenReturn(PaidQueryResponse.empty(false));life.put("state","WITHDRAWN");
        assertThat(service.query(USER).eligible()).isFalse();
        life.put("state","ACTIVE");account.put("state","WITHDRAWN");assertThat(service.query(USER).eligible()).isFalse();
    }
}
