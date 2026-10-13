package web.tosunsaeng.billing.domain.payment.recovery;

import java.time.*;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

/** Offline operator only: deliberately not a Spring bean or an HTTP endpoint. */
public final class RecoveryOperator {
    public enum Action { INITIAL, ROTATE, BLOCK, RECOVER, OPEN }
    public record Command(Action action, String operationId, String expectedEpoch, long expectedVersion,
                          String newEpoch, String streamId, boolean preserveBaseline) {
        public Command {
            Objects.requireNonNull(action); uuid(operationId); uuid(streamId);
            if (action == Action.INITIAL) require(expectedEpoch == null && expectedVersion == 0 && !preserveBaseline);
            else { uuid(expectedEpoch); require(expectedVersion > 0 && expectedVersion < Long.MAX_VALUE); }
            if (action == Action.INITIAL || action == Action.ROTATE) {
                uuid(newEpoch); require(!newEpoch.equals(expectedEpoch));
            } else require(newEpoch == null && !preserveBaseline);
        }
        @Override public String toString() { return "RecoveryOperatorCommand[REDACTED]"; }
    }
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    public RecoveryOperator(PaymentStore store, PaymentProperties properties, Clock clock) {
        this.store = store; this.properties = properties; this.clock = clock;
        require(Set.of("SANDBOX", "PRODUCTION").contains(properties.getEnvironment()));
    }
    /** No epoch, cursor, user or operation identifiers in console status. */
    public Document inspect() {
        Document c = store.database().getCollection(PaymentRecoveryFence.COLLECTION).find(scope()).first();
        return c == null ? new Document("present", false) : new Document("present", true)
                .append("schemaVersion", c.get("schemaVersion")).append("version", c.get("version"))
                .append("barrier", c.get("barrier")).append("hasBaseline", c.containsKey("baseline"));
    }
    public String execute(Command command, boolean apply) {
        PaymentRecoverySchema.verify(store.database()); PaymentSnapshotSchema.verify(store.database());
        return store.transaction(session -> {
            var controls = store.database().getCollection(PaymentRecoveryFence.COLLECTION);
            var operations = store.database().getCollection(WithdrawalSnapshotProcessor.OPERATIONS);
            Document receipt = operations.find(session, scope().append("operationId", command.operationId())).first();
            Document binding = binding(command);
            if (receipt != null) {
                Document stored = receipt.get("operator", Document.class);
                require(stored != null);
                stored = new Document(stored); stored.remove("previousStreamId"); stored.remove("previousCheckpoint");
                require(binding.equals(stored) && command.streamId().equals(receipt.getString("streamId")));
                return "ALREADY_APPLIED";
            }
            Document current = controls.find(session, scope()).first();
            Document next;
            if (command.action() == Action.INITIAL) {
                require(current == null);
                next = new Document(scope()).append("_id", UUID.randomUUID().toString()).append("schemaVersion", 4L)
                        .append("epoch", command.newEpoch()).append("version", 1L)
                        .append("barrier", "ALL_BLOCKED").append("historicalUncertainty", true);
            } else {
                require(current != null && command.expectedEpoch().equals(current.getString("epoch"))
                        && Long.valueOf(command.expectedVersion()).equals(current.get("version")));
                require(Set.of(1L, 2L, 3L, 4L).contains(current.get("schemaVersion")));
                if (command.action() != Action.ROTATE && current.get("baseline") instanceof Document baseline)
                    require(command.streamId().equals(baseline.getString("streamId")));
                next = new Document(current); next.put("version", command.expectedVersion() + 1);
                switch (command.action()) {
                    case BLOCK -> next.put("barrier", "ALL_BLOCKED");
                    case ROTATE -> {
                        require("ALL_BLOCKED".equals(current.getString("barrier")));
                        next.put("epoch", command.newEpoch()); next.put("schemaVersion", 4L);
                        next.remove("snapshotJob"); next.remove("feedJob");
                        if (command.preserveBaseline()) {
                            var baseline = current.get("baseline", Document.class);
                            require(baseline != null && command.streamId().equals(baseline.getString("streamId"))
                                    && current.get("checkpoint") instanceof Long
                                    && current.getLong("checkpoint") >= baseline.getLong("highWater"));
                        } else {
                            next.remove("baseline"); next.remove("checkpoint"); next.put("historicalUncertainty", true);
                        }
                    }
                    case RECOVER -> {
                        require("ALL_BLOCKED".equals(current.getString("barrier")) && Long.valueOf(4).equals(current.get("schemaVersion")));
                        next.put("barrier", "PURCHASE_BLOCKED");
                    }
                    case OPEN -> {
                        require("PURCHASE_BLOCKED".equals(current.getString("barrier")) && Long.valueOf(4).equals(current.get("schemaVersion")));
                        var baseline = current.get("baseline", Document.class);
                        var feed = current.get("feedJob", Document.class);
                        require(baseline != null && command.streamId().equals(baseline.getString("streamId"))
                                && feed != null && command.expectedEpoch().equals(feed.getString("epoch"))
                                && command.streamId().equals(feed.getString("streamId"))
                                && "CAUGHT_UP".equals(feed.getString("state")) && feed.get("lastSuccessAt") instanceof Date
                                && current.get("checkpoint") instanceof Long
                                && Objects.equals(current.get("checkpoint"), feed.get("target")));
                        next.put("barrier", "OPEN");
                    }
                    default -> throw new IllegalStateException("Invalid recovery operation");
                }
            }
            if (!apply) return "DRY_RUN";
            if (current != null && current.get("baseline") instanceof Document previous) {
                binding.append("previousStreamId", previous.getString("streamId")).append("previousCheckpoint", current.get("checkpoint"));
            }
            if (current == null) controls.insertOne(session, next);
            else require(controls.replaceOne(session, scope().append("epoch", command.expectedEpoch())
                    .append("version", command.expectedVersion()), next).getModifiedCount() == 1);
            operations.insertOne(session, new Document(scope()).append("_id", UUID.randomUUID().toString())
                    .append("operationId", command.operationId()).append("streamId", command.streamId())
                    .append("epoch", next.getString("epoch")).append("state", "COMPLETE").append("operator", binding)
                    .append("createdAt", Date.from(clock.instant()))
                    .append("retentionExpiresAt", Date.from(clock.instant().plus(Duration.ofDays(120)))));
            return "APPLIED";
        });
    }
    private Document binding(Command c) {
        return new Document("action", c.action().name()).append("expectedEpoch", c.expectedEpoch())
                .append("expectedVersion", c.expectedVersion()).append("newEpoch", c.newEpoch())
                .append("preserveBaseline", c.preserveBaseline());
    }
    private Document scope() { return new Document("environment", properties.getEnvironment()).append("consumer", "billing"); }
    private static void uuid(String value) {
        require(value != null);
        try { var id = UUID.fromString(value); require(id.toString().equals(value) && id.version() == 4 && id.variant() == 2); }
        catch (IllegalArgumentException bad) { throw new IllegalStateException("Invalid recovery operation"); }
    }
}
