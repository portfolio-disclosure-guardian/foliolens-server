package com.foliolens.backend.disclosure.domain.fact.facility.verification;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FacilityInvestmentValueNormalizer;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FactValueNormalizationResult;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fact 정의별 CANDIDATE Evidence가 정확히 하나이고, 자료형·단위가 명확할
 * 때만 VERIFIED로 승격한다.
 *
 * 후보가 없거나 둘 이상이면 승격하지 않고, 값이 비어 있거나 정규화에
 * 실패·모호한 경우에도 승격하지 않는다. 승격된 Evidence는 원본과 같은
 * {@code evidenceId}와 원문 위치를 유지한다.
 */
public class FacilityInvestmentEvidenceVerifier {

    private final FacilityInvestmentValueNormalizer normalizer;

    public FacilityInvestmentEvidenceVerifier(
            FacilityInvestmentValueNormalizer normalizer
    ) {
        this.normalizer = Objects.requireNonNull(
                normalizer,
                "normalizer는 필수입니다."
        );
    }

    public FacilityInvestmentEvidenceVerificationResult verify(
            FacilityInvestmentEvidenceExtractionResult candidates
    ) {
        Objects.requireNonNull(candidates, "candidates는 필수입니다.");

        Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence>
                verified = new EnumMap<>(FacilityInvestmentFactDefinition.class);
        Map<FacilityInvestmentFactDefinition, String> skipped =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);

        for (FacilityInvestmentFactDefinition definition
                : FacilityInvestmentFactDefinition.values()) {
            verifyOne(definition, candidates, verified, skipped);
        }

        return new FacilityInvestmentEvidenceVerificationResult(
                verified,
                skipped
        );
    }

    private void verifyOne(
            FacilityInvestmentFactDefinition definition,
            FacilityInvestmentEvidenceExtractionResult candidates,
            Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence> verified,
            Map<FacilityInvestmentFactDefinition, String> skipped
    ) {
        List<DisclosureEvidence> candidateList =
                candidates.candidatesFor(definition);

        if (candidateList.isEmpty()) {
            skipped.put(definition, "Evidence 후보가 없습니다.");
            return;
        }
        if (candidateList.size() > 1) {
            skipped.put(
                    definition,
                    "Evidence 후보가 " + candidateList.size() + "개로 모호합니다."
            );
            return;
        }

        DisclosureEvidence candidate = candidateList.get(0);

        String locationIssue = checkLocation(candidate);
        if (locationIssue != null) {
            skipped.put(definition, locationIssue);
            return;
        }
        if (candidate.value().rawValue() == null
                || candidate.value().rawValue().isBlank()) {
            skipped.put(definition, "rawValue가 비어 있습니다.");
            return;
        }

        FactValueNormalizationResult normalization =
                normalizer.normalize(definition, candidate.value());
        if (!normalization.mapped()) {
            skipped.put(
                    definition,
                    "정규화되지 않았습니다: " + normalization.detail()
            );
            return;
        }
        if (normalization.valueType() != definition.valueType()) {
            skipped.put(definition, "정규화 결과 타입이 Fact 정의와 다릅니다.");
            return;
        }
        if (definition.valueType() == FactValueType.DECIMAL
                && (candidate.value().rawUnit() == null
                || normalization.normalizedUnit() == null)) {
            skipped.put(definition, "단위가 명확하지 않습니다.");
            return;
        }

        DisclosureEvidence verifiedEvidence = promote(candidate);
        verified.put(
                definition,
                new VerifiedFacilityEvidence(
                        definition,
                        verifiedEvidence,
                        normalization
                )
        );
    }

    private String checkLocation(DisclosureEvidence evidence) {
        if (evidence.status() != EvidenceStatus.CANDIDATE) {
            return "CANDIDATE 상태의 Evidence만 검증할 수 있습니다: "
                    + evidence.status();
        }
        if (evidence.disclosureId() == null
                || evidence.disclosureDocumentId() == null) {
            return "disclosureId 또는 disclosureDocumentId가 없습니다.";
        }
        if (evidence.contentBlockId() == null) {
            return "contentBlockId가 없습니다.";
        }
        if (evidence.blockType() != EvidenceBlockType.TABLE_CELL) {
            return "TABLE_CELL Evidence만 검증할 수 있습니다.";
        }
        if (!evidence.location().hasTableLocation()
                || evidence.location().tableCellIndex() == null) {
            return "표 행·셀 위치가 없습니다.";
        }
        return null;
    }

    private DisclosureEvidence promote(DisclosureEvidence candidate) {
        return new DisclosureEvidence(
                candidate.evidenceId(),
                candidate.disclosureId(),
                candidate.disclosureDocumentId(),
                candidate.receiptNo(),
                candidate.documentName(),
                candidate.documentFileRole(),
                candidate.eventDocumentRole(),
                candidate.sectionId(),
                candidate.sectionPath(),
                candidate.contentBlockId(),
                candidate.blockType(),
                candidate.tableIndexOrName(),
                candidate.location(),
                candidate.value(),
                EvidenceStatus.VERIFIED
        );
    }
}
