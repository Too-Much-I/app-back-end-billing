package web.tosunsaeng.billing.trialeligibility.domain;

import java.util.Arrays;
import java.util.Optional;

public enum TrialEligibilityEventType {
    VERIFIED("PhoneEligibilityBindingVerified"),
    REVOKED("PhoneEligibilityBindingRevoked");

    private final String wireName;

    TrialEligibilityEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<TrialEligibilityEventType> fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(value -> value.wireName.equals(wireName))
                .findFirst();
    }
}
