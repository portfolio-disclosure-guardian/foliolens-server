package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureEvidenceEntity;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEntity;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEntityMapper;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEvidenceEntity;
import com.foliolens.backend.disclosure.repository.DisclosureFactRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureFactLookupServiceTest {

    @Test
    void VERIFIED_Fact와_중복_제거된_Evidence와_누락_key를_반환한다() {
        DisclosureFactRepository repository = mock(
                DisclosureFactRepository.class
        );
        DisclosureFactEntityMapper mapper = mock(
                DisclosureFactEntityMapper.class
        );
        DisclosureFactLookupService service = new DisclosureFactLookupService(
                repository,
                mapper
        );

        UUID disclosureId = UUID.randomUUID();
        DisclosureFactEntity amountEntity = mock(DisclosureFactEntity.class);
        DisclosureFactEntity purposeEntity = mock(DisclosureFactEntity.class);
        DisclosureEvidenceEntity sharedEvidenceEntity = mock(
                DisclosureEvidenceEntity.class
        );
        DisclosureFactEvidenceEntity amountLink = mock(
                DisclosureFactEvidenceEntity.class
        );
        DisclosureFactEvidenceEntity purposeLink = mock(
                DisclosureFactEvidenceEntity.class
        );
        DisclosureFact amount = mock(DisclosureFact.class);
        DisclosureFact purpose = mock(DisclosureFact.class);
        DisclosureEvidence sharedEvidence = mock(DisclosureEvidence.class);

        when(repository.findAllForLookup(
                Set.of(disclosureId),
                Set.of(
                        "facility.amount",
                        "facility.purpose",
                        "facility.end_date"
                ),
                FactValidationStatus.VERIFIED
        )).thenReturn(List.of(amountEntity, purposeEntity));
        when(mapper.toDomain(amountEntity)).thenReturn(amount);
        when(mapper.toDomain(purposeEntity)).thenReturn(purpose);
        when(amount.factKey()).thenReturn("facility.amount");
        when(purpose.factKey()).thenReturn("facility.purpose");
        when(amountEntity.getEvidenceLinks()).thenReturn(List.of(amountLink));
        when(purposeEntity.getEvidenceLinks()).thenReturn(List.of(purposeLink));
        when(amountLink.getDisclosureEvidence()).thenReturn(
                sharedEvidenceEntity
        );
        when(purposeLink.getDisclosureEvidence()).thenReturn(
                sharedEvidenceEntity
        );
        when(mapper.toDomain(sharedEvidenceEntity)).thenReturn(sharedEvidence);
        when(sharedEvidence.evidenceId()).thenReturn(UUID.randomUUID());
        when(sharedEvidence.status()).thenReturn(EvidenceStatus.VERIFIED);

        DisclosureFactLookupResult result = service.lookup(
                Set.of(disclosureId),
                List.of(
                        "facility.amount",
                        "facility.purpose",
                        "facility.end_date"
                )
        );

        assertThat(result.facts()).containsExactly(amount, purpose);
        assertThat(result.evidences()).containsExactly(sharedEvidence);
        assertThat(result.missingFactKeys())
                .containsExactly("facility.end_date");
        verify(repository).findAllForLookup(
                Set.of(disclosureId),
                Set.of(
                        "facility.amount",
                        "facility.purpose",
                        "facility.end_date"
                ),
                FactValidationStatus.VERIFIED
        );
    }

    @Test
    void 공시가_없으면_DB를_조회하지_않고_모든_key를_누락으로_반환한다() {
        DisclosureFactRepository repository = mock(
                DisclosureFactRepository.class
        );
        DisclosureFactLookupService service = new DisclosureFactLookupService(
                repository,
                mock(DisclosureFactEntityMapper.class)
        );

        DisclosureFactLookupResult result = service.lookup(
                Set.of(),
                List.of("facility.amount")
        );

        assertThat(result.facts()).isEmpty();
        assertThat(result.evidences()).isEmpty();
        assertThat(result.missingFactKeys())
                .containsExactly("facility.amount");
    }
}
