package com.foliolens.backend.question.plan.toolinput;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 앞선 SEARCH_DISCLOSURES 결과 안에서 근거 청크를 검색하기 위한 계획 입력.
 * 실제 공시 UUID 목록은 계획에 직접 넣지 않고 실행 시 disclosureIdsFrom이
 * 가리키는 step 결과에서 가져온다.
 */
public record SearchEvidenceInput(
        String disclosureIdsFrom,
        List<String> concepts,
        List<String> factKeys,
        List<String> sectionHints,
        List<String> keywords,
        List<String> blockTypes,
        int topK
) implements ToolInput {

    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 20;

    private static final Set<String> ALLOWED_BLOCK_TYPES = Set.of(
            "TEXT",
            "TITLE",
            "HEADING",
            "SECTION",
            "PARAGRAPH",
            "TABLE",
            "TABLE_ROW",
            "TABLE_CELL"
    );

    public SearchEvidenceInput {
        disclosureIdsFrom = requireText(
                disclosureIdsFrom,
                "disclosureIdsFrom"
        );
        concepts = immutableTextList(concepts, "concepts");
        factKeys = immutableTextList(factKeys, "factKeys");
        sectionHints = immutableTextList(sectionHints, "sectionHints");
        keywords = immutableTextList(keywords, "keywords");
        blockTypes = normalizeBlockTypes(blockTypes);

        if (concepts.isEmpty()
                && factKeys.isEmpty()
                && sectionHints.isEmpty()
                && keywords.isEmpty()) {
            throw new IllegalArgumentException(
                    "concepts, factKeys, sectionHints, keywords 중 "
                            + "하나 이상의 검색 신호가 필요합니다."
            );
        }

        if (topK < MIN_TOP_K || topK > MAX_TOP_K) {
            throw new IllegalArgumentException(
                    "topK는 " + MIN_TOP_K + " 이상 "
                            + MAX_TOP_K + " 이하여야 합니다."
            );
        }
    }

    private static List<String> normalizeBlockTypes(List<String> values) {
        List<String> normalized = immutableTextList(values, "blockTypes")
                .stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<String> unsupported = normalized.stream()
                .filter(value -> !ALLOWED_BLOCK_TYPES.contains(value))
                .toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "지원하지 않는 blockTypes입니다: " + unsupported
            );
        }

        return normalized;
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }

        return value.strip();
    }
}
