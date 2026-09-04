package com.foliolens.backend.disclosure.domain.fact.facility.verification;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FactValueNormalizationResult;

import java.util.Objects;

/**
 * 유일한 CANDIDATE Evidence를 검증해 VERIFIED로 승격한 결과.
 *
 * 원본 Evidence와 같은 {@code evidenceId}와 원문 위치를 그대로 유지한다.
 */
public record VerifiedFacilityEvidence(
        FacilityInvestmentFactDefinition definition,
        DisclosureEvidence evidence,
        FactValueNormalizationResult normalization
) {

    public VerifiedFacilityEvidence {
        definition = Objects.requireNonNull(
                definition,
                "definition은 필수입니다."
        );
        evidence = Objects.requireNonNull(evidence, "evidence는 필수입니다.");
        normalization = Objects.requireNonNull(
                normalization,
                "normalization은 필수입니다."
        );

        if (evidence.status() != EvidenceStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    "VerifiedFacilityEvidence의 evidence는 VERIFIED 상태여야 합니다."
            );
        }
        if (normalization.normalizationStatus()
                != FactNormalizationStatus.MAPPED) {
            throw new IllegalArgumentException(
                    "VerifiedFacilityEvidence의 normalization은 MAPPED 상태여야 합니다."
            );
        }
        if (normalization.valueType() != definition.valueType()) {
            throw new IllegalArgumentException(
                    "normalization의 타입이 시설투자 Fact 정의와 다릅니다."
            );
        }
    }
}
