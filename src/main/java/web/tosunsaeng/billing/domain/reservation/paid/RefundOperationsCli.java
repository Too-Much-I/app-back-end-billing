package web.tosunsaeng.billing.domain.reservation.paid;

import com.mongodb.client.MongoClients;
import java.io.PrintStream;
import java.time.Clock;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

/** Standalone operator. Dry-run default; no Spring, HTTP, private keys or worker startup. */
public final class RefundOperationsCli {
    private RefundOperationsCli() {}
    public static void main(String[] args) {
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext logs)
            logs.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        int status = run(args, System.getenv(), System.out, System.err);
        if (status != 0) System.exit(status);
    }
    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        try {
            Map<String, String> o = parse(args);
            String database = required(env, "BILLING_REFUND_OPERATOR_DATABASE");
            String environment = required(env, "BILLING_REFUND_OPERATOR_ENVIRONMENT");
            if (!database.matches("[A-Za-z0-9_-]{1,63}") || !Set.of("test", "staging", "production").contains(environment)) throw new IllegalArgumentException();
            try (var mongo = MongoClients.create(required(env, "BILLING_REFUND_OPERATOR_MONGO_URI"))) {
                var store = new PaymentStore(mongo, new SimpleMongoClientDatabaseFactory(mongo, database));
                var auth = store.database().runCommand(new Document("connectionStatus", 1)).get("authInfo", Document.class);
                if (auth == null || auth.getList("authenticatedUsers", Document.class).size() != 1) throw new IllegalArgumentException();
                String action = o.get("action");
                if (Set.of("purchase-show", "outbox-show", "case-list", "case-show").contains(action)) {
                    var reader = new RefundOperationsReader(store, environment, Clock.systemUTC());
                    Document result = switch (action) {
                        case "purchase-show" -> reader.purchase(o.get("target"));
                        case "outbox-show" -> reader.outbox(o.get("target"));
                        case "case-show" -> reader.reviewCase(o.get("target"));
                        default -> reader.cases(o.get("after"));
                    };
                    out.println(result.toJson());
                } else if (action.equals("schema")) {
                    if (flag(o, "apply")) RefundOperatorSchema.installUnderMaintenance(store.database(), flag(o, "writers-isolated"));
                    out.println(flag(o, "apply") ? "SCHEMA_VERIFIED" : "DRY_RUN_NO_WRITES");
                } else {
                    var template = new org.springframework.data.mongodb.core.MongoTemplate(new SimpleMongoClientDatabaseFactory(mongo, database));
                    var operator = new RefundOperator(new PaidExamTransactions(store, template), store, environment,
                            required(env, "BILLING_REFUND_OPERATOR_SCOPE"), Clock.systemUTC());
                    if (action.equals("result")) out.println(operator.result(o.get("operation-id")));
                    else {
                        Document user = auth.getList("authenticatedUsers", Document.class).getFirst();
                        if (user.getString("db") == null || user.getString("user") == null) throw new IllegalArgumentException();
                        String actor = RefundFanout.digest(new Document("db", user.getString("db")).append("user", user.getString("user")).toJson());
                        out.println(operator.execute(command(o), actor, flag(o, "apply"), flag(o, "writers-isolated")));
                    }
                }
            }
            return 0;
        } catch (Exception refused) {
            err.println("Refund operation refused or unavailable. If apply was attempted, query result with the same operation ID before retrying.");
            return 1;
        }
    }
    static Map<String, String> parse(String[] args) {
        if (args == null || args.length > 11) throw new IllegalArgumentException();
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || arg.length() > 128) throw new IllegalArgumentException();
            String[] pair = arg.substring(2).split("=", 2);
            if (pair.length != 2 || values.putIfAbsent(pair[0], pair[1]) != null) throw new IllegalArgumentException();
        }
        String action = required(values, "action");
        Set<String> allowed = new HashSet<>(Set.of("action"));
        switch (action) {
            case "purchase-show", "outbox-show", "case-show" -> { allowed.add("target"); RefundOperationsReader.uuid(required(values, "target")); }
            case "case-list" -> { allowed.add("after"); if (values.containsKey("after")) RefundOperationsReader.uuid(values.get("after")); }
            case "result" -> { allowed.add("operation-id"); RefundOperationsReader.uuid(required(values, "operation-id")); }
            case "schema" -> allowed.addAll(Set.of("apply", "writers-isolated"));
            case "outbox-resume", "fanout-resume", "group-recheck" -> {
                allowed.addAll(Set.of("target", "operation-id", "version", "generation", "epoch", "reason", "apply", "writers-isolated"));
                if (action.equals("group-recheck")) allowed.add("group");
                command(values);
            }
            default -> throw new IllegalArgumentException();
        }
        if (!allowed.containsAll(values.keySet()) || flag(values, "apply") && !flag(values, "writers-isolated")) throw new IllegalArgumentException();
        return Map.copyOf(values);
    }
    static RefundOperator.Command command(Map<String, String> o) {
        return new RefundOperator.Command(required(o, "operation-id"), RefundOperator.Action.valueOf(required(o, "action").replace('-', '_').toUpperCase(Locale.ROOT)),
                required(o, "target"), o.get("group"), number(required(o, "version")), number(required(o, "generation")),
                required(o, "epoch"), RefundOperator.Reason.valueOf(required(o, "reason")));
    }
    private static long number(String s) {
        if (!s.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException(); return Long.parseLong(s);
    }
    private static boolean flag(Map<String, String> o, String key) {
        String v = o.getOrDefault(key, "false");
        if (!Set.of("true", "false").contains(v)) throw new IllegalArgumentException(); return Boolean.parseBoolean(v);
    }
    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }
}
