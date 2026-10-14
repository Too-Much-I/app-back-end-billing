package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.MongoClient;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import web.tosunsaeng.billing.domain.payment.config.*;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.recovery.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncService;
import web.tosunsaeng.billing.global.config.mongodb.BillingMongoProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PaymentProviderWorkerProperties.class, RevenueCatWebhookProperties.class})
public class PaymentProviderWorkerConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "billing.payment", name = "sync-enabled", havingValue = "true")
    PaymentSyncService publicPaymentSyncService(PaymentProviderWorkerRuntime runtime, PaymentProviderWorkerProperties config) {
        if (!config.isEnabled()) throw new IllegalStateException("Payment sync requires enabled provider worker");
        return runtime.syncService();
    }
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "billing.payment.provider.worker", name = "enabled", havingValue = "true")
    PaymentProviderWorkerRuntime paymentProviderWorkerRuntime(PaymentStore store, MongoClient mongoClient, PaymentProperties p,
            BillingMongoProperties mongo, RevenueCatWebhookProperties provider, PaymentProviderWorkerProperties config, Clock clock, MeterRegistry metrics,
            ObjectProvider<PaymentProviderAlerts.Sink> alertSink) {
        validate(p, mongo, config, store.database().getName());
        PaymentFoundationInitializer.verify(store.database()); PaymentRecoverySchema.verify(store.database());
        new PaymentRecoveryFence(store, p).capture();
        PurchaseLookupKey lookup;
        PurchaseReferenceCipher cipher;
        try {
            lookup = new PurchaseLookupKey(Base64.getDecoder().decode(provider.getLookupKey()), provider.getLookupKeyVersion());
            cipher = new PurchaseReferenceCipher(config.getReferenceKeyId(), Base64.getDecoder().decode(config.getReferenceKey()));
        } catch (RuntimeException invalid) { throw new IllegalStateException("Invalid provider worker key configuration"); }
        Map<PurchaseObservation.Store, String> apps = new EnumMap<>(PurchaseObservation.Store.class);
        provider.getApps().forEach((id, a) -> {
            if (a.getStore() == null || a.getProducts().isEmpty() || apps.putIfAbsent(a.getStore(), id) != null)
                throw new IllegalStateException("Ambiguous provider worker app mapping");
        });
        if (apps.isEmpty()) throw new IllegalStateException("Missing provider worker app mapping");
        Map<String, RevenueCatPurchaseDecoder.Product> products = new LinkedHashMap<>();
        var rows = store.database().getCollection("payment_products").find(new Document("environment", p.getEnvironment())).limit(1001).into(new ArrayList<>());
        if (rows.isEmpty() || rows.size() > 1000) throw new IllegalStateException("Invalid provider worker catalog size");
        for (Document row : rows) {
            var app = provider.getApps().get(row.getString("appId"));
            if (app == null || !app.getStore().name().equals(row.getString("store")) || !app.getProducts().contains(row.getString("storeProductId")))
                throw new IllegalStateException("Provider worker catalog mapping mismatch");
            var product = new RevenueCatPurchaseDecoder.Product(row.getString("appId"), app.getStore(), row.getString("storeProductId"));
            if (products.putIfAbsent(row.getString("rcProductId"), product) != null) throw new IllegalStateException("Duplicate provider product mapping");
        }
        var core = new PurchaseCore(store, p, clock, apps, lookup.version());
        var sync = new PaymentSyncService(store, p, clock, lookup, apps);
        // Explicit separate DB on the configured cluster. Every environment sharing the RC project must use this same DB.
        var shared = new PaymentStore(mongoClient, new SimpleMongoClientDatabaseFactory(mongoClient, config.getBudgetDatabase()));
        var budget = new RevenueCatProjectBudget(shared, provider.getProject(), clock);
        var alerts = new PaymentProviderAlerts(store, p.getEnvironment(), clock, budget::isAuthBlocked, metrics,
                config.isAlertsEnabled(), config.isAlertsEnabled() ? alertSink.getIfAvailable() : null);
        var decoder = new RevenueCatPurchaseDecoder(provider.getProject(), p.getEnvironment(), lookup, products, clock);
        var source = new PaymentSourceWorker(store, p, core, sync, clock, () -> ThreadLocalRandom.current().nextDouble());
        var retention = new PaymentRetentionWorker(store, p, core, clock, config.isTerminalSyncDeleteEnabled());
        var references = new PaymentReferenceRetentionWorker(store, p, clock, config.isReferenceRetentionEnabled());
        PaymentAccountLookupSchema.verify(store.database());
        var http = new RevenueCatHttpTransport();
        try {
            var reader = new RevenueCatReadClient(provider.getProject(), p.getEnvironment(), config.getApiKey(), new RevenueCatBudgetedTransport(budget, http), decoder, clock);
            var account = new PaymentAccountLookupWorker(store, p, clock, provider.getProject(), lookup, cipher, core, sync, reader, () -> ThreadLocalRandom.current().nextDouble());
            return new PaymentProviderWorkerRuntime(() -> {
                var tick = source.tick(config.getBatch()); report(metrics, "source", tick.claimed(), tick.failed());
            }, () -> {
                var tick = account.tick(config.getBatch()); report(metrics, "account", tick.claimed(), tick.failed());
                if (config.isReconciliationEnabled()) {
                    var scan = account.tickPeriodic(config.getBatch()); report(metrics, "reconciliation", scan.claimed(), scan.failed());
                }
            }, new PaymentHourlyTask(clock, () -> {
                try {
                    var tick = retention.tick(config.getBatch());
                    metrics.counter("billing_payment_retention_handed_off").increment(tick.handedOff());
                    metrics.counter("billing_payment_retention_candidates").increment(tick.candidates());
                    metrics.counter("billing_payment_retention_deleted").increment(tick.deleted());
                    metrics.counter("billing_payment_retention_failed").increment(tick.failed());
                    var cleanup = references.tick(config.getBatch());
                    metrics.counter("billing_payment_reference_inspected").increment(cleanup.inspected());
                    metrics.counter("billing_payment_reference_eligible").increment(cleanup.eligible());
                    metrics.counter("billing_payment_reference_deleted").increment(cleanup.deleted());
                    metrics.counter("billing_payment_lookup_leases_released").increment(cleanup.leasesReleased());
                    metrics.counter("billing_payment_reference_retention_failed").increment(cleanup.failed());
                } finally { alerts.poll(); }
            }), http::close, metrics, config.getPollMillis(), sync);
        } catch (RuntimeException error) { http.close(); throw error; }
    }
    @Bean ApplicationListener<ApplicationReadyEvent> paymentProviderWorkerReady(ObjectProvider<PaymentProviderWorkerRuntime> runtime) {
        return event -> runtime.ifAvailable(PaymentProviderWorkerRuntime::start);
    }
    public static void validate(PaymentProperties p, BillingMongoProperties mongo, PaymentProviderWorkerProperties config, String billingDb) {
        if (!p.getRecovery().isFenceRequired() || !mongo.isRequireTransactions() || !mongo.isInitializeIndexes()
                || !Set.of("SANDBOX", "PRODUCTION").contains(p.getEnvironment()) || config.getBatch() < 1 || config.getBatch() > 100
                || config.getPollMillis() < 1000 || config.getPollMillis() > 60000
                || config.getBudgetDatabase() == null || !config.getBudgetDatabase().matches("[A-Za-z0-9_-]{1,63}")
                || config.getBudgetDatabase().equals(billingDb) || Set.of("admin", "local", "config").contains(config.getBudgetDatabase()))
            throw new IllegalStateException("Provider worker requires transactional schemas, explicit shared budget database and bounded configuration");
    }
    private static void report(MeterRegistry metrics, String phase, int claimed, int failed) {
        metrics.counter("billing_payment_provider_claimed", "phase", phase).increment(claimed);
        metrics.counter("billing_payment_provider_failed", "phase", phase).increment(failed);
    }
}
