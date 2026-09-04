package com.foliolens.backend.disclosure.domain.fact.facility.generation;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceLocation;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactSet;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FactValueNormalizationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerificationResult;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.VerifiedFacilityEvidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityInvestmentFactGeneratorTest {

    private static final String RECEIPT_NO = "20240424800596";

    private final FacilityInvestmentFactGenerator generator =
            new FacilityInvestmentFactGenerator();

    @Test
    void VERIFIED_Evidence로부터_원문값과_정규화값을_모두_보존한_Fact를_만든다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        DisclosureEvidence verifiedEvidence = verifiedAmountEvidence(
                disclosureId,
                documentId,
                "5,296,200,000,000",
                "원"
        );
        FacilityInvestmentEvidenceVerificationResult verification =
                verificationResult(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        verifiedEvidence,
                        FactValueNormalizationResult.mapped(
                                new DecimalFactValue(
                                        new BigDecimal("5296200000000")
                                ),
                                "KRW"
                        )
                );

        FacilityInvestmentFactSet factSet = generator.generate(
                disclosureId,
                documentId,
                RECEIPT_NO,
                verification
        );

        DisclosureFact fact = factSet
                .find(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow();
        assertThat(fact.rawValue()).isEqualTo("5,296,200,000,000");
        assertThat(fact.rawUnit()).isEqualTo("원");
        assertThat(fact.normalizedValue())
                .isEqualTo(new DecimalFactValue(
                        new BigDecimal("5296200000000")
                ));
        assertThat(fact.normalizedUnit()).isEqualTo("KRW");
        assertThat(fact.currency()).isEqualTo("KRW");
        assertThat(fact.sourceReceiptNo()).isEqualTo(RECEIPT_NO);
        assertThat(fact.validationStatus())
                .isEqualTo(FactValidationStatus.VERIFIED);
        assertThat(fact.evidenceIds())
                .containsExactly(verifiedEvidence.evidenceId());
    }

    @Test
    void 원문_위치와_evidenceId가_Fact와_Evidence_사이에_연결된다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        DisclosureEvidence verifiedEvidence = verifiedAmountEvidence(
                disclosureId,
                documentId,
                "5,296,200,000,000",
                "원"
        );
        FacilityInvestmentEvidenceVerificationResult verification =
                verificationResult(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        verifiedEvidence,
                        FactValueNormalizationResult.mapped(
                                new DecimalFactValue(
                                        new BigDecimal("5296200000000")
                                ),
                                "KRW"
                        )
                );

        FacilityInvestmentFactSet factSet = generator.generate(
                disclosureId,
                documentId,
                RECEIPT_NO,
                verification
        );

        DisclosureFact fact = factSet
                .find(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow();
        assertThat(fact.evidenceIds()).hasSize(1);
        UUID linkedEvidenceId = fact.evidenceIds().get(0);
        assertThat(linkedEvidenceId).isEqualTo(verifiedEvidence.evidenceId());
        assertThat(verifiedEvidence.location().tableRowIndex()).isEqualTo(2);
        assertThat(verifiedEvidence.location().tableCellIndex()).isEqualTo(2);
    }

    @Test
    void 누락된_핵심_Fact는_FactSet에서_확인할_수_있다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        FacilityInvestmentEvidenceVerificationResult verification =
                new FacilityInvestmentEvidenceVerificationResult(
                        Map.of(),
                        Map.of()
                );

        FacilityInvestmentFactSet factSet = generator.generate(
                disclosureId,
                documentId,
                RECEIPT_NO,
                verification
        );

        assertThat(factSet.hasAllCoreFacts()).isFalse();
        assertThat(factSet.missingCoreDefinitions())
                .contains(FacilityInvestmentFactDefinition.AMOUNT);
    }

    @Test
    void 같은_입력으로_재실행해도_factId가_동일하다() {
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        DisclosureEvidence verifiedEvidence = verifiedAmountEvidence(
                disclosureId,
                documentId,
                evidenceId,
                "5,296,200,000,000",
                "원"
        );
        FacilityInvestmentEvidenceVerificationResult verification =
                verificationResult(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        verifiedEvidence,
                        FactValueNormalizationResult.mapped(
                                new DecimalFactValue(
                                        new BigDecimal("5296200000000")
                                ),
                                "KRW"
                        )
                );

        FacilityInvestmentFactSet first = generator.generate(
                disclosureId,
                documentId,
                RECEIPT_NO,
                verification
        );
        FacilityInvestmentFactSet second = generator.generate(
                disclosureId,
                documentId,
                RECEIPT_NO,
                verification
        );

        UUID firstFactId = first
                .find(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow()
                .factId();
        UUID secondFactId = second
                .find(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow()
                .factId();
        assertThat(firstFactId).isEqualTo(secondFactId);
    }

    private FacilityInvestmentEvidenceVerificationResult verificationResult(
            FacilityInvestmentFactDefinition definition,
            DisclosureEvidence verifiedEvidence,
            FactValueNormalizationResult normalization
    ) {
        Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence> verified =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        verified.put(
                definition,
                new VerifiedFacilityEvidence(
                        definition,
                        verifiedEvidence,
                        normalization
                )
        );
        return new FacilityInvestmentEvidenceVerificationResult(
                verified,
                Map.of()
        );
    }

    private DisclosureEvidence verifiedAmountEvidence(
            UUID disclosureId,
            UUID documentId,
            String rawValue,
            String rawUnit
    ) {
        return verifiedAmountEvidence(
                disclosureId,
                documentId,
                UUID.randomUUID(),
                rawValue,
                rawUnit
        );
    }

    private DisclosureEvidence verifiedAmountEvidence(
            UUID disclosureId,
            UUID documentId,
            UUID evidenceId,
            String rawValue,
            String rawUnit
    ) {
        return new DisclosureEvidence(
                evidenceId,
                disclosureId,
                documentId,
                RECEIPT_NO,
                "20240424800596.xml",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                UUID.randomUUID(),
                "신규시설투자등 > 투자내역",
                UUID.randomUUID(),
                EvidenceBlockType.TABLE_CELL,
                "table-1",
                new DisclosureEvidenceLocation(97, 97, null, 2, 2),
                new DisclosureEvidenceValue(
                        "투자금액(원) | " + rawValue,
                        "2. 투자내역 > 투자금액(원)",
                        null,
                        rawValue,
                        rawUnit,
                        null
                ),
                EvidenceStatus.VERIFIED
        );
    }
}
