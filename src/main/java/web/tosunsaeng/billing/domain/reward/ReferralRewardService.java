package web.tosunsaeng.billing.domain.reward;

import java.security.SecureRandom;
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

/** Successful application and both grants/ledgers commit in one explicit session. No quota or outbox. */
public class ReferralRewardService {
    public static final String CODES = "referral_codes", APPLICATIONS = "referral_applications";
    public static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public record Code(String code) {}
    public record Applied(String applicationId, boolean applied, int applicantAwardedQuantity, int referrerAwardedQuantity) {}
    private final PaidExamTransactions tx;
    private final PaymentStore store;
    private final PaymentProperties payment;
    private final PaymentRecoveryFence fence;
    private final String environment, issuer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ReferralRewardService(PaidExamTransactions tx, PaymentStore store, PaymentProperties payment,
            String environment, String issuer, Clock clock) {
        this.tx = tx; this.store = store; this.payment = payment; this.environment = environment;
        this.issuer = issuer; this.clock = clock; this.fence = new PaymentRecoveryFence(store, payment);
        require(payment.getRecovery().isFenceRequired() && issuer != null && !issuer.isBlank()
                && (Set.of("test", "staging").contains(environment) && "SANDBOX".equals(payment.getEnvironment())
                || "production".equals(environment) && "PRODUCTION".equals(payment.getEnvironment())));
    }
    public Code issue(VerifiedMemberEvidence evidence) {
        ready(evidence);
        var ticket = fence.capture();
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = randomCode(), id = UUID.randomUUID().toString();
            Instant now = clock.instant();
            // Each collision attempt has a new transaction; a committed existing code always wins.
            Code result = tx.execute(c -> {
                fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
                deadline(evidence);
                Document account = account(c, evidence.userId(), false);
                touch(c, evidence.userId());
                Document old = one(c, CODES, key("ownerUserId", evidence.userId()));
                if (old != null) {
                    require(account.getString("_id").equals(old.getString("accountId")));
                    deadline(evidence); return new Code(old.getString("code"));
                }
                if (one(c, CODES, key("code", candidate)) != null) return null;
                c.operations().insert(key("code", candidate).append("_id", id).append("schemaVersion", 1L)
                        .append("ownerUserId", evidence.userId()).append("accountId", account.getString("_id"))
                        .append("createdAt", ceilingMillis(now)), CODES);
                deadline(evidence); return new Code(candidate);
            });
            if (result != null) return result;
        }
        throw PaymentException.unavailable();
    }
    public Applied apply(VerifiedMemberEvidence evidence, String operationId, String rawCode) {
        if (operationId == null || !operationId.matches(UUID_V4)) throw new PaymentException(400, "INVALID_REQUEST");
        String code = normalize(rawCode);
        ready(evidence);
        var ticket = fence.capture();
        String applicationId = UUID.randomUUID().toString();
        String applicantGrant = UUID.randomUUID().toString(), referrerGrant = UUID.randomUUID().toString();
        String applicantLedger = UUID.randomUUID().toString(), referrerLedger = UUID.randomUUID().toString();
        Instant now = clock.instant();
        return tx.execute(c -> {
            fence.acquire(c.session(), ticket, PaymentRecoveryFence.Writer.ACCOUNT);
            deadline(evidence);
            String user = evidence.userId();
            Document applicant = account(c, user, false);
            Document old = one(c, APPLICATIONS, key("applicantUserId", user));
            if (old != null) {
                if (!code.equals(old.getString("normalizedCode"))) throw new PaymentException(409,
                        operationId.equals(old.getString("operationId")) ? "IDEMPOTENCY_KEY_CONFLICT" : "REFERRAL_ALREADY_APPLIED");
                touch(c, user); deadline(evidence);
                return new Applied(old.getString("_id"), true, 1, 1);
            }
            Document referral = one(c, CODES, key("code", code));
            if (referral == null || referral.getString("ownerUserId") == null)
                throw new PaymentException(422, "REFERRAL_CODE_UNAVAILABLE");
            String referrer = referral.getString("ownerUserId");
            if (user.equals(referrer)) throw new PaymentException(409, "SELF_REFERRAL_NOT_ALLOWED");
            Document owner = account(c, referrer, true);
            require(owner.getString("_id").equals(referral.getString("accountId")));
            // Fixed order across reciprocal and many-to-one requests; existing session guards stay intact.
            List<String> users = new ArrayList<>(List.of(user, referrer));
            Collections.sort(users);
            for (String member : users) {
                touch(c, member);
                boolean isApplicant = member.equals(user);
                Document account = isApplicant ? applicant : owner;
                long order = nextOrder(c, member, account);
                var grant = EntitlementGrant.referral(isApplicant ? applicantGrant : referrerGrant, environment, member,
                        applicationId + (isApplicant ? ":APPLICANT" : ":REFERRER"), order, now);
                c.grants().insert(grant);
                c.ledger().insert(EntitlementLedgerEntry.referral(isApplicant ? applicantLedger : referrerLedger, grant, now));
            }
            c.operations().insert(key("applicantUserId", user).append("_id", applicationId).append("schemaVersion", 1L)
                    .append("normalizedCode", code).append("operationId", operationId).append("appliedAt", ceilingMillis(now))
                    .append("detailEligibleAt", ceilingMillis(now.plus(Duration.ofDays(120))))
                    .append("detail", new Document("referrerUserId", referrer).append("applicantGrantId", applicantGrant)
                            .append("referrerGrantId", referrerGrant).append("applicantLedgerId", applicantLedger)
                            .append("referrerLedgerId", referrerLedger)), APPLICATIONS);
            deadline(evidence);
            return new Applied(applicationId, true, 1, 1);
        });
    }
    public static String normalize(String raw) {
        if (raw == null || raw.length() > 64) throw new PaymentException(400, "INVALID_REQUEST");
        int start = 0, end = raw.length();
        while (start < end && raw.charAt(start) == ' ') start++;
        while (end > start && raw.charAt(end - 1) == ' ') end--;
        StringBuilder code = new StringBuilder();
        for (int i = start; i < end; i++) {
            char ch = raw.charAt(i);
            if (ch >= 'a' && ch <= 'z') ch -= 32;
            if (ALPHABET.indexOf(ch) < 0) throw new PaymentException(400, "INVALID_REQUEST");
            code.append(ch);
        }
        if (code.length() != 10) throw new PaymentException(400, "INVALID_REQUEST");
        return code.toString();
    }
    private String randomCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }
    private Document account(PaidExamTransactions.Context c, String user, boolean referrer) {
        Document query = new Document("environment", payment.getEnvironment()).append("userId", user);
        Document life = one(c, "payment_account_lifecycles", query), account = one(c, "payment_accounts", query);
        if (life != null && "WITHDRAWN".equals(life.getString("state")) || account != null && "WITHDRAWN".equals(account.getString("state")))
            throw new PaymentException(referrer ? 422 : 403, referrer ? "REFERRAL_CODE_UNAVAILABLE" : "PAYMENT_ACCOUNT_WITHDRAWN");
        if (!referrer && life == null && account == null) throw new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED");
        require(life != null && account != null && "ACTIVE".equals(life.getString("state")) && "ACTIVE".equals(account.getString("state"))
                && positive(life, "version") && positive(account, "version"));
        Document proof = account.get("memberEvidence", Document.class);
        if (!referrer && proof == null) throw new PaymentException(409, "PURCHASE_ACCOUNT_REQUIRED");
        require(VerifiedMemberEvidence.matches(proof, user, payment.getEnvironment(), issuer));
        return account;
    }
    private void touch(PaidExamTransactions.Context c, String user) {
        Document q = new Document("environment", payment.getEnvironment()).append("userId", user);
        Document life = one(c, "payment_account_lifecycles", q);
        require(life != null && positive(life, "version"));
        require(c.operations().updateFirst(new BasicQuery(q.append("state", "ACTIVE").append("version", life.getLong("version"))),
                new Update().inc("version", 1L), "payment_account_lifecycles").getModifiedCount() == 1);
    }
    private long nextOrder(PaidExamTransactions.Context c, String user, Document account) {
        Document guard = one(c, PaidExamStore.GUARDS, key("userId", user));
        if (guard == null) {
            require(!c.operations().exists(new BasicQuery(key("ownerUserId", user)), "entitlement_grants"));
            guard = c.paid().createGuard(environment, user, new Document("kind", "MEMBER_ACCOUNT")
                    .append("reference", account.getString("_id")).append("version", account.getLong("version")).append("epoch", null));
        }
        Document owner = guard.get("owner", Document.class);
        require(owner != null && "MEMBER_ACCOUNT".equals(owner.getString("kind")) && owner.get("epoch") == null
                && account.getString("_id").equals(owner.getString("reference")) && positive(owner, "version")
                && owner.getLong("version") <= account.getLong("version") && positive(guard, "version") && positive(guard, "nextGrantOrder"));
        return c.paid().nextGrantOrder(environment, user, guard.getLong("version"));
    }
    private void ready(VerifiedMemberEvidence evidence) {
        require(evidence != null && payment.getEnvironment().equals(evidence.environment()));
        require(store.database().listCollectionNames().into(new ArrayList<>()).containsAll(List.of(
                CODES, APPLICATIONS, PaidExamStore.GUARDS, "payment_accounts", "payment_account_lifecycles", "entitlement_grants", "entitlement_ledger")));
    }
    private void deadline(VerifiedMemberEvidence evidence) {
        if (!clock.instant().isBefore(evidence.authorizationDeadline())) throw new PaymentException(401, "UNAUTHENTICATED");
    }
    private Document key(String field, String value) { return new Document("environment", environment).append(field, value); }
    static Date ceilingMillis(Instant time) { return new Date(time.toEpochMilli() + (time.getNano() % 1_000_000 == 0 ? 0 : 1)); }
    private static boolean positive(Document d, String f) { return d.get(f) instanceof Long n && n > 0 && n < Long.MAX_VALUE; }
    private static Document one(PaidExamTransactions.Context c, String collection, Document key) {
        var rows = c.operations().find(new BasicQuery(key).limit(2), Document.class, collection);
        require(rows.size() <= 1); return rows.isEmpty() ? null : rows.getFirst();
    }
}
