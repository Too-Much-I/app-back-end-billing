package web.tosunsaeng.billing.domain.reanswer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ReanswerConfigurationTest {
    @Test void offNeedsNoDependencies() {
        assertThat(new ReanswerProperties().isEnabled()).isFalse();
        new ApplicationContextRunner().withUserConfiguration(ReanswerConfiguration.class)
                .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(ReanswerService.class));
    }
    @Test void onWithoutSnapshotAndRecoveryReadinessFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(ReanswerConfiguration.class)
                .withPropertyValues("app.billing.reanswer-eligibility.enabled=true")
                .run(c -> assertThat(c).hasFailed());
    }
}
