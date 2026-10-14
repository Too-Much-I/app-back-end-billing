package web.tosunsaeng.billing.domain.attempt.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Historical creation evidence only; never a grant of current access or deletion authority. */
public record SessionCreationOwner(String environment, Kind kind, String userId, String reference, Long epoch) {
    public enum Kind { TRIAL_SUBJECT, MEMBER_ACCOUNT }

    public SessionCreationOwner {
        Objects.requireNonNull(kind);
        if (!Set.of("test", "staging", "production").contains(environment))
            throw new IllegalArgumentException("Invalid owner environment");
        if (userId == null || !UUID.fromString(userId).toString().equals(userId))
            throw new IllegalArgumentException("Invalid owner user identifier");
        if (reference == null || reference.isBlank() || reference.length() > 128)
            throw new IllegalArgumentException("Invalid owner reference");
        if (kind == Kind.TRIAL_SUBJECT ? epoch == null || epoch < 1 : epoch != null)
            throw new IllegalArgumentException("Invalid owner epoch");
    }

    @Override public String toString() {
        return "SessionCreationOwner[kind=" + kind + ", references=REDACTED]";
    }
}
