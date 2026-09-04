package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactPersistenceResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 한 시설투자 문서의 Evidence 검증·Fact 생성·저장 결과. */
public record FacilityInvestmentFactIngestionResult(
        UUID disclosureId,
        UUID disclosureDocumentId,
        String receiptNo,
        int candidateEvidenceCount,
        int verifiedEvidenceCount,
        int generatedFactCount,
        Set<FacilityInvestmentFactDefinition> missingCoreDefinitions,
        Map<FacilityInvestmentFactDefinition, String> skippedDefinitions,
        List<String> extractionWarnings,
        DisclosureFactPersistenceResult persistenceResult
) {

    public FacilityInvestmentFactIngestionResult {
        disclosureId = Objects.requireNonNull(
                disclosureId,
                "disclosureId는 필수입니다."
        );
        disclosureDocumentId = Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        if (receiptNo == null || !receiptNo.matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "receiptNo는 14자리 숫자 문자열이어야 합니다."
            );
        }
        if (candidateEvidenceCount < 0
                || verifiedEvidenceCount < 0
                || generatedFactCount < 0) {
            throw new IllegalArgumentException("적재 결과 건수는 음수일 수 없습니다.");
        }
        missingCoreDefinitions = Set.copyOf(
                Objects.requireNonNull(
                        missingCoreDefinitions,
                        "missingCoreDefinitions는 필수입니다."
                )
        );
        skippedDefinitions = Map.copyOf(
                Objects.requireNonNull(
                        skippedDefinitions,
                        "skippedDefinitions는 필수입니다."
                )
        );
        extractionWarnings = List.copyOf(
                Objects.requireNonNull(
                        extractionWarnings,
                        "extractionWarnings는 필수입니다."
                )
        );
        persistenceResult = Objects.requireNonNull(
                persistenceResult,
                "persistenceResult는 필수입니다."
        );
    }

    public boolean hasAllCoreFacts() {
        return missingCoreDefinitions.isEmpty();
    }
}
