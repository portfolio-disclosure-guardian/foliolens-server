package com.foliolens.backend.disclosure.domain.fact.facility;

import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 동일한 신규시설투자 공시·문서에서 추출된 핵심 Fact 묶음.
 * 일부 Fact가 누락된 PARTIAL 결과도 표현할 수 있다.
 */
public record FacilityInvestmentFactSet(
        UUID disclosureId,
        UUID disclosureDocumentId,
        String receiptNo,
        Map<FacilityInvestmentFactDefinition, DisclosureFact> facts
) {

    public FacilityInvestmentFactSet {
        disclosureId = Objects.requireNonNull(
                disclosureId,
                "disclosureId는 필수입니다."
        );
        disclosureDocumentId = Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        receiptNo = requireReceiptNo(receiptNo);
        facts = immutableFacts(
                facts,
                disclosureId,
                disclosureDocumentId,
                receiptNo
        );
    }

    public Optional<DisclosureFact> find(
            FacilityInvestmentFactDefinition definition
    ) {
        return Optional.ofNullable(facts.get(definition));
    }

    public Set<FacilityInvestmentFactDefinition> missingDefinitions() {
        EnumSet<FacilityInvestmentFactDefinition> missing =
                EnumSet.allOf(FacilityInvestmentFactDefinition.class);
        missing.removeAll(facts.keySet());
        return Set.copyOf(missing);
    }

    public Set<FacilityInvestmentFactDefinition> missingCoreDefinitions() {
        EnumSet<FacilityInvestmentFactDefinition> missing =
                EnumSet.copyOf(
                        FacilityInvestmentFactDefinition.coreDefinitions()
                );
        missing.removeAll(facts.keySet());
        return Set.copyOf(missing);
    }

    public boolean hasAllCoreFacts() {
        return missingCoreDefinitions().isEmpty();
    }

    public boolean hasAllDefinedFacts() {
        return facts.size() == FacilityInvestmentFactDefinition.values().length;
    }

    private static Map<FacilityInvestmentFactDefinition, DisclosureFact>
    immutableFacts(
            Map<FacilityInvestmentFactDefinition, DisclosureFact> values,
            UUID disclosureId,
            UUID disclosureDocumentId,
            String receiptNo
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        EnumMap<FacilityInvestmentFactDefinition, DisclosureFact> copied =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        values.forEach((definition, fact) -> {
            Objects.requireNonNull(
                    definition,
                    "시설투자 Fact 정의는 null일 수 없습니다."
            );
            Objects.requireNonNull(fact, "시설투자 Fact는 null일 수 없습니다.");
            validateFact(
                    definition,
                    fact,
                    disclosureId,
                    disclosureDocumentId,
                    receiptNo
            );
            copied.put(definition, fact);
        });
        return Map.copyOf(copied);
    }

    private static void validateFact(
            FacilityInvestmentFactDefinition definition,
            DisclosureFact fact,
            UUID disclosureId,
            UUID disclosureDocumentId,
            String receiptNo
    ) {
        if (!definition.factKey().equals(fact.factKey())) {
            throw new IllegalArgumentException(
                    "시설투자 Fact 정의와 factKey가 다릅니다."
            );
        }
        if (definition.valueType() != fact.valueType()) {
            throw new IllegalArgumentException(
                    "시설투자 Fact 정의와 valueType이 다릅니다."
            );
        }
        if (!disclosureId.equals(fact.disclosureId())
                || !disclosureDocumentId.equals(
                fact.disclosureDocumentId()
        )) {
            throw new IllegalArgumentException(
                    "시설투자 Fact 묶음과 Fact의 공시·문서가 다릅니다."
            );
        }
        if (!receiptNo.equals(fact.sourceReceiptNo())) {
            throw new IllegalArgumentException(
                    "시설투자 Fact 묶음과 Fact의 접수번호가 다릅니다."
            );
        }
        if (definition.normalizedUnit() != null
                && fact.normalizedValue() != null
                && !definition.normalizedUnit().equals(
                fact.normalizedUnit()
        )) {
            throw new IllegalArgumentException(
                    "시설투자 Fact의 표준 단위가 정의와 다릅니다."
            );
        }
    }

    private static String requireReceiptNo(String value) {
        if (value == null || !value.matches("^[0-9]{14}$")) {
            throw new IllegalArgumentException(
                    "receiptNo는 14자리 숫자 문자열이어야 합니다."
            );
        }
        return value;
    }
}
