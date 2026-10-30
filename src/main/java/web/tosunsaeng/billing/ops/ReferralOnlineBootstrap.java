package web.tosunsaeng.billing.ops;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import java.util.*;
import org.bson.Document;
import web.tosunsaeng.billing.domain.reward.ReferralRewardSchema;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;

/** Explicit one-off CLI, never a Spring bean or startup initializer. No deletes or control writes. */
public final class ReferralOnlineBootstrap {
    private ReferralOnlineBootstrap() {}
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.mongodb.driver"))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            require(args.length == 1 && Set.of("check", "install").contains(args[0]));
            String name = System.getenv("BILLING_EXPECTED_MONGO_DATABASE"), host = System.getenv("BILLING_EXPECTED_MONGO_HOST");
            require(name != null && name.matches("[A-Za-z0-9_-]+[-_]prod") && host != null);
            require("true".equals(System.getenv("REFERRAL_ONLINE_WRITERS_DISABLED")));
            require("false".equals(System.getenv("REFERRAL_REWARD_ENABLED")));
            var uri = new ConnectionString(System.getenv("MONGODB_URI"));
            require(name.equals(uri.getDatabase()) && uri.getHosts().equals(List.of(host)));
            try (var client = MongoClients.create(uri)) {
                var db = client.getDatabase(name);
                var controls = db.getCollection(PaymentRecoveryFence.COLLECTION);
                var before = controls.find().sort(new Document("_id", 1)).into(new ArrayList<>());
                require(before.stream().anyMatch(d -> "PRODUCTION".equals(d.getString("environment"))
                        && "billing".equals(d.getString("consumer")) && "OPEN".equals(d.getString("barrier"))));
                if ("install".equals(args[0])) ReferralRewardSchema.installOnlineWhileReferralDisabled(db, true);
                else ReferralRewardSchema.verify(db);
                require(before.equals(controls.find().sort(new Document("_id", 1)).into(new ArrayList<>())));
                System.out.println("REFERRAL_ONLINE_OK revision=" + ReferralRewardSchema.VERSION + " recoveryUnchanged=true mode=" + args[0]);
            }
        } catch (Exception e) {
            System.out.println("REFERRAL_ONLINE_FAILED " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException(); }
}
