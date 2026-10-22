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
  String phase="configuration";
  try {
   String name=System.getenv("BILLING_EXPECTED_MONGO_DATABASE"), host=System.getenv("BILLING_EXPECTED_MONGO_HOST");
   require(name!=null && name.matches("[A-Za-z0-9_-]+[-_]test") && host!=null);
   require("true".equals(System.getenv("REFERRAL_MAINTENANCE_WRITERS_STOPPED")));
   var uri=new ConnectionString(System.getenv("MONGODB_URI"));
   require(name.equals(uri.getDatabase()) && uri.getHosts().equals(List.of(host)));
   try(var client=MongoClients.create(uri)) {
    phase="recovery-control";
    var db=client.getDatabase(name);
    var controls=db.getCollection(PaymentRecoveryFence.COLLECTION);
    if (args.length==1 && "check".equals(args[0])) {
     System.out.println("CONTROL_COUNT="+controls.countDocuments());
     for(Document d:controls.find()) System.out.println("CONTROL environment="+d.getString("environment")+" consumer="+d.getString("consumer")+" barrier="+d.getString("barrier"));
     web.tosunsaeng.billing.domain.reservation.paid.PaidExamSchema.verify(db);
     System.out.println("PAID_SCHEMA_OK");
     phase="referral-verify";
     ReferralRewardSchema.verify(db);
     System.out.println("REFERRAL_VERIFY_OK");
     return;
    }
    require(controls.countDocuments()==1);
    Document before=controls.find().first();
    System.out.println("RECOVERY_CONTROL environment="+before.getString("environment")+" consumer="+before.getString("consumer")+" barrier="+before.getString("barrier"));
    require("SANDBOX".equals(before.getString("environment")) && "billing".equals(before.getString("consumer")) && "OPEN".equals(before.getString("barrier")));
    phase="paid-schema";
    web.tosunsaeng.billing.domain.reservation.paid.PaidExamSchema.verify(db);
    phase="referral-install";
    ReferralRewardSchema.installUnderMaintenance(db,true);
    ReferralRewardSchema.verify(db);
    phase="recovery-unchanged";
    require(before.equals(controls.find().first()));
    System.out.println("REFERRAL_SCHEMA_OK revision="+ReferralRewardSchema.VERSION+" recoveryUnchanged=true");
   }
  } catch(Exception e) {System.out.println("REFERRAL_SCHEMA_FAILED phase="+phase+" type="+e.getClass().getSimpleName()); System.exit(1);}
 }
 static void require(boolean ok) {if(!ok) throw new IllegalStateException();}
}
