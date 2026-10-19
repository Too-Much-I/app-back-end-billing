package web.tosunsaeng.billing.domain.benefit.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class BenefitDefinitionTest {

    @Test
    void createsApprovedFreeExamOnceDefinition() {
        Instant createdAt = Instant.parse("2026-08-28T00:00:00Z");

        BenefitDefinition definition = BenefitDefinition.freeExamOnce(createdAt);

        assertThat(definition.getBenefitCode()).isEqualTo("FREE_EXAM_ONCE");
        assertThat(definition.getDisplayName()).isEqualTo("무료 모의고사 응시권");
        assertThat(definition.getEntitlementModel())
                .isEqualTo(BenefitDefinition.EntitlementModel.UNIT);
        assertThat(definition.getUnitType())
                .isEqualTo(BenefitDefinition.UnitType.EXAM_ATTEMPT);
        assertThat(definition.getDefaultGrantUnits()).isOne();
        assertThat(definition.getPolicyVersion()).isOne();
        assertThat(definition.isActive()).isTrue();
        assertThat(definition.getCreatedAt()).isEqualTo(createdAt);
        assertThat(definition.hasApprovedFreeExamOncePolicy()).isTrue();
    }

    @Test
    void validatesStableBenefitCodeFormat() {
        assertThat(BenefitDefinition.isValidBenefitCode("FREE_EXAM_ONCE")).isTrue();
        assertThat(BenefitDefinition.isValidBenefitCode("A")).isTrue();
        assertThat(BenefitDefinition.isValidBenefitCode("free_exam_once")).isFalse();
        assertThat(BenefitDefinition.isValidBenefitCode("FREE-EXAM-ONCE")).isFalse();
        assertThat(BenefitDefinition.isValidBenefitCode("_FREE_EXAM_ONCE")).isFalse();
        assertThat(BenefitDefinition.isValidBenefitCode("A".repeat(65))).isFalse();
        assertThat(BenefitDefinition.isValidBenefitCode(null)).isFalse();
    }

    @Test
    void toStringContainsNoDisplayMetadata() {
        BenefitDefinition definition = BenefitDefinition.freeExamOnce(
                Instant.parse("2026-08-28T00:00:00Z")
        );

        assertThat(definition.toString())
                .contains("FREE_EXAM_ONCE", "policyVersion=1", "active=true")
                .doesNotContain("무료 모의고사 응시권");
    }
}
