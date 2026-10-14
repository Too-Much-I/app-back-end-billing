package web.tosunsaeng.billing.domain.payment.worker;

import com.mongodb.client.MongoClients;
import java.io.PrintStream;
import java.time.Clock;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import web.tosunsaeng.billing.domain.payment.sync.PaymentSyncService;

/** Separate process only: no Spring startup, workers, provider HTTP, key loading or automatic schema creation. */
public final class PaymentReviewOperatorCli {
    private PaymentReviewOperatorCli() {}
    public static void main(String[] args) {
        // URI and driver exceptions must never be emitted by third-party loggers in this operator process.
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext logs)
            logs.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        int status = run(args, System.getenv(), System.out, System.err);
        if (status != 0) System.exit(status);
    }
    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        try {
            var options = parse(args);
            String action = options.getOrDefault("action", "inspect");
            var kind = PaymentReviewOperator.Action.valueOf(required(options, "kind").toUpperCase(Locale.ROOT));
            boolean apply = flag(options, "apply"), isolated = flag(options, "writers-isolated");
            if (apply && !isolated) throw new IllegalArgumentException();
            String database = required(env, "BILLING_REVIEW_OPERATOR_DATABASE");
            if (!database.matches("[A-Za-z0-9_-]{1,63}")) throw new IllegalArgumentException();
            String scope = required(env, "BILLING_REVIEW_OPERATOR_SCOPE");
            if (kind == PaymentReviewOperator.Action.AUTH ? !"SHARED".equals(scope) : !Set.of("SANDBOX", "PRODUCTION").contains(scope))
                throw new IllegalArgumentException();
            try (var mongo = MongoClients.create(required(env, "BILLING_REVIEW_OPERATOR_MONGO_URI"))) {
                var store = new PaymentStore(mongo, new SimpleMongoClientDatabaseFactory(mongo, database));
                String actor = authenticatedActor(store.database().runCommand(new Document("connectionStatus", 1)));
                if ("schema".equals(action)) {
                    if (apply) PaymentOperatorSchema.installUnderMaintenance(store.database(), isolated);
                    else if (store.database().listCollections().filter(new Document("name", PaymentOperatorSchema.COLLECTION)).first() != null)
                        PaymentOperatorSchema.verify(store.database());
                    out.println(apply ? "SCHEMA_VERIFIED" : "DRY_RUN_SCHEMA_NO_WRITES"); return 0;
                }
                var operator = new PaymentReviewOperator(store, Clock.systemUTC(), kind, scope);
                if ("inspect".equals(action)) out.println(operator.inspect(required(options, "target")).toJson());
                else if ("result".equals(action)) out.println(operator.result(required(options, "operation-id")));
                else out.println(operator.execute(command(options, kind), actor, apply, isolated));
            }
            return 0;
        } catch (Exception refused) {
            err.println("Operator refused or unavailable. No automatic retry. If apply was attempted, check result with the same operation ID before retrying.");
            return 1; // No args, secret URI, identifiers or raw exception.
        }
    }
    /** Authenticated Mongo principal is the audit identity. Shared logins cannot identify individual humans. */
    static String authenticatedActor(Document connectionStatus) {
        var users = connectionStatus.get("authInfo", Document.class).getList("authenticatedUsers", Document.class);
        if (users == null || users.size() != 1) throw new IllegalArgumentException();
        var user = users.getFirst(); String name = user.getString("user"), db = user.getString("db");
        if (name == null || name.isBlank() || db == null || db.isBlank()) throw new IllegalArgumentException();
        return PaymentReviewOperator.hash(new Document("db", db).append("user", name).toJson());
    }
    static Map<String, String> parse(String[] args) {
        if (args == null || args.length > 12) throw new IllegalArgumentException();
        var result = new HashMap<String, String>();
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || arg.length() > 8192) throw new IllegalArgumentException();
            String[] pair = arg.substring(2).split("=", 2);
            if (result.putIfAbsent(pair[0], pair.length == 1 ? "true" : pair[1]) != null) throw new IllegalArgumentException();
        }
        String action = result.getOrDefault("action", "inspect");
        Set<String> allowed = new HashSet<>(Set.of("action", "kind"));
        switch (action) {
            case "inspect" -> allowed.add("target");
            case "result" -> allowed.add("operation-id");
            case "schema" -> allowed.addAll(Set.of("apply", "writers-isolated"));
            case "resume" -> {
                allowed.addAll(Set.of("target", "operation-id", "version", "reason", "apply", "writers-isolated"));
                if ("account".equals(result.get("kind"))) allowed.addAll(Set.of("generation", "epoch", "failure", "selected"));
            }
            default -> throw new IllegalArgumentException();
        }
        if (!allowed.containsAll(result.keySet()) || !Set.of("account", "auth").contains(required(result, "kind"))) throw new IllegalArgumentException();
        flag(result, "apply"); flag(result, "writers-isolated");
        // Validate full command before opening any database connection.
        if (action.equals("resume")) command(result, PaymentReviewOperator.Action.valueOf(result.get("kind").toUpperCase(Locale.ROOT)));
        if (action.equals("inspect")) PaymentReviewOperator.token(required(result, "target"));
        if (action.equals("result")) PaymentReviewOperator.uuid(required(result, "operation-id"));
        return Map.copyOf(result);
    }
    static PaymentReviewOperator.Command command(Map<String, String> o, PaymentReviewOperator.Action kind) {
        var selected = new TreeMap<String, Long>();
        boolean account = kind == PaymentReviewOperator.Action.ACCOUNT;
        if (account) {
            String value = required(o, "selected");
            if (!"none".equals(value)) for (String item : value.split(",", -1)) {
                String[] pair = item.split(":", -1);
                if (pair.length != 2 || selected.putIfAbsent(pair[0], number(pair[1])) != null) throw new IllegalArgumentException();
            }
            PaymentReviewOperator.uuid(required(o, "target")); PaymentReviewOperator.uuid(required(o, "epoch"));
        }
        var reason = PaymentReviewOperator.Reason.valueOf(required(o, "reason"));
        if (!account && reason != PaymentReviewOperator.Reason.CREDENTIALS_FIXED) throw new IllegalArgumentException();
        return new PaymentReviewOperator.Command(required(o, "operation-id"), required(o, "target"), number(required(o, "version")),
                account ? number(required(o, "generation")) : 0, account ? required(o, "epoch") : null,
                account ? PaymentSyncService.LookupFailure.valueOf(required(o, "failure")) : null, reason, selected);
    }
    static long number(String value) {
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
        return Long.parseLong(value);
    }
    private static boolean flag(Map<String, String> m, String key) {
        String value = m.getOrDefault(key, "false");
        if (!Set.of("true", "false").contains(value)) throw new IllegalArgumentException();
        return Boolean.parseBoolean(value);
    }
    private static String required(Map<String, String> m, String key) {
        String value = m.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException(); return value;
    }
}
