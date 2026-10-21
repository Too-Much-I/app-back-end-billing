package web.tosunsaeng.billing.domain.payment.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.mongodb.MongoException;
import org.bson.Document;
import org.springframework.stereotype.Service;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;

@Service
public class PaymentRateLimiter {
    public enum Route {
        PRODUCTS(60), PURCHASE_ACCOUNT(10), SYNC(12), ENTITLEMENTS(60), INVITE_QUERY(60), INVITE_AWARD(10);
        final long limit;
        Route(long limit) { this.limit = limit; }
    }
    private final PaymentStore store;
    private final PaymentProperties properties;
    private final Clock clock;
    public PaymentRateLimiter(PaymentStore store, PaymentProperties properties, Clock clock) {
        this.store = store; this.properties = properties; this.clock = clock;
    }
    /** 0 means admitted; otherwise returns Retry-After seconds. */
    public long acquire(boolean purchase, String userId) {
        return acquire(purchase ? Route.PURCHASE_ACCOUNT : Route.PRODUCTS, userId);
    }
    public long acquire(Route route, String userId) {
        Instant now = clock.instant(), start = now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        long wait = Math.max(1, (Duration.between(now, start.plusSeconds(60)).toNanos() + 999_999_999L) / 1_000_000_000L);
        var key = new Document("environment", properties.getEnvironment()).append("routeKey", route.name())
                .append("subjectKey", subjectKey(userId)).append("windowStart", Date.from(start));
        var buckets = store.database().getCollection("payment_rate_limits");
        var belowLimit = new Document(key).append("count", new Document("$lt", route.limit));
        var increment = new Document("$inc", new Document("count", 1L));
        if (buckets.updateOne(belowLimit, increment).getModifiedCount() == 1) return 0;
        try {
            buckets.insertOne(new Document(key).append("_id", UUID.randomUUID().toString()).append("count", 1L)
                    .append("purgeAt", Date.from(start.plusSeconds(120))));
            return 0;
        } catch (MongoException duplicate) {
            if (duplicate.getCode() != 11000) throw PaymentException.unavailable();
            return buckets.updateOne(belowLimit, increment).getModifiedCount() == 1 ? 0 : wait;
        }
    }
    private String subjectKey(String userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(properties.getLimiterKey()), "HmacSHA256"));
            for (String part : List.of(properties.getEnvironment(), userId)) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                mac.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); mac.update(bytes);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception invalid) { throw PaymentException.unavailable(); }
    }
}
