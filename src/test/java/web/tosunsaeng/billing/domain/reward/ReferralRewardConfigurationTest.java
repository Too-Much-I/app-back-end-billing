package web.tosunsaeng.billing.domain.reward;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ReferralRewardConfigurationTest {
    @Test void defaultsOffWithoutDependencies() {
        new ApplicationContextRunner().withUserConfiguration(ReferralRewardConfiguration.class).run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(ReferralRewardService.class);
            assertThat(c.getBean(ReferralRewardProperties.class).isEnabled()).isFalse();
        });
    }
    @Test void enabledRequiresFoundation() {
        new ApplicationContextRunner().withUserConfiguration(ReferralRewardConfiguration.class)
                .withPropertyValues("app.rewards.referral.enabled=true").run(c -> assertThat(c).hasFailed());
    }
    @Test void normalizationIsAsciiOnlyAndBounded() {
        assertThat(ReferralRewardService.normalize("  abcdefghjk  ")).isEqualTo("ABCDEFGHJK");
        for (String code : java.util.List.of("a".repeat(65), "ＡBCDEFGHJK", "ABCDEFGHIJ", "ABCD EFGHJK", "\tabcdefghjk", "", "ABC"))
            assertThatThrownBy(() -> ReferralRewardService.normalize(code)).isInstanceOf(web.tosunsaeng.billing.domain.payment.exception.PaymentException.class);
    }
    @Test void cannotEnableTogetherWithSharingAndDoesNotTouchDatabase() {
        var payment = new web.tosunsaeng.billing.domain.payment.config.PaymentProperties();
        payment.setPurchaseAccountEnabled(true); payment.setMemberEvidenceEnabled(true); payment.setEntitlementQueryEnabled(true);
        payment.getRecovery().setFenceRequired(true);
        var jwt = new web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties(); jwt.setPublicConnectorEnabled(true);
        var store = org.mockito.Mockito.mock(web.tosunsaeng.billing.domain.payment.repository.PaymentStore.class);
        var env = new org.springframework.mock.env.MockEnvironment().withProperty("billing.reservation.paid-recovery-enabled", "true")
                .withProperty("app.rewards.invite-share.enabled", "true");
        assertThatThrownBy(() -> new ReferralRewardConfiguration().referralRewardService(null, null, null, store, payment, jwt,
                null, java.time.Clock.systemUTC(), env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("invite-share OFF");
        org.mockito.Mockito.verifyNoInteractions(store);
    }
}
