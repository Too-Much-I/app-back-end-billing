package web.tosunsaeng.billing.domain.reward;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class InviteRewardConfigurationTest {
    @Test void defaultsOffWithoutAnyPaymentDependencies() {
        new ApplicationContextRunner().withUserConfiguration(InviteRewardConfiguration.class).run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(InviteRewardService.class);
            assertThat(c.getBean(InviteRewardProperties.class).isEnabled()).isFalse();
        });
    }
    @Test void cannotEnableWithoutTransactionalFoundation() {
        new ApplicationContextRunner().withUserConfiguration(InviteRewardConfiguration.class)
                .withPropertyValues("app.rewards.invite-share.enabled=true").run(c -> assertThat(c).hasFailed());
    }
}
