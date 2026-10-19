package web.tosunsaeng.billing.domain.payment.cleanup;

import java.time.*;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.provider.*;
import static web.tosunsaeng.billing.domain.payment.cleanup.CleanupEvidence.*;

/** Authenticated project/environment-scoped observations, NOT proof of Store-wide absence. */
public final class CleanupProviderReader implements ProviderReader {
    private final RevenueCatReadClient client;
    private final Clock clock;
    private final String environment;
    public CleanupProviderReader(RevenueCatReadClient client, Clock clock, String environment) {
        this.client = Objects.requireNonNull(client); this.clock = Objects.requireNonNull(clock);
        this.environment = Objects.requireNonNull(environment);
    }
    @Override public Provider read(Subject subject, Instant deadline) {
        if (environment.equals(subject.environment()) && subject.accountId() == null && subject.references().isEmpty()
                && clock.instant().isBefore(deadline)) return new Provider(Observation.ABSENT_OBSERVED, true, null);
        if (!environment.equals(subject.environment()) || subject.references().isEmpty()
                || subject.references().size() > 100 || new HashSet<>(subject.references()).size() != subject.references().size())
            return unknown(Reason.PROVIDER_INCOMPLETE);
        long started = System.nanoTime();
        int pages = 0;
        try {
            for (String ref : subject.references()) {
                Set<String> seen = new HashSet<>();
                String cursor = null;
                do {
                    if (++pages > 100 || expired(deadline, started)) return unknown(Reason.PROVIDER_INCOMPLETE);
                    Duration remaining = Duration.between(clock.instant(), deadline);
                    Duration monotonic = Duration.ofNanos(5_000_000_000L - (System.nanoTime() - started));
                    if (remaining.compareTo(monotonic) > 0) remaining = monotonic;
                    if (remaining.isNegative() || remaining.isZero()) return unknown(Reason.PROVIDER_INCOMPLETE);
                    var page = client.purchases(ref, cursor, remaining);
                    if (expired(deadline, started)) return unknown(Reason.PROVIDER_INCOMPLETE);
                    if (page.items().stream().anyMatch(item -> item.observation() != null))
                        return new Provider(Observation.FOUND, false, null);
                    if (!page.items().isEmpty()) return unknown(Reason.PROVIDER_CONTRACT_ERROR);
                    cursor = page.nextCursor();
                    if (cursor != null && !seen.add(cursor)) return unknown(Reason.PROVIDER_INCOMPLETE);
                } while (cursor != null);
            }
            return new Provider(Observation.ABSENT_OBSERVED, true, null);
        } catch (RevenueCatFailure failure) {
            return unknown(switch (failure.kind()) {
                case AUTH -> Reason.PROVIDER_AUTH_ERROR;
                case CONTRACT, REVIEW -> Reason.PROVIDER_CONTRACT_ERROR;
                case NOT_FOUND -> Reason.PROVIDER_INCOMPLETE;
                case RETRY -> Reason.PROVIDER_UNAVAILABLE;
            });
        } catch (RuntimeException unavailable) { return Provider.unknown(); }
    }
    private boolean expired(Instant deadline, long started) {
        return !clock.instant().isBefore(deadline) || System.nanoTime() - started >= 5_000_000_000L;
    }
    private static Provider unknown(Reason reason) { return new Provider(Observation.UNKNOWN, false, reason); }
}
