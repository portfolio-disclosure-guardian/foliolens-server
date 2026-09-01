package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.ResolvedChunkSearchTerms;

import java.util.List;

/**
 * 선택된 공시 안에서 검색 청크와 원본 위치 후보를 찾는 Repository 계약.
 */
public interface DisclosureChunkSearchRepository {

    SearchResult search(
            DisclosureChunkSearchCondition condition,
            ResolvedChunkSearchTerms terms,
            String retrievalVersion
    );

    record SearchResult(
            List<DisclosureChunkSearchHit> items,
            int searchedDocumentCount,
            int candidateChunkCount,
            List<String> warnings
    ) {

        public SearchResult {
            items = items == null ? List.of() : List.copyOf(items);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);

            if (searchedDocumentCount < 0) {
                throw new IllegalArgumentException(
                        "searchedDocumentCount는 0 이상이어야 합니다."
                );
            }

            if (candidateChunkCount < items.size()) {
                throw new IllegalArgumentException(
                        "candidateChunkCount는 items 수보다 작을 수 없습니다."
                );
            }
        }
    }
}
