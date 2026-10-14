package web.tosunsaeng.billing.domain.payment.worker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.provider.*;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.*;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Offline, separately credentialed operator. Never a Spring bean or an HTTP controller. */
public final class PaymentReviewOperator {
    public enum Action { ACCOUNT, AUTH }
    public enum Reason { PROVIDER_RECOVERED, CREDENTIALS_FIXED, CONTRACT_FIXED, MANUAL_REVIEW_COMPLETED }
    public record Command(String operationId, String target, long version, long generation, String epoch,
                          PaymentSyncService.LookupFailure failure, Reason reason, Map<String, Long> selected) {
        public Command {
            uuid(operationId); token(target); require(version > 0 && version < Long.MAX_VALUE && generation >= 0
                    && generation < Long.MAX_VALUE && reason != null && selected != null && selected.size() <= 100);
            selected = Collections.unmodifiableMap(new TreeMap<>(selected));
            selected.forEach((id, v) -> { uuid(id); require(v != null && v > 0 && v < Long.MAX_VALUE); });
        }
        @Override public String toString() { return "PaymentReviewCommand[REDACTED]"; }
    }
    public record Result(String status, int count, Instant completedAt) {}
    private final PaymentStore store;
    private final Clock clock;
    private final Action action;
    private final String scope;
    private final PaymentProperties properties;
    public PaymentReviewOperator(PaymentStore store, Clock clock, Action action, String scope) {
        require(store != null && clock != null && action != null);
        require(action == Action.AUTH ? "SHARED".equals(scope) : Set.of("SANDBOX", "PRODUCTION").contains(scope));
        this.store = store; this.clock = clock; this.action = action; this.scope = scope;
        properties = new PaymentProperties(); properties.setEnvironment(scope); properties.getRecovery().setFenceRequired(true);
        PaymentOperatorSchema.verify(store.database());
        if (action == Action.ACCOUNT) { PaymentSyncSchema.verify(store.database()); PaymentAccountLookupSchema.verify(store.database()); }
        else RevenueCatBudgetSchema.verify(store.database());
    }
    /** UUIDs are internal operation handles, never user IDs or Store references. Output is for restricted operator terminal only. */
    public Document inspect(String target) {
        token(target);
        return store.transaction(s -> {
            if (action == Action.AUTH) {
                var row = store.database().getCollection(RevenueCatBudgetSchema.COLLECTION).find(s, new Document("_id", target)).first();
                require(row != null);
                long live = row.getList("slots", Document.class).stream().filter(d -> d.getDate("until").toInstant().isAfter(clock.instant())).count();
                return new Document("version", row.getLong("version")).append("blockedAuth", row.getBoolean("blockedAuth")).append("activeSlots", live);
            }
            uuid(target);
            require(store.database().getCollection("payment_accounts").find(s, new Document("_id", target).append("environment", scope)).first() != null);
            var row = store.database().getCollection(PaymentAccountLookupSchema.COLLECTION)
                    .find(s, new Document("accountId", target).append("kind", "ACCOUNT_LOOKUP")).first();
            require(row != null && row.get("lookup") instanceof Document);
            var work = row.get("lookup", Document.class);
            var control = store.database().getCollection(PaymentRecoveryFence.COLLECTION)
                    .find(s, new Document("environment", scope).append("consumer", "billing")).first(); require(control != null);
            var rows = store.database().getCollection(PaymentSyncSchema.COLLECTION).find(s, new Document("environment", scope)
                    .append("accountId", target).append("state", new Document("$in", List.of("REVIEW_REQUIRED", "BLOCKED_AUTH"))))
                    .sort(new Document("_id", 1)).limit(101).into(new ArrayList<>());
            var commands = rows.stream().limit(100).map(d -> new Document("commandId", d.getString("_id"))
                    .append("version", d.getLong("version")).append("failure", failure(d.getString("errorCode")))
                    .append("sourceBound", d.getString("sourceObservationKey") != null).append("counted", d.getBoolean("counted"))).toList();
            long pending = store.database().getCollection(PaymentSyncSchema.COLLECTION).countDocuments(s,
                    new Document("environment", scope).append("accountId", target).append("state", "PENDING"));
            return new Document("version", row.getLong("version")).append("generation", work.getLong("generation"))
                    .append("epoch", control.getString("epoch")).append("barrier", control.getString("barrier"))
                    .append("failure", failure(work.getString("failure"))).append("pending", pending)
                    .append("activeLease", work.getString("owner") != null && work.getDate("until").toInstant().isAfter(clock.instant()))
                    .append("commands", commands).append("truncated", rows.size() > 100);
        });
    }
    public Result result(String operationId) {
        uuid(operationId);
        return store.transaction(s -> {
            var row = store.database().getCollection(PaymentOperatorSchema.COLLECTION).find(s, new Document("_id", operationId)).first();
            if (row == null) return new Result("NOT_FOUND", 0, null);
            require(action.name().equals(row.getString("action")) && scope.equals(row.getString("scope")));
            return result(row, "APPLIED");
        });
    }
    /** actorDigest must come from the authenticated execution boundary, never command-line self-assertion. */
    public Result execute(Command command, String actorDigest, boolean apply, boolean writersIsolated) {
        require(actorDigest != null && actorDigest.matches("[0-9a-f]{64}"));
        require(!apply || writersIsolated);
        if (action == Action.ACCOUNT) { uuid(command.target()); uuid(command.epoch()); require(command.failure() != null); }
        else require(command.epoch() == null && command.failure() == null && command.generation() == 0
                && command.selected().isEmpty() && command.reason() == Reason.CREDENTIALS_FIXED);
        var binding = new Document("action", action.name()).append("scope", scope).append("target", command.target())
                .append("version", command.version()).append("generation", command.generation()).append("epoch", command.epoch())
                .append("failure", command.failure() == null ? null : command.failure().name()).append("reason", command.reason().name())
                .append("selected", new Document(new TreeMap<>(command.selected()))).append("actorDigest", actorDigest);
        String digest = hash(binding.toJson());
        // Constructors verify only. Do not install schemas or make external calls during execution.
        var resumer = action == Action.ACCOUNT ? new PaymentLookupResumer(store, properties, clock) : null;
        var budget = action == Action.AUTH ? new RevenueCatProjectBudget(store, command.target(), clock) : null;
        return store.transaction(s -> {
            var receipts = store.database().getCollection(PaymentOperatorSchema.COLLECTION);
            var old = receipts.find(s, new Document("_id", command.operationId())).first();
            if (old != null) { require(digest.equals(old.getString("requestDigest"))); return result(old, "ALREADY_APPLIED"); }
            int count;
            if (resumer != null) count = resumer.resumeInTransaction(s, new PaymentRecoveryFence.Ticket(command.epoch()), command.target(),
                    command.version(), command.generation(), command.failure(), command.selected(), apply);
            else { budget.resumeInTransaction(s, command.version(), apply); count = 1; }
            if (!apply) return new Result("DRY_RUN", count, null);
            Instant completed = Instant.ofEpochMilli(clock.instant().toEpochMilli());
            receipts.insertOne(s, new Document("_id", command.operationId()).append("schemaVersion", 1L)
                    .append("action", action.name()).append("scope", scope).append("requestDigest", digest)
                    .append("targetDigest", hash(command.target())).append("actorDigest", actorDigest).append("reason", command.reason().name())
                    .append("count", (long) count).append("completedAt", Date.from(completed)));
            return new Result("APPLIED", count, completed);
        });
    }
    private static Result result(Document row, String status) { return new Result(status, Math.toIntExact(row.getLong("count")), row.getDate("completedAt").toInstant()); }
    private static String failure(String value) {
        return Arrays.stream(PaymentSyncService.LookupFailure.values()).map(Enum::name).filter(s -> s.equals(value)).findFirst().orElse("OTHER");
    }
    static void uuid(String value) {
        require(value != null);
        try { UUID id = UUID.fromString(value); require(value.equals(id.toString()) && id.version() == 4 && id.variant() == 2); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid operator input"); }
    }
    static void token(String value) { require(value != null && value.matches("[A-Za-z0-9_-]{1,128}")); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("Hash unavailable"); }
    }
}
