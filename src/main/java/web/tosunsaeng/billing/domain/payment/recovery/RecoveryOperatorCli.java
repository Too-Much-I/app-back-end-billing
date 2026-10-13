package web.tosunsaeng.billing.domain.payment.recovery;

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.ValidationOptions;
import java.time.Clock;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Explicit, separately credentialed process. Does not start Spring, a server, worker, or HTTP client. */
public final class RecoveryOperatorCli {
    private RecoveryOperatorCli() { }
    public static void main(String[] args) {
        try {
            Map<String, String> options = parse(args);
            String action = options.getOrDefault("action", "inspect");
            boolean apply = flag(options, "apply");
            if (apply && !flag(options, "approved")) throw new IllegalArgumentException();
            if (apply && Set.of("schema", "initial", "rotate").contains(action) && !flag(options, "writers-isolated"))
                throw new IllegalArgumentException();
            String uri = requiredEnv("BILLING_OPERATOR_MONGO_URI");
            String database = requiredEnv("BILLING_OPERATOR_DATABASE");
            String environment = requiredEnv("BILLING_OPERATOR_ENVIRONMENT");
            if (!database.matches("[a-zA-Z0-9_-]{1,63}") || !Set.of("SANDBOX", "PRODUCTION").contains(environment))
                throw new IllegalArgumentException();
            try (var mongo = MongoClients.create(uri)) {
                var store = new PaymentStore(mongo, new SimpleMongoClientDatabaseFactory(mongo, database));
                var p = new PaymentProperties(); p.setEnvironment(environment); p.getRecovery().setFenceRequired(true);
                var operator = new RecoveryOperator(store, p, Clock.systemUTC());
                if ("inspect".equals(action)) {
                    if (apply) throw new IllegalArgumentException();
                    System.out.println(operator.inspect().toJson());
                } else if ("schema".equals(action)) {
                    if (apply) migrateSchema(store);
                    System.out.println(apply ? "SCHEMA_VERIFIED" : "DRY_RUN_SCHEMA_NO_WRITES");
                } else if ("snapshot".equals(action)) {
                    var c = store.database().getCollection(PaymentRecoveryFence.COLLECTION)
                            .find(new Document("environment", environment).append("consumer", "billing")).first();
                    if (c == null || !Objects.equals(c.getString("epoch"), options.get("expected-epoch"))
                            || !Objects.equals(c.getLong("version"), version(options))) throw new IllegalArgumentException();
                    if (apply) {
                        p.getRecovery().setSnapshotEnabled(true);
                        new WithdrawalSnapshotProcessor(store, p, Clock.systemUTC(), null).startAtEpoch(
                                required(options, "stream-id"), required(options, "operation-id"), true,
                                new PaymentRecoveryFence.Ticket(required(options, "expected-epoch")));
                    }
                    System.out.println(apply ? "SNAPSHOT_PREPARED_NO_HTTP" : "DRY_RUN");
                } else {
                    var command = new RecoveryOperator.Command(RecoveryOperator.Action.valueOf(action.toUpperCase(Locale.ROOT)),
                            required(options, "operation-id"), options.get("expected-epoch"), version(options),
                            options.get("new-epoch"), required(options, "stream-id"), flag(options, "preserve-baseline"));
                    System.out.println(operator.execute(command, apply));
                }
            }
        } catch (Exception failure) {
            System.err.println("Recovery operator refused or unavailable; no automatic retry. Verify approved inputs and database state.");
            System.exit(1); // Never print args, URI, raw database error or stack.
        }
    }
    /** Only the known additive operator-receipt extension; never replaces an unknown validator or drops an index. */
    public static void migrateSchema(PaymentStore store) {
        var db = store.database();
        var c = db.getCollection(PaymentRecoveryFence.COLLECTION).find().into(new ArrayList<>());
        if (c.stream().anyMatch(row -> !"ALL_BLOCKED".equals(row.getString("barrier")))) throw new IllegalStateException("Writers must be blocked");
        PaymentRecoverySchema.initializeEmpty(db);
        var target = PaymentSnapshotSchema.manifest().get("validators", Document.class)
                .get(WithdrawalSnapshotProcessor.OPERATIONS, Document.class);
        var meta = db.listCollections().filter(new Document("name", WithdrawalSnapshotProcessor.OPERATIONS)).first();
        if (meta != null) {
            var current = meta.get("options", Document.class);
            var previous = Document.parse(target.toJson());
            previous.get("$jsonSchema", Document.class).get("properties", Document.class).remove("operator");
            if (!target.equals(current.get("validator"))) {
                if (!previous.equals(current.get("validator")) || !"strict".equals(current.getString("validationLevel"))
                        || !"error".equals(current.getString("validationAction"))) throw new IllegalStateException("Unrecognized schema");
                db.runCommand(new Document("collMod", WithdrawalSnapshotProcessor.OPERATIONS)
                        .append("validator", target).append("validationLevel", "strict").append("validationAction", "error"));
            }
        }
        PaymentSnapshotSchema.initializeEmpty(db);
    }
    static Map<String, String> parse(String[] args) {
        Set<String> allowed = Set.of("action", "apply", "approved", "writers-isolated", "operation-id",
                "expected-epoch", "expected-version", "new-epoch", "stream-id", "preserve-baseline");
        Map<String, String> result = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) throw new IllegalArgumentException();
            String[] pair = arg.substring(2).split("=", 2);
            if (!allowed.contains(pair[0]) || result.putIfAbsent(pair[0], pair.length == 1 ? "true" : pair[1]) != null)
                throw new IllegalArgumentException();
        }
        if (!Set.of("inspect", "schema", "snapshot", "initial", "rotate", "block", "recover", "open")
                .contains(result.getOrDefault("action", "inspect"))) throw new IllegalArgumentException();
        return result;
    }
    private static boolean flag(Map<String, String> options, String name) {
        String value = options.getOrDefault(name, "false");
        if (!Set.of("true", "false").contains(value)) throw new IllegalArgumentException();
        return Boolean.parseBoolean(value);
    }
    private static long version(Map<String, String> options) {
        String value = options.getOrDefault("expected-version", "0");
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
        return Long.parseLong(value);
    }
    private static String required(Map<String, String> options, String key) {
        String value = options.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException(); return value;
    }
    private static String requiredEnv(String key) {
        String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalArgumentException(); return value;
    }
}
