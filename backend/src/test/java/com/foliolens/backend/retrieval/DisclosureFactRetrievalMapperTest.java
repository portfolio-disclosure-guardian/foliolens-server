package com.foliolens.backend.retrieval;

import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import com.foliolens.backend.disclosure.domain.fact.AccountingBasis;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceLocation;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactGenerationMethod;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchHit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureFactRetrievalMapperTest {

    private final DisclosureFactRetrievalMapper mapper =
            new DisclosureFactRetrievalMapper();

    @Test
    void DECIMAL_Fact와_원문_Evidence를_검색_계약으로_변환한다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        DisclosureEvidence evidence = evidence(
                evidenceId,
                disclosureId,
                documentId,
                sectionId,
                blockId
        );
        DisclosureFact fact = new DisclosureFact(
                factId,
                disclosureId,
                documentId,
                "facility.amount",
                FactValueType.DECIMAL,
                "5,296,200,000,000",
                "원",
                new DecimalFactValue(new BigDecimal("5296200000000")),
                "KRW",
                "KRW",
                null,
                null,
                LocalDate.of(2024, 4, 24),
                AccountingBasis.UNKNOWN,
                FactGenerationMethod.DIRECT_NORMALIZED,
                FactAvailabilityStatus.AVAILABLE,
                FactNormalizationStatus.MAPPED,
                FactValidationStatus.VERIFIED,
                "20240424800596",
                "facility-v1",
                List.of(evidenceId)
        );

        RetrievedFact mappedFact = mapper.toRetrievedFact(fact);
        RetrievedEvidence mappedEvidence = mapper.toRetrievedEvidence(evidence);

        assertThat(mappedFact.normalizedValue())
                .isEqualTo("5296200000000");
        assertThat(mappedFact.unit()).isEqualTo("KRW");
        assertThat(mappedFact.evidenceIds())
                .containsExactly(evidenceId.toString());
        assertThat(mappedEvidence.documentId())
                .isEqualTo(documentId.toString());
        assertThat(mappedEvidence.sectionId())
                .isEqualTo(sectionId.toString());
        assertThat(mappedEvidence.status()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(mappedEvidence.content()).contains("5,296,200,000,000");

        DisclosureMetadataSearchHit metadata = metadata(disclosureId);
        assertThat(mapper.toRetrievedDocuments(
                List.of(evidence),
                Map.of(disclosureId, metadata)
        )).singleElement().satisfies(document -> {
            assertThat(document.documentId()).isEqualTo(documentId.toString());
            assertThat(document.companyName()).isEqualTo("SK하이닉스");
            assertThat(document.content()).contains("5,296,200,000,000");
        });
    }

    private DisclosureEvidence evidence(
            UUID evidenceId,
            UUID disclosureId,
            UUID documentId,
            UUID sectionId,
            UUID blockId
    ) {
        return new DisclosureEvidence(
                evidenceId,
                disclosureId,
                documentId,
                "20240424800596",
                "신규 시설투자 등",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                sectionId,
                "신규시설투자 > 투자내역",
                blockId,
                EvidenceBlockType.TABLE_CELL,
                "투자내역",
                new DisclosureEvidenceLocation(97, 97, "root", 2, 2),
                new DisclosureEvidenceValue(
                        "투자금액(원) 5,296,200,000,000",
                        "투자금액(원)",
                        "내용",
                        "5,296,200,000,000",
                        "원",
                        null
                ),
                EvidenceStatus.VERIFIED
        );
    }

    private DisclosureMetadataSearchHit metadata(UUID disclosureId) {
        return new DisclosureMetadataSearchHit(
                disclosureId,
                UUID.randomUUID(),
                "SK하이닉스",
                "000660",
                "20240424800596",
                LocalDate.of(2024, 4, 24),
                "신규시설투자등",
                DisclosureSourceGroup.EXCHANGE,
                DisclosureCategory.EXCHANGE,
                "신규시설투자등",
                false,
                SourceProvider.CONTEST,
                1,
                10.0,
                List.of("신규시설투자")
        );
    }
}
