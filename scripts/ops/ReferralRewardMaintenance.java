import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import org.bson.Document;
import java.util.*;
import web.tosunsaeng.billing.domain.reward.ReferralRewardSchema;
import web.tosunsaeng.billing.domain.payment.recovery.PaymentRecoveryFence;
/** Test-only additive DDL. The operator must verify every test writer task is STOPPED.
 * Does not purge data or change recovery controls; failure leaves the service OFF.
 * Run using the deployed jar and its dependencies, never the production database. */
public final class ReferralRewardMaintenance {
 public static void main(String[] args) {
  try {
   String name=System.getenv("BILLING_EXPECTED_MONGO_DATABASE"), host=System.getenv("BILLING_EXPECTED_MONGO_HOST");
   require(name!=null && name.matches("[A-Za-z0-9_-]+[-_]test") && host!=null);
   require("true".equals(System.getenv("REFERRAL_MAINTENANCE_WRITERS_STOPPED")));
   var uri=new ConnectionString(System.getenv("MONGODB_URI"));
   require(name.equals(uri.getDatabase()) && uri.getHosts().equals(List.of(host)));
   try(var client=MongoClients.create(uri)) {
    var db=client.getDatabase(name);
    var controls=db.getCollection(PaymentRecoveryFence.COLLECTION);
    require(controls.countDocuments()==1);
    Document before=controls.find().first();
    require("SANDBOX".equals(before.getString("environment")) && "billing".equals(before.getString("consumer")) && "OPEN".equals(before.getString("barrier")));
    ReferralRewardSchema.installUnderMaintenance(db,true);
    ReferralRewardSchema.verify(db);
    require(before.equals(controls.find().first()));
    System.out.println("REFERRAL_SCHEMA_OK revision="+ReferralRewardSchema.VERSION+" recoveryUnchanged=true");
   }
  } catch(Exception e) {System.out.println("REFERRAL_SCHEMA_FAILED "+e.getClass().getSimpleName()); System.exit(1);}
 }
 static void require(boolean ok) {if(!ok) throw new IllegalStateException();}
}
