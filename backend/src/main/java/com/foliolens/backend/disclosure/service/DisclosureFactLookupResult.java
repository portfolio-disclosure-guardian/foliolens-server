package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;

import java.util.List;

/**
 * LOOKUP_FACTS가 반환하는 검증 완료 Fact와 그 원문 Evidence 묶음.
 */
public record DisclosureFactLookupResult(
        List<DisclosureFact> facts,
        List<DisclosureEvidence> evidences,
        List<String> missingFactKeys
) {

    public DisclosureFactLookupResult {
        facts = facts == null ? List.of() : List.copyOf(facts);
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
        missingFactKeys = missingFactKeys == null
                ? List.of()
                : List.copyOf(missingFactKeys);
    }
}
