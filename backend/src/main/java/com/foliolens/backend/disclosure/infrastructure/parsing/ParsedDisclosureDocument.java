package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.List;

public record ParsedDisclosureDocument(
        String fileName,
        String documentName,
        List<ParsedDisclosureBlock> preambleBlocks, // section에 포함되지 않는 문서 앞부분
        List<ParsedDisclosureSection> sections // 문서의 최상위 섹션 목록
) {

    public ParsedDisclosureDocument {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "fileName은 필수입니다."
            );
        }

        fileName = fileName.trim();
        documentName = normalizeNullable(documentName);
        preambleBlocks = List.copyOf(preambleBlocks);
        sections = List.copyOf(sections);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
