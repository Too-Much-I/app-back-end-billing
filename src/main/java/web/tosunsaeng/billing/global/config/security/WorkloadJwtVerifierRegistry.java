package web.tosunsaeng.billing.global.config.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Fixed local issuer allowlist; no key union, refresh or verifier fallback. */
final class WorkloadJwtVerifierRegistry {
    record Verified(String service, WorkloadJwtCodec.Principal principal) {}
    private record Entry(String service, WorkloadJwtCodec codec) {}
    private final Map<String, Entry> verifiers;
    WorkloadJwtVerifierRegistry(WorkloadJwtProperties p, Clock clock) {
        Map<String, Entry> entries = new HashMap<>();
        if (p.isReceiverEnabled()) entries.put(p.identityIssuer(),
                new Entry("identity-service", new WorkloadJwtCodec(p.getIdentityPublicJwkSet(), p.identityIssuer(), clock)));
        if (p.isLearningCoreReceiverEnabled()) entries.put(p.learningCoreIssuer(),
                new Entry("learning-core-service", new WorkloadJwtCodec(p.getLearningCorePublicJwkSet(), p.learningCoreIssuer(), clock)));
        verifiers = Map.copyOf(entries);
    }
    Verified authenticate(String token) {
        Entry entry = verifiers.get(WorkloadJwtCodec.unverifiedIssuer(token));
        if (entry == null) throw new WorkloadJwtCodec.InvalidToken();
        return new Verified(entry.service(), entry.codec().authenticate(token));
    }
}
