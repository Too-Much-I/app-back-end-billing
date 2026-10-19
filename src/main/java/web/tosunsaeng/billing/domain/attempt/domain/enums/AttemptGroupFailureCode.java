package web.tosunsaeng.billing.domain.attempt.domain.enums;

import java.util.Arrays;
import java.util.Optional;

public enum AttemptGroupFailureCode {
    REQUIRED_RESULTS_UNAVAILABLE,
    SUMMARY_UNAVAILABLE,
    GRADING_DEADLINE_EXCEEDED,
    RESULT_INTEGRITY_VIOLATION;

    public static Optional<AttemptGroupFailureCode> fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(value))
                .findFirst();
    }
}
