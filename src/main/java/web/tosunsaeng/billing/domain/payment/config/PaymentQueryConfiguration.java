package web.tosunsaeng.billing.domain.payment.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseCoreSchema;
import web.tosunsaeng.billing.domain.payment.query.PaidEntitlementReader;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncSchema;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

@Configuration
@ConditionalOnProperty(prefix = "billing.payment", name = "entitlement-query-enabled", havingValue = "true")
public class PaymentQueryConfiguration {
    @Bean PaidEntitlementReader paidEntitlementReader(MongoTemplate mongo, PaymentProperties p) { return new PaidEntitlementReader(mongo, p); }
    @Bean ApplicationRunner paymentQuerySchemaVerifier(PaymentStore store, PaymentProperties p) {
        return args -> {
            PaymentFoundationInitializer.verify(store.database());
            PurchaseCoreSchema.verify(store.database()); PaymentSyncSchema.verify(store.database());
            PaymentRecoverySchema.verify(store.database()); new PaymentRecoveryFence(store, p).capture();
        };
    }
}
