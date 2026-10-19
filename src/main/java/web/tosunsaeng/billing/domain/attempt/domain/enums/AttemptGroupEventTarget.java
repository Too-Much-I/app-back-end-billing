package web.tosunsaeng.billing.domain.attempt.domain.enums;

import java.util.Arrays;
import java.util.Optional;

public enum AttemptGroupEventTarget {
    GRADING,
    COMPLETED,
    RETAKE_AVAILABLE;

    public static Optional<AttemptGroupEventTarget> fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(value))
                .findFirst();
    }
}
