package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.List;

public record ParsedDisclosureSection(
        int level,
        int order,  // XML 안에서 등장한 순서 -> 나중에 DB에서 원문 순서를 복원하는 데 사용
        String title,
        int sourceLineStart,
        int sourceLineEnd,
        List<ParsedDisclosureBlock> blocks, // 해당 section에 포함된 블럭들
        List<ParsedDisclosureSection> children // children으로 장·절의 상하관계를 표현
        /*
            SECTION-1
                └─ SECTION-2
                    └─ SECTION-3
         */
) {

    public ParsedDisclosureSection {
        if (level < 1) {
            throw new IllegalArgumentException(
                    "section level은 1 이상이어야 합니다."
            );
        }

        title = normalizeNullable(title);
        blocks = List.copyOf(blocks);
        children = List.copyOf(children);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
