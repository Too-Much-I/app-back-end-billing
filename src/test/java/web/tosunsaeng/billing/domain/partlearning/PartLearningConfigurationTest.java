package web.tosunsaeng.billing.domain.partlearning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class PartLearningConfigurationTest {
    @Test void offNeedsNoDependencies() {
        assertThat(new PartLearningProperties().isEnabled()).isFalse();
        new ApplicationContextRunner().withUserConfiguration(PartLearningConfiguration.class)
                .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(PartLearningService.class));
    }
    @Test void onWithoutSnapshotAndRecoveryReadinessFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(PartLearningConfiguration.class)
                .withPropertyValues("app.billing.part-learning-eligibility.enabled=true")
                .run(c -> assertThat(c).hasFailed());
    }
}
