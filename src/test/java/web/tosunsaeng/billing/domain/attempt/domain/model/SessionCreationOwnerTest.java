package web.tosunsaeng.billing.domain.attempt.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionCreationOwnerTest {
    private static final String USER = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Test void rejectsMissingTrialEpochAndFabricatedMemberEpoch() {
        assertThatThrownBy(() -> new SessionCreationOwner("test", SessionCreationOwner.Kind.TRIAL_SUBJECT, USER, "subject", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionCreationOwner("test", SessionCreationOwner.Kind.TRIAL_SUBJECT, USER, "subject", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionCreationOwner("test", SessionCreationOwner.Kind.MEMBER_ACCOUNT, USER, "account", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnboundEnvironmentNonCanonicalUserAndEmptyReference() {
        assertThatThrownBy(() -> new SessionCreationOwner("SANDBOX", SessionCreationOwner.Kind.MEMBER_ACCOUNT, USER, "account", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionCreationOwner("test", SessionCreationOwner.Kind.MEMBER_ACCOUNT, USER.toUpperCase(), "account", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionCreationOwner("test", SessionCreationOwner.Kind.MEMBER_ACCOUNT, USER, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
