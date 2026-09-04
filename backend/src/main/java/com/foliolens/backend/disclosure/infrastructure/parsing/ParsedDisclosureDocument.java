package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.util.List;

public record ParsedDisclosureDocument(
        String fileName,
        String documentName,
        List<ParsedDisclosureBlock> preambleBlocks, // section에 포함되지 않는 문서 앞부분
        List<ParsedDisclosureSection> sections, // 문서의 최상위 섹션 목록
        List<ParsedDisclosureLink> relatedLinks, // 원문에 기재된 공시 링크. 관계 확정은 별도 단계
        com.foliolens.backend.disclosure.infrastructure.parsing.pdf.PdfTextExtractionReport pdfTextReport
        List<ParsedDisclosureLink> relatedLinks // 원문에 기재된 공시 링크. 관계 확정은 별도 단계
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
        relatedLinks = List.copyOf(relatedLinks);
    }

    public ParsedDisclosureDocument(String fileName, String documentName,
                                    List<ParsedDisclosureBlock> preambleBlocks,
                                    List<ParsedDisclosureSection> sections, List<ParsedDisclosureLink> relatedLinks) {
        this(fileName, documentName, preambleBlocks, sections, relatedLinks, null);
    }

    public ParsedDisclosureDocument(String fileName, String documentName,
                                    List<ParsedDisclosureBlock> preambleBlocks,
                                    List<ParsedDisclosureSection> sections) {
        this(fileName, documentName, preambleBlocks, sections, List.of());
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
