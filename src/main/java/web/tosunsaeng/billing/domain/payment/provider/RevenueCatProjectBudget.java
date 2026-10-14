package web.tosunsaeng.billing.domain.payment.provider;

import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Cross-process AND cross-environment budget. Never instantiate against separate per-environment databases. */
public final class RevenueCatProjectBudget {
    public record Permit(String token, Instant until) {
        @Override public String toString() { return "RevenueCatPermit[REDACTED]"; }
    }
    private final PaymentStore sharedStore;
    private final String project;
    private final Clock clock;
    public RevenueCatProjectBudget(PaymentStore sharedStore, String project, Clock clock) {
        RevenueCatPurchaseDecoder.text(project);
        this.sharedStore = Objects.requireNonNull(sharedStore); this.project = project; this.clock = Objects.requireNonNull(clock);
        RevenueCatBudgetSchema.verify(sharedStore.database());
    }
    String project() { return project; }
    public boolean isAuthBlocked() {
        Document row = rows().find(key()).first(); return row != null && row.getBoolean("blockedAuth");
    }
    /** A committed permit consumes rate budget even on failure, unknown commit or process crash. */
    public Permit acquire() {
        String token = UUID.randomUUID().toString();
        return sharedStore.transaction(s -> {
            Instant now = clock.instant(); Document row = rows().find(s, key()).first();
            if (row == null) {
                row = key().append("version", 1L).append("updatedAt", Date.from(now)).append("blockedAuth", false)
                        .append("starts", List.of()).append("slots", List.of());
                rows().insertOne(s, row);
            }
            monotonic(row, now);
            if (row.getBoolean("blockedAuth")) throw new RevenueCatFailure(RevenueCatFailure.Kind.AUTH, null);
            var starts = new ArrayList<>(row.getList("starts", Date.class).stream().filter(d -> d.toInstant().isAfter(now.minusSeconds(60))).toList());
            var slots = new ArrayList<>(row.getList("slots", Document.class).stream().filter(d -> d.getDate("until").toInstant().isAfter(now)).toList());
            Instant retry = null;
            if (starts.size() >= 240) retry = starts.stream().map(Date::toInstant).min(Instant::compareTo).orElseThrow().plusSeconds(60);
            if (slots.size() >= 4) {
                Instant free = slots.stream().map(d -> d.getDate("until").toInstant()).min(Instant::compareTo).orElseThrow();
                if (retry == null || free.isAfter(retry)) retry = free;
            }
            if (retry != null) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, retry);
            Instant until = Instant.ofEpochMilli(now.plusSeconds(30).toEpochMilli()); starts.add(Date.from(now));
            slots.add(new Document("token", token).append("until", Date.from(until)));
            replace(s, row, new Document("starts", starts).append("slots", slots).append("updatedAt", Date.from(now)));
            return new Permit(token, until);
        });
    }
    /** Before network dispatch and before accepting its result. Does not extend an expired permit. */
    public void validate(Permit permit) {
        sharedStore.transaction(s -> {
            Document row = owned(s, permit); Instant now = clock.instant();
            if (row.getBoolean("blockedAuth")) throw new RevenueCatFailure(RevenueCatFailure.Kind.AUTH, null);
            replace(s, row, new Document("updatedAt", Date.from(now))); return null;
        });
    }
    public void blockAuth(Permit permit) {
        sharedStore.transaction(s -> {
            Document row = owned(s, permit);
            replace(s, row, new Document("blockedAuth", true).append("updatedAt", Date.from(clock.instant()))); return null;
        });
    }
    /** Only returns concurrency capacity. Never refunds an attempted HTTP call's rate budget. */
    public void release(Permit permit) {
        sharedStore.transaction(s -> {
            Document row = rows().find(s, key()).first(); if (row == null) return null;
            var slots = row.getList("slots", Document.class);
            var remaining = slots.stream().filter(d -> !permit.token().equals(d.getString("token"))).toList();
            if (remaining.size() != slots.size()) replace(s, row, new Document("slots", remaining));
            return null;
        });
    }
    /** Explicit operator maintenance after fixing credentials; no automatic retry/re-enable path. */
    public void resumeUnderMaintenance(long expectedVersion, boolean allProjectCallersIsolated) {
        require(allProjectCallersIsolated && expectedVersion > 0);
        sharedStore.transaction(s -> {
            resumeInTransaction(s, expectedVersion, true); return null;
        });
    }
    /** Operator receipt must be written in this same shared-budget transaction. */
    public void resumeInTransaction(ClientSession s, long expectedVersion, boolean apply) {
            require(s != null && s.hasActiveTransaction() && expectedVersion > 0 && expectedVersion < Long.MAX_VALUE);
            Document row = rows().find(s, key()).first(); require(row != null && row.getLong("version") == expectedVersion);
            Instant now = clock.instant(); monotonic(row, now); require(row.getBoolean("blockedAuth"));
            require(row.getList("slots", Document.class).stream().noneMatch(d -> d.getDate("until").toInstant().isAfter(now)));
            if (apply) replace(s, row, new Document("blockedAuth", false).append("slots", List.of()).append("updatedAt", Date.from(now)));
    }
    private Document owned(ClientSession s, Permit permit) {
        Document row = rows().find(s, key()).first(); Instant now = clock.instant();
        if (row == null || !permit.until().isAfter(now)) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null);
        monotonic(row, now);
        if (row.getList("slots", Document.class).stream().noneMatch(d -> permit.token().equals(d.getString("token"))
                && permit.until().equals(d.getDate("until").toInstant()))) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null);
        return row;
    }
    private static void monotonic(Document row, Instant now) {
        Instant previous = row.getDate("updatedAt").toInstant();
        if (now.isBefore(previous)) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, previous);
    }
    private void replace(ClientSession s, Document row, Document fields) {
        long version = row.getLong("version"); require(version > 0 && version < Long.MAX_VALUE);
        require(rows().updateOne(s, key().append("version", version), new Document("$set", fields)
                .append("$inc", new Document("version", 1L))).getModifiedCount() == 1);
    }
    private Document key() { return new Document("_id", project); }
    private MongoCollection<Document> rows() { return sharedStore.database().getCollection(RevenueCatBudgetSchema.COLLECTION); }
    @Override public String toString() { return "RevenueCatProjectBudget[REDACTED]"; }
}
