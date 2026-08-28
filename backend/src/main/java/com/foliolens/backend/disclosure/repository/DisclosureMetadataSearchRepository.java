package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchHit;

import java.util.List;

/**
 * 기업·기간·공시 유형·보고서명으로 공시 후보를 제한하는 검색 전용 Repository 계약
 */
public interface DisclosureMetadataSearchRepository {

    SearchResult search(DisclosureMetadataSearchCondition condition);

    /**
     * limit이 적용된 결과와 limit 적용 전 전체 후보 수를 함께 반환한다.
     */
    record SearchResult(
            List<DisclosureMetadataSearchHit> items,
            int candidateCount
    ) {

        public SearchResult {
            items = items == null ? List.of() : List.copyOf(items);

            if (items.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "items에는 null 원소가 포함될 수 없습니다."
                );
            }

            if (candidateCount < items.size()) {
                throw new IllegalArgumentException(
                        "candidateCount는 items 수보다 작을 수 없습니다."
                );
            }
        }
    }
}
