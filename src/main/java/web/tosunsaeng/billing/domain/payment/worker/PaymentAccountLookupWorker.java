package web.tosunsaeng.billing.domain.payment.worker;

import java.time.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.*;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Explicit C2 worker, one external page per account/tick; never used as a no-purchase verifier. */
public final class PaymentAccountLookupWorker {
    public record Tick(int claimed, int completed, int deferred, int failed) {}
    private final PaymentStore store;
    private final PaymentAccountLookupWork work;
    private final PurchaseCore core;
    private final PaymentSyncService sync;
    private final RevenueCatReadClient reader;
    private final Clock clock;
    private final DoubleSupplier jitter;
    private final String environment;
    private final PurchaseReferenceStore references;
    public PaymentAccountLookupWorker(PaymentStore store, PaymentProperties p, Clock clock, String project,
            PurchaseLookupKey lookup, PurchaseReferenceCipher cipher, PurchaseCore core, PaymentSyncService sync,
            RevenueCatReadClient reader, DoubleSupplier jitter) {
        this.store = store; this.clock = clock; this.core = core; this.sync = sync; this.reader = reader;
        this.environment = p.getEnvironment(); this.jitter = jitter;
        work = new PaymentAccountLookupWork(store, p, clock, project, lookup, cipher); PaymentSyncSchema.verify(store.database());
        references = new PurchaseReferenceStore(store, p, clock, project, cipher);
    }
    public Tick tick(int batch) {
        return tick(batch, false);
    }
    public Tick tickPeriodic(int batch) { return tick(batch, true); }
    private Tick tick(int batch, boolean periodic) {
        int claimed = 0, completed = 0, deferred = 0, failed = 0;
        for (String id : periodic ? work.duePeriodic(batch) : work.due(batch)) {
            PaymentAccountLookupWork.Lease lease = null;
            try {
                lease = work.claim(id, periodic); if (lease == null) continue; claimed++;
                if (process(lease)) completed++; else deferred++;
            } catch (RuntimeException error) {
                failed++;
                if (lease != null) try {
                    if (error instanceof RevenueCatFailure f) {
                        switch (f.kind()) {
                            case AUTH -> handoff(lease, PaymentSyncService.LookupFailure.BLOCKED_AUTH);
                            case CONTRACT, REVIEW -> handoff(lease, PaymentSyncService.LookupFailure.PROVIDER_CONTRACT);
                            case NOT_FOUND, RETRY -> work.defer(lease, retryAt(lease, f.retryAt()), f.kind().name());
                        }
                    } else if (error instanceof PaymentException p && p.status() == 409) {
                        handoff(lease, PaymentSyncService.LookupFailure.PROVIDER_CONTRACT);
                    } else work.defer(lease, retryAt(lease, null), "TEMPORARILY_UNAVAILABLE");
                } catch (RuntimeException lostLeaseOrDatabase) { /* Never recapture authority to store a stale response. */ }
            }
        }
        return new Tick(claimed, completed, deferred, failed);
    }
    private boolean process(PaymentAccountLookupWork.Lease l) {
        if (l.failure() != null) {
            for (var reason : PaymentSyncService.LookupFailure.values())
                if (reason.name().equals(l.failure()) && reason != PaymentSyncService.LookupFailure.RETRY_EXHAUSTED) {
                    handoff(l, reason); return false;
                }
        }
        settle(l);
        if (l.periodic() && !clock.instant().isBefore(l.scanStartedAt().plus(Duration.ofHours(24)))) {
            handoff(l, PaymentSyncService.LookupFailure.PROVIDER_REVIEW); return false;
        }
        for (Document command : work.commands(l)) {
            if (!clock.instant().isBefore(PaymentSyncService.retryStartedAt(command).plus(Duration.ofHours(24)))) {
                work.renew(l);
                sync.handoffLookup(command.getString("_id"), command.getLong("version"), PaymentSyncService.LookupFailure.RETRY_EXHAUSTED,
                        l.ticket(), s -> work.guard(s, l));
            }
        }
        if (!l.periodic() && !work.hasPending(l)) { work.checkpoint(l, null, clock.instant()); return true; }
        if (work.hasExpired(l)) { work.defer(l, clock.instant().plusSeconds(1), "EXPIRY_DRAIN"); return false; }
        if (!l.periodic() && !work.hasUnbound(l)) { work.defer(l, retryAt(l, null), "SYNC_PENDING"); return false; }
        if (l.pages() >= 1000) { handoff(l, PaymentSyncService.LookupFailure.PAGE_LIMIT); return false; }
        work.renew(l);
        // The reader uses a budgeted, bounded HTTP transport. No database transaction spans HTTP.
        var page = reader.purchases(l.reference(), l.cursor());
        boolean review = false;
        for (var item : page.items()) {
            if (item.observation() == null) { review = true; continue; }
            var observation = item.observation();
            require(environment.equals(observation.environment()) && l.reference().equals(observation.ownerRefId()));
            work.renew(l);
            core.receive(observation, l.ticket(), s -> work.guard(s, l));
            var outcome = core.apply(observation.providerEventKey(), l.ticket(), s -> work.guard(s, l));
            if (outcome == PurchaseCore.Outcome.APPLIED) {
                work.renew(l);
                references.remember(observation, item.reference(), l.accountId(), l.ticket(), s -> work.guard(s, l));
            }
        }
        settle(l);
        if (review) { handoff(l, PaymentSyncService.LookupFailure.PROVIDER_REVIEW); return false; }
        // A replay after a crash before this checkpoint uses the same observation keys, never pays twice.
        work.checkpoint(l, page.nextCursor(), page.nextCursor() == null ? retryAt(l, null) : clock.instant().plusSeconds(1));
        return l.periodic() ? page.nextCursor() == null : !work.hasPending(l);
    }
    private void settle(PaymentAccountLookupWork.Lease l) {
        for (Document command : work.commands(l)) {
            Document source = source(command);
            if (source == null) {
                work.renew(l);
                sync.completeVerifiedPurchase(command.getString("_id"), command.getLong("version"), l.ticket(), s -> work.guard(s, l));
                continue;
            }
            String event = source.getString("providerEventKey"); work.renew(l);
            if ("RECEIVED".equals(source.getString("state"))) core.apply(event, l.ticket(), s -> work.guard(s, l));
            long version = sync.bindObservation(command.getString("_id"), command.getLong("version"), event, l.ticket(), s -> work.guard(s, l));
            sync.complete(command.getString("_id"), version, l.ticket(), s -> work.guard(s, l));
        }
    }
    private Document source(Document command) {
        Document q = new Document("environment", environment).append("accountId", command.get("accountId")).append("lifecycleId", command.get("lifecycleId"));
        String bound = command.getString("sourceObservationKey");
        if (bound != null) q.append("providerEventKey", bound);
        for (String field : List.of("environment", "store", "appId", "transactionKey", "lookupKeyVersion")) q.append("normalizedObservation." + field, command.get(field));
        Document result = store.database().getCollection("payment_purchase_cases").find(q).first();
        if (result == null) result = store.database().getCollection("payment_provider_inbox").find(q).sort(new Document("receivedAt", 1).append("_id", 1)).first();
        return result;
    }
    private void handoff(PaymentAccountLookupWork.Lease l, PaymentSyncService.LookupFailure reason) {
        work.markHandoff(l, reason.name());
        for (Document command : work.commands(l)) {
            work.renew(l);
            sync.handoffLookup(command.getString("_id"), command.getLong("version"), reason, l.ticket(), s -> work.guard(s, l));
        }
        // Bounded batches must not park the remaining commands without recording their disposition.
        work.defer(l, work.hasPending(l) ? clock.instant().plusSeconds(1) : null, reason.name());
    }
    private Instant retryAt(PaymentAccountLookupWork.Lease l, Instant provider) {
        double random = jitter.getAsDouble(); require(random >= 0 && random < 1);
        long base = Math.min(3600, 5L << (int) Math.min(10, l.attempts() - 1));
        Instant local = clock.instant().plusMillis(Math.min(3_600_000, (long) (base * 1000 * (1 + random))));
        return provider != null && provider.isAfter(local) ? provider : local;
    }
}
