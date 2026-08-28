package com.foliolens.backend.disclosure.infrastructure.search;

import java.util.List;

/**
 * 순위가 적용된 공시 메타데이터 검색 결과 묶음.
 *
 * 검색 결과가 없는 경우도 예외가 아니라 빈 items로 표현한다.
 *
 * 예:
 * {
 *   "items": [
 *     {
 *       "disclosureId": "공시 UUID",
 *       "companyId": "기업 UUID",
 *       "companyName": "삼성전자",
 *       "stockCode": "005930",
 *       "receiptNo": "20250410800123",
 *       "receiptDate": "2025-04-10",
 *       "reportName": "신규시설투자등",
 *       "sourceGroup": "MAJOR",
 *       "category": "MATERIAL",
 *       "rawSubtype": "신규시설투자등",
 *       "correction": false,
 *       "sourceProvider": "CONTEST",
 *       "documentCount": 2,
 *       "searchScore": 0.95,
 *       "matchedTerms": ["시설투자"]
 *     }
 *   ],
 *   "candidateCount": 12,
 *   "truncated": true,
 *   "warnings": [],
 *   "retrievalVersion": "metadata-search-v1"
 * }
 */
public record DisclosureMetadataSearchResult(
        List<DisclosureMetadataSearchHit> items,
        int candidateCount,
        boolean truncated, // limit 때문에 일부 결과가 잘렸는지 여부
        List<String> warnings, // 검색 조건과 결과에 대한 경고
        String retrievalVersion
) {

    public DisclosureMetadataSearchResult {
        items = immutableItemList(items);
        warnings = immutableTextList(warnings, "warnings");
        retrievalVersion = requireText(
                retrievalVersion,
                "retrievalVersion"
        );

        if (candidateCount < 0) {
            throw new IllegalArgumentException(
                    "candidateCount는 0 이상이어야 합니다."
            );
        }

        if (candidateCount < items.size()) {
            throw new IllegalArgumentException(
                    "candidateCount는 반환된 items 수보다 작을 수 없습니다."
            );
        }

        boolean shouldBeTruncated = candidateCount > items.size();
        if (truncated != shouldBeTruncated) {
            throw new IllegalArgumentException(
                    "truncated는 candidateCount와 items 수의 차이와 일치해야 합니다."
            );
        }
    }

    public static DisclosureMetadataSearchResult empty(
            String retrievalVersion
    ) {
        return new DisclosureMetadataSearchResult(
                List.of(),
                0,
                false,
                List.of(),
                retrievalVersion
        );
    }

    private static List<DisclosureMetadataSearchHit> immutableItemList(
            List<DisclosureMetadataSearchHit> values
    ) {
        if (values == null) {
            return List.of();
        }

        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "items에는 null 원소가 포함될 수 없습니다."
            );
        }

        return List.copyOf(values);
    }

    private static List<String> immutableTextList(
            List<String> values,
            String fieldName
    ) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(value -> requireText(value, fieldName))
                .toList();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }

        return value.strip();
    }
}
