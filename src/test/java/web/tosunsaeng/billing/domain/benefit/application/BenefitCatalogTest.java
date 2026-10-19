package web.tosunsaeng.billing.domain.benefit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.benefit.repository.BenefitDefinitionRepository;

class BenefitCatalogTest {

    private final BenefitDefinitionRepository repository = mock(
            BenefitDefinitionRepository.class
    );
    private final BenefitCatalog catalog = new BenefitCatalog(repository);

    @Test
    void returnsOnlyApprovedActiveDefinition() {
        BenefitDefinition definition = BenefitDefinition.freeExamOnce(
                Instant.parse("2026-08-28T00:00:00Z")
        );
        when(repository.findByCode(BenefitDefinition.FREE_EXAM_ONCE))
                .thenReturn(Optional.of(definition));

        assertThat(catalog.findActiveFreeExamOnce()).contains(definition);
    }

    @Test
    void missingDefinitionIsUnavailable() {
        when(repository.findByCode(BenefitDefinition.FREE_EXAM_ONCE))
                .thenReturn(Optional.empty());

        assertThat(catalog.findActiveFreeExamOnce()).isEmpty();
    }

    @Test
    void inactiveOrDriftedDefinitionIsUnavailable() {
        BenefitDefinition drifted = mock(BenefitDefinition.class);
        when(drifted.hasApprovedFreeExamOncePolicy()).thenReturn(false);
        when(repository.findByCode(BenefitDefinition.FREE_EXAM_ONCE))
                .thenReturn(Optional.of(drifted));

        assertThat(catalog.findActiveFreeExamOnce()).isEmpty();
    }
}
