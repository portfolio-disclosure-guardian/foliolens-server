package com.foliolens.backend.disclosure.domain.fact.facility.verification;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceLocation;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidenceValue;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FacilityInvestmentValueNormalizer;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityInvestmentEvidenceVerifierTest {

    private static final String RECEIPT_NO = "20240424800596";

    private final FacilityInvestmentEvidenceVerifier verifier =
            new FacilityInvestmentEvidenceVerifier(
                    new FacilityInvestmentValueNormalizer()
            );

    @Test
    void 후보가_없으면_승격하지_않고_이유를_남긴다() {
        FacilityInvestmentEvidenceExtractionResult candidates =
                FacilityInvestmentEvidenceExtractionResult.empty(null);

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        assertThat(result.isVerified(FacilityInvestmentFactDefinition.AMOUNT))
                .isFalse();
        assertThat(result.skipped())
                .containsEntry(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        "Evidence 후보가 없습니다."
                );
    }

    @Test
    void 후보가_정확히_하나이면_VERIFIED로_승격한다() {
        DisclosureEvidence candidate = amountCandidate("5,296,200,000,000", "원");
        FacilityInvestmentEvidenceExtractionResult candidates =
                singleCandidateResult(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        candidate
                );

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        VerifiedFacilityEvidence verified = result
                .find(FacilityInvestmentFactDefinition.AMOUNT)
                .orElseThrow();
        assertThat(verified.evidence().status())
                .isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(verified.evidence().evidenceId())
                .isEqualTo(candidate.evidenceId());
        assertThat(verified.evidence().location())
                .isEqualTo(candidate.location());
        assertThat(verified.normalization().mapped()).isTrue();
    }

    @Test
    void 후보가_둘_이상이면_승격하지_않는다() {
        DisclosureEvidence first = amountCandidate("5,296,200,000,000", "원");
        DisclosureEvidence second = amountCandidate("1,000,000,000", "원");
        Map<FacilityInvestmentFactDefinition, List<DisclosureEvidence>> map =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        map.put(
                FacilityInvestmentFactDefinition.AMOUNT,
                List.of(first, second)
        );
        FacilityInvestmentEvidenceExtractionResult candidates =
                new FacilityInvestmentEvidenceExtractionResult(map, List.of());

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        assertThat(result.isVerified(FacilityInvestmentFactDefinition.AMOUNT))
                .isFalse();
        assertThat(result.skipped())
                .hasEntrySatisfying(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        reason -> assertThat(reason).contains("모호")
                );
    }

    @Test
    void rawValue가_비어있으면_승격하지_않는다() {
        DisclosureEvidence blank = tableCellEvidence(
                FacilityInvestmentFactDefinition.PURPOSE,
                "투자목적",
                null,
                null
        );
        FacilityInvestmentEvidenceExtractionResult candidates =
                singleCandidateResult(
                        FacilityInvestmentFactDefinition.PURPOSE,
                        blank
                );

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        assertThat(result.isVerified(FacilityInvestmentFactDefinition.PURPOSE))
                .isFalse();
        assertThat(result.skipped())
                .containsEntry(
                        FacilityInvestmentFactDefinition.PURPOSE,
                        "rawValue가 비어 있습니다."
                );
    }

    @Test
    void 정규화에_실패하면_승격하지_않는다() {
        DisclosureEvidence garbage = amountCandidate("약 오조원", "원");
        FacilityInvestmentEvidenceExtractionResult candidates =
                singleCandidateResult(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        garbage
                );

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        assertThat(result.isVerified(FacilityInvestmentFactDefinition.AMOUNT))
                .isFalse();
        assertThat(result.skipped())
                .hasEntrySatisfying(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        reason -> assertThat(reason).contains("정규화")
                );
    }

    @Test
    void DECIMAL_Fact의_원문_단위가_없으면_정규화에_성공해도_승격하지_않는다() {
        // 값 자체에 "백만원"이 포함돼 정규화(normalizer)는 성공하지만,
        // Evidence.rawUnit()이 비어 있으면 원문 단위가 불명확하므로 승격하지 않는다.
        DisclosureEvidence noUnit = amountCandidate("5,296,200백만원", null);
        FacilityInvestmentEvidenceExtractionResult candidates =
                singleCandidateResult(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        noUnit
                );

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        assertThat(result.isVerified(FacilityInvestmentFactDefinition.AMOUNT))
                .isFalse();
        assertThat(result.skipped())
                .containsEntry(
                        FacilityInvestmentFactDefinition.AMOUNT,
                        "단위가 명확하지 않습니다."
                );
    }

    @Test
    void 핵심_8개_후보가_모두_유일하면_모두_VERIFIED로_승격한다() {
        Map<FacilityInvestmentFactDefinition, List<DisclosureEvidence>> map =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        map.put(
                FacilityInvestmentFactDefinition.TARGET,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.TARGET,
                        "투자대상",
                        "청주 M15X 건설",
                        null
                ))
        );
        map.put(
                FacilityInvestmentFactDefinition.AMOUNT,
                List.of(amountCandidate("5,296,200,000,000", "원"))
        );
        map.put(
                FacilityInvestmentFactDefinition.EQUITY_AMOUNT,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.EQUITY_AMOUNT,
                        "자기자본",
                        "53,503,752,397,611",
                        "원"
                ))
        );
        map.put(
                FacilityInvestmentFactDefinition.EQUITY_RATIO,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.EQUITY_RATIO,
                        "자기자본대비",
                        "9.90",
                        "%"
                ))
        );
        map.put(
                FacilityInvestmentFactDefinition.PURPOSE,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.PURPOSE,
                        "투자목적",
                        "차세대 DRAM 생산능력 확장",
                        null
                ))
        );
        map.put(
                FacilityInvestmentFactDefinition.START_DATE,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.START_DATE,
                        "투자기간",
                        "2024-04-24",
                        null
                ))
        );
        map.put(
                FacilityInvestmentFactDefinition.END_DATE,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.END_DATE,
                        "투자기간",
                        "2026-10-30",
                        null
                ))
        );
        map.put(
                FacilityInvestmentFactDefinition.DECISION_DATE,
                List.of(tableCellEvidence(
                        FacilityInvestmentFactDefinition.DECISION_DATE,
                        "이사회결의일",
                        "2024-04-24",
                        null
                ))
        );

        FacilityInvestmentEvidenceExtractionResult candidates =
                new FacilityInvestmentEvidenceExtractionResult(map, List.of());

        FacilityInvestmentEvidenceVerificationResult result =
                verifier.verify(candidates);

        assertThat(result.hasAllCoreVerified()).isTrue();
        assertThat(result.missingCoreVerified()).isEmpty();
    }

    private FacilityInvestmentEvidenceExtractionResult singleCandidateResult(
            FacilityInvestmentFactDefinition definition,
            DisclosureEvidence candidate
    ) {
        Map<FacilityInvestmentFactDefinition, List<DisclosureEvidence>> map =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        map.put(definition, List.of(candidate));
        return new FacilityInvestmentEvidenceExtractionResult(map, List.of());
    }

    private DisclosureEvidence amountCandidate(String rawValue, String rawUnit) {
        return tableCellEvidence(
                FacilityInvestmentFactDefinition.AMOUNT,
                "투자금액",
                rawValue,
                rawUnit
        );
    }

    private DisclosureEvidence tableCellEvidence(
            FacilityInvestmentFactDefinition definition,
            String rowLabel,
            String rawValue,
            String rawUnit
    ) {
        return new DisclosureEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                RECEIPT_NO,
                "20240424800596.xml",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                UUID.randomUUID(),
                "신규시설투자등 > 투자내역",
                UUID.randomUUID(),
                EvidenceBlockType.TABLE_CELL,
                "table-1",
                new DisclosureEvidenceLocation(10, 10, null, 2, 1),
                new DisclosureEvidenceValue(
                        rowLabel + " | " + rawValue,
                        rowLabel,
                        null,
                        rawValue,
                        rawUnit,
                        null
                ),
                EvidenceStatus.CANDIDATE
        );
    }
}
