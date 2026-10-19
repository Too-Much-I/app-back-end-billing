package web.tosunsaeng.billing.domain.examdeletion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class DeletionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(DeletionConfiguration.class);
    @Test void offDoesNotNeedDatabaseOrAdmission() {
        runner.run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(DeletionService.class);
            assertThat(context.getBean(DeletionProperties.class).isEnabled()).isFalse(); });
    }
    @Test void onCannotStartWithoutCompleteAdmissionFoundation() {
        runner.withPropertyValues("app.billing.exam-deletion.enabled=true").run(context -> assertThat(context).hasFailed());
    }
}
