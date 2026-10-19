package web.tosunsaeng.billing.domain.payment.sync;

import java.util.*;
import org.bson.Document;

/** The client hint has no trusted Store environment. Candidates never grant entitlement. */
public final class SyncTransactionKeys {
    private SyncTransactionKeys() {}
    public static Object query(Document command) {
        String alternate = command.getString("sandboxTransactionKey");
        return alternate == null ? command.getString("transactionKey")
                : new Document("$in", List.of(command.getString("transactionKey"), alternate));
    }
    public static boolean matches(Document command, Object key) {
        return Objects.equals(command.get("transactionKey"), key)
                || command.get("sandboxTransactionKey") != null && Objects.equals(command.get("sandboxTransactionKey"), key);
    }
}
