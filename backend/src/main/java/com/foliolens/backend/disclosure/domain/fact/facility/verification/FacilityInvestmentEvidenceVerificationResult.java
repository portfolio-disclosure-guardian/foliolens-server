package com.foliolens.backend.disclosure.domain.fact.facility.verification;

import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 시설투자 Fact 정의별 Evidence 검증 결과.
 *
 * VERIFIED로 승격한 항목과, 승격하지 않은 이유를 함께 표현해 핵심 Fact
 * 누락을 확인할 수 있게 한다.
 */
public record FacilityInvestmentEvidenceVerificationResult(
        Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence> verified,
        Map<FacilityInvestmentFactDefinition, String> skipped
) {

    public FacilityInvestmentEvidenceVerificationResult {
        verified = immutableVerified(verified);
        skipped = immutableSkipped(skipped, verified);
    }

    public Optional<VerifiedFacilityEvidence> find(
            FacilityInvestmentFactDefinition definition
    ) {
        return Optional.ofNullable(verified.get(definition));
    }

    public boolean isVerified(FacilityInvestmentFactDefinition definition) {
        return verified.containsKey(definition);
    }

    public Set<FacilityInvestmentFactDefinition> missingCoreVerified() {
        EnumSet<FacilityInvestmentFactDefinition> missing = EnumSet.copyOf(
                FacilityInvestmentFactDefinition.coreDefinitions()
        );
        missing.removeAll(verified.keySet());
        return Set.copyOf(missing);
    }

    public boolean hasAllCoreVerified() {
        return missingCoreVerified().isEmpty();
    }

    private static Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence>
    immutableVerified(
            Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        EnumMap<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence>
                copied = new EnumMap<>(FacilityInvestmentFactDefinition.class);
        values.forEach((definition, evidence) -> {
            Objects.requireNonNull(definition, "Fact 정의는 null일 수 없습니다.");
            Objects.requireNonNull(
                    evidence,
                    "검증된 Evidence는 null일 수 없습니다."
            );
            if (definition != evidence.definition()) {
                throw new IllegalArgumentException(
                        "Map의 key와 VerifiedFacilityEvidence의 정의가 다릅니다."
                );
            }
            copied.put(definition, evidence);
        });
        return Map.copyOf(copied);
    }

    private static Map<FacilityInvestmentFactDefinition, String>
    immutableSkipped(
            Map<FacilityInvestmentFactDefinition, String> values,
            Map<FacilityInvestmentFactDefinition, VerifiedFacilityEvidence> verified
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        EnumMap<FacilityInvestmentFactDefinition, String> copied =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        values.forEach((definition, reason) -> {
            Objects.requireNonNull(definition, "Fact 정의는 null일 수 없습니다.");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "건너뛴 사유는 비어 있을 수 없습니다."
                );
            }
            if (verified.containsKey(definition)) {
                throw new IllegalArgumentException(
                        "같은 정의가 검증됨과 건너뜀에 동시에 있을 수 없습니다."
                );
            }
            copied.put(definition, reason.strip());
        });
        return Map.copyOf(copied);
    }
}
