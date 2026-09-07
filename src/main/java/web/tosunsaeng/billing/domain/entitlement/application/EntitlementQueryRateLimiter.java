package web.tosunsaeng.billing.domain.entitlement.application;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

/** Task-local abuse control, not a distributed entitlement/financial counter. */
@Component
public class EntitlementQueryRateLimiter {
    private record Bucket(double tokens, long touchedAt) { }
    private final Map<String, Bucket> buckets = new LinkedHashMap<>();
    private final Clock clock;
    public EntitlementQueryRateLimiter(Clock clock) { this.clock = clock; }
    public synchronized boolean allow(String subject) {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> now - e.getValue().touchedAt() >= 300000);
        var previous = buckets.remove(subject);
        if (previous == null && buckets.size() >= 10000) {
            buckets.remove(buckets.keySet().iterator().next());
        }
        double tokens = previous == null ? 60 : Math.min(60, previous.tokens() + Math.max(0, now - previous.touchedAt()) / 1000d);
        boolean allowed = tokens >= 1;
        buckets.put(subject, new Bucket(allowed ? tokens - 1 : tokens, now));
        return allowed;
    }
}
