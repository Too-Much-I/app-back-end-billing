package web.tosunsaeng.billing.domain.reward;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.reservation.paid.*;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.*;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** All reward, quota, ledger and receipt writes use the existing explicit transaction session. */
public class InviteRewardService {
    public static final String ACCOUNTS = "invite_reward_accounts", RECEIPTS = "invite_reward_receipts";
    public static final int LIMIT = 3;
    public record Quota(int awardedCount, int limit, int remainingAwards) {}
    public record Award(String operationId, int awardedQuantity, int awardedCount, int limit, int remainingAwards) {}
    private final PaidExamTransactions tx;
    private final PaymentStore store;
    private final PaymentProperties payment;
    private final PaymentRecoveryFence fence;
    private final String environment, issuer;
    private final Clock clock;
    public InviteRewardService(PaidExamTransactions tx, PaymentStore store, PaymentProperties payment,
            String environment, String issuer, Clock clock) {
        this.tx = tx; this.store = store; this.payment = payment; this.environment = environment;
        this.issuer = issuer; this.clock = clock; this.fence = new PaymentRecoveryFence(store, payment);
        require(payment.getRecovery().isFenceRequired() && issuer != null && !issuer.isBlank()
                && (Set.of("test", "staging").contains(environment) && "SANDBOX".equals(payment.getEnvironment())
                || "production".equals(environment) && "PRODUCTION".equals(payment.getEnvironment())));
    }
    public Quota read(String user) {
        storageReady();
        var ticket = fence.capture();
        return tx.execute(c -> {
            fence.check(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            Document account = account(c, user, false);
            int count = count(c, user, account);
            return new Quota(count, LIMIT, LIMIT - count);
        });
    }
    public Award award(VerifiedMemberEvidence evidence, String operationId) {
        if (operationId == null || !operationId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new PaymentException(400, "INVALID_REQUEST");
        require(evidence != null && payment.getEnvironment().equals(evidence.environment()));
        storageReady();
        var ticket = fence.capture();
        String awardId = UUID.randomUUID().toString(), grantId = UUID.randomUUID().toString(), ledgerId = UUID.randomUUID().toString();
        String user = evidence.userId();
        Instant now = clock.instant();
        Document result = tx.execute(c -> {
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            deadline(evidence);
            Document account = account(c, user, true);
            Document proof = account.get("memberEvidence", Document.class);
            if (proof == null) throw new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED");
            require(VerifiedMemberEvidence.matches(proof, user, payment.getEnvironment(), issuer));
            Document old = one(c, RECEIPTS, key(user).append("operationId", operationId));
            int count = count(c, user, account);
            if (old != null) {
                require(account.getString("_id").equals(old.getString("accountId")));
                deadline(evidence);
                return old;
            }
            Document receipt = key(user).append("_id", awardId).append("schemaVersion", 1L)
                    .append("accountId", account.getString("_id")).append("operationId", operationId)
                    .append("terminalAt", ceilingMillis(now)).append("detailEligibleAt", ceilingMillis(now.plus(Duration.ofDays(120))));
            if (count == LIMIT) {
                receipt.append("state", "LIMIT_REACHED").append("httpStatus", 409).append("errorCode", "INVITE_REWARD_LIMIT_REACHED")
                        .append("grantId", null).append("ledgerEventId", null).append("responseSnapshot", null);
            } else {
                Document state = one(c, ACCOUNTS, key(user));
                if (state == null) {
                    c.operations().insert(key(user).append("_id", UUID.randomUUID().toString()).append("schemaVersion", 1L)
                            .append("accountId", account.getString("_id")).append("awardedCount", 1).append("version", 1L)
                            .append("createdAt", Date.from(now)).append("updatedAt", Date.from(now)), ACCOUNTS);
                } else {
                    require(c.operations().updateFirst(new BasicQuery(key(user).append("version", state.getLong("version")).append("awardedCount", count)),
                            new Update().inc("awardedCount", 1).inc("version", 1L).set("updatedAt", Date.from(now)), ACCOUNTS).getModifiedCount() == 1);
                }
                Document guard = one(c, PaidExamStore.GUARDS, key(user));
                if (guard == null) {
                    require(!c.operations().exists(new BasicQuery(new Document("environment", environment).append("ownerUserId", user)), "entitlement_grants"));
                    guard = c.paid().createGuard(environment, user, new Document("kind", "MEMBER_ACCOUNT")
                            .append("reference", account.getString("_id")).append("version", account.getLong("version")).append("epoch", null));
                }
                Document owner = guard.get("owner", Document.class);
                require(owner != null && "MEMBER_ACCOUNT".equals(owner.getString("kind")) && owner.get("epoch") == null
                        && account.getString("_id").equals(owner.getString("reference")) && positive(owner, "version")
                        && owner.getLong("version") <= account.getLong("version") && positive(guard, "version") && positive(guard, "nextGrantOrder"));
                long order = c.paid().nextGrantOrder(environment, user, guard.getLong("version"));
                var grant = EntitlementGrant.invite(grantId, environment, user, awardId, order, now);
                c.grants().insert(grant);
                c.ledger().insert(EntitlementLedgerEntry.invite(ledgerId, grant, now));
                receipt.append("state", "AWARDED").append("httpStatus", 200).append("errorCode", null)
                        .append("grantId", grantId).append("ledgerEventId", ledgerId)
                        .append("responseSnapshot", new Document("operationId", operationId).append("awardedQuantity", 1)
                                .append("awardedCount", count + 1).append("limit", LIMIT).append("remainingAwards", LIMIT - count - 1));
            }
            c.operations().insert(receipt, RECEIPTS);
            deadline(evidence);
            return receipt;
        });
        // Terminal rejection must commit before it becomes an HTTP error.
        if ("LIMIT_REACHED".equals(result.getString("state"))) throw new PaymentException(409, "INVITE_REWARD_LIMIT_REACHED");
        require("AWARDED".equals(result.getString("state")));
        Document snapshot = result.get("responseSnapshot", Document.class);
        return new Award(snapshot.getString("operationId"), snapshot.getInteger("awardedQuantity"), snapshot.getInteger("awardedCount"),
                snapshot.getInteger("limit"), snapshot.getInteger("remainingAwards"));
    }
    private Document account(PaidExamTransactions.Context c, String user, boolean write) {
        Document query = new Document("environment", payment.getEnvironment()).append("userId", user);
        Document life = one(c, "payment_account_lifecycles", query), account = one(c, "payment_accounts", query);
        if (life != null && "WITHDRAWN".equals(life.getString("state")) || account != null && "WITHDRAWN".equals(account.getString("state")))
            throw new PaymentException(403, "PAYMENT_ACCOUNT_WITHDRAWN");
        if (life == null && account == null) {
            if (write) throw new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED");
            return null;
        }
        require(life != null && account != null && "ACTIVE".equals(life.getString("state")) && "ACTIVE".equals(account.getString("state"))
                && positive(life, "version") && positive(account, "version"));
        if (write) require(c.operations().updateFirst(new BasicQuery(new Document("_id", life.get("_id")).append("version", life.getLong("version"))
                        .append("state", "ACTIVE")), new Update().inc("version", 1L), "payment_account_lifecycles").getModifiedCount() == 1);
        return account;
    }
    private int count(PaidExamTransactions.Context c, String user, Document account) {
        Document state = one(c, ACCOUNTS, key(user));
        // Bounded retained grants and their issuance ledgers survive receipt cleanup and consumption.
        var grants = c.operations().find(new BasicQuery(new Document("environment", environment).append("ownerUserId", user)
                .append("sourceType", "INVITE")).limit(4), Document.class, "entitlement_grants");
        var issuance = c.operations().find(new BasicQuery(new Document("environment", environment).append("ownerUserId", user)
                .append("sourceType", "INVITE")).limit(4), Document.class, "entitlement_ledger");
        if (state == null) {
            require(grants.isEmpty() && issuance.isEmpty() && !c.operations().exists(new BasicQuery(key(user)), RECEIPTS));
            return 0;
        }
        require(account != null && account.getString("_id").equals(state.getString("accountId")) && positive(state, "version")
                && state.get("awardedCount") instanceof Integer n && n >= 1 && n <= LIMIT);
        int count = state.getInteger("awardedCount");
        require(grants.size() == count && issuance.size() == count);
        for (Document grant : grants) {
            Document ledger = one(c, "entitlement_ledger", new Document("aggregateId", grant.getString("_id")).append("eventType", "GRANTED"));
            require(ledger != null && ("INVITE:" + environment + ":" + grant.getString("sourceId")).equals(ledger.getString("dedupeKey")));
        }
        return count;
    }
    private static Date ceilingMillis(Instant time) {
        return new Date(time.toEpochMilli() + (time.getNano() % 1_000_000 == 0 ? 0 : 1));
    }
    private void storageReady() {
        require(store.database().listCollectionNames().into(new ArrayList<>()).containsAll(List.of(
                ACCOUNTS, RECEIPTS, "payment_accounts", "payment_account_lifecycles", "entitlement_grants", "entitlement_ledger")));
    }
    private void deadline(VerifiedMemberEvidence evidence) {
        if (!clock.instant().isBefore(evidence.authorizationDeadline())) throw new PaymentException(401, "UNAUTHENTICATED");
    }
    private Document key(String user) { return new Document("environment", environment).append("userId", user); }
    private static boolean positive(Document d, String f) { return d.get(f) instanceof Long n && n > 0 && n < Long.MAX_VALUE; }
    private static Document one(PaidExamTransactions.Context c, String collection, Document key) {
        var rows = c.operations().find(new BasicQuery(key).limit(2), Document.class, collection);
        require(rows.size() <= 1); return rows.isEmpty() ? null : rows.getFirst();
    }
}
