package com.foliolens.backend.disclosure.infrastructure.search;

import java.util.List;
import java.util.Set;

/**
 * concept·factKey를 실제 공시 원문에서 찾을 문자열로 해석한 결과.
 */
public record ResolvedChunkSearchTerms(
        List<String> keywords,
        List<String> sectionHints,
        Set<String> factHintTerms,
        Set<String> resolvedConcepts,
        Set<String> resolvedFactKeys,
        Set<String> unresolvedConcepts,
        Set<String> unresolvedFactKeys,
        List<String> warnings
) {

    public ResolvedChunkSearchTerms {
        keywords = List.copyOf(keywords);
        sectionHints = List.copyOf(sectionHints);
        factHintTerms = Set.copyOf(factHintTerms);
        resolvedConcepts = Set.copyOf(resolvedConcepts);
        resolvedFactKeys = Set.copyOf(resolvedFactKeys);
        unresolvedConcepts = Set.copyOf(unresolvedConcepts);
        unresolvedFactKeys = Set.copyOf(unresolvedFactKeys);
        warnings = List.copyOf(warnings);
    }

    public boolean hasExecutableTerms() {
        return !keywords.isEmpty() || !sectionHints.isEmpty();
    }
}
