package com.foliolens.backend.disclosure.infrastructure.search;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 선택된 공시 집합에서 수행한 청크 검색 결과 묶음.
 */
public record DisclosureChunkSearchResult(
        List<DisclosureChunkSearchHit> items,
        Set<UUID> searchedDisclosureIds,
        int searchedDocumentCount,
        int candidateChunkCount, // topK 적용 전 조건을 만족한 청크 수
        boolean truncated, // topK로 일부 후보가 제외됐는지 여부
        List<String> warnings,
        String retrievalVersion
) {

    public DisclosureChunkSearchResult {
        items = immutableItems(items);
        searchedDisclosureIds = immutableIds(
                searchedDisclosureIds,
                "searchedDisclosureIds"
        );
        warnings = immutableTextList(warnings, "warnings");
        retrievalVersion = requireText(
                retrievalVersion,
                "retrievalVersion"
        );

        if (searchedDisclosureIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "searchedDisclosureIds는 하나 이상이어야 합니다."
            );
        }

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

        boolean shouldBeTruncated = candidateChunkCount > items.size();
        if (truncated != shouldBeTruncated) {
            throw new IllegalArgumentException(
                    "truncated는 candidateChunkCount와 items 수의 차이와 "
                            + "일치해야 합니다."
            );
        }

        validateItems(
                items,
                searchedDisclosureIds,
                searchedDocumentCount,
                retrievalVersion
        );
    }

    public static DisclosureChunkSearchResult empty(
            Set<UUID> searchedDisclosureIds,
            int searchedDocumentCount,
            List<String> warnings,
            String retrievalVersion
    ) {
        return new DisclosureChunkSearchResult(
                List.of(),
                searchedDisclosureIds,
                searchedDocumentCount,
                0,
                false,
                warnings,
                retrievalVersion
        );
    }

    private static void validateItems(
            List<DisclosureChunkSearchHit> items,
            Set<UUID> searchedDisclosureIds,
            int searchedDocumentCount,
            String retrievalVersion
    ) {
        Set<UUID> chunkIds = new HashSet<>();
        Set<UUID> documentIds = new HashSet<>();

        for (DisclosureChunkSearchHit item : items) {
            if (!searchedDisclosureIds.contains(item.disclosureId())) {
                throw new IllegalArgumentException(
                        "검색 대상 공시에 속하지 않은 청크가 포함됐습니다."
                );
            }

            if (!retrievalVersion.equals(item.retrievalVersion())) {
                throw new IllegalArgumentException(
                        "청크와 결과의 retrievalVersion이 다릅니다."
                );
            }

            if (!chunkIds.add(item.chunkId())) {
                throw new IllegalArgumentException(
                        "items에 중복된 chunkId가 있습니다."
                );
            }

            documentIds.add(item.disclosureDocumentId());
        }

        if (searchedDocumentCount < documentIds.size()) {
            throw new IllegalArgumentException(
                    "searchedDocumentCount가 결과에 포함된 문서 수보다 작습니다."
            );
        }
    }

    private static List<DisclosureChunkSearchHit> immutableItems(
            List<DisclosureChunkSearchHit> values
    ) {
        if (values == null) {
            return List.of();
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "items에는 null 원소가 포함될 수 없습니다."
            );
        }

        return List.copyOf(values);
    }

    private static Set<UUID> immutableIds(
            Set<UUID> values,
            String fieldName
    ) {
        if (values == null) {
            return Set.of();
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    fieldName + "에는 null 원소가 포함될 수 없습니다."
            );
        }

        return Set.copyOf(values);
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
                .distinct()
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
