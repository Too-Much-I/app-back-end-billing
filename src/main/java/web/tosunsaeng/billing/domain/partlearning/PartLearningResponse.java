package web.tosunsaeng.billing.domain.partlearning;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** String timestamps preserve Instant precision independently of the application Jackson settings. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PartLearningResponse(String userId, String decision, String reason, String checkedAt, String validUntil) {
    static PartLearningResponse result(String user, Instant now, String decision, String reason, Instant until) {
        return new PartLearningResponse(user, decision, reason, now.toString(), until == null ? null : until.toString());
    }
    @Override public String toString() { return "PartLearningResponse[REDACTED]"; }
}
