package com.foliolens.backend.retrieval;

import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;

import java.time.LocalDate;
import java.util.List;

// currency/accountingBasis 등 시설투자 fact에 미적용되는 필드는 필요해지면 추가한다.
public record RetrievedFact(
        String factId,
        String disclosureId,
        String factKey,
        FactValueType valueType,
        String rawValue,
        String normalizedValue,
        String unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<String> evidenceIds,
        FactValidationStatus validationStatus) {
}
