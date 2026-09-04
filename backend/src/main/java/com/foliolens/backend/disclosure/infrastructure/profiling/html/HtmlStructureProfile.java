package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 실제 콘텐츠가 HTML인 공시 원문 한 파일의 구조 조사 결과.
 */
public record HtmlStructureProfile(
        String fileName,
        String rootElementName,
        String title,
        String decodedCharset,
        String declaredCharset,
        long fileSizeBytes,
        int maxDepth,
        Map<String, Long> tagCounts,
        Map<String, Long> classCounts,
        long xformsContainerCount,
        long xformsTitleCount,
        long xformsInputCount,
        long tableCount,
        long topLevelTableCount,
        long nestedTableCount,
        int maxTableDepth,
        int maxRowsPerTable,
        int maxCellsPerRow,
        long rowSpanCellCount,
        int maxRowSpan,
        long colSpanCellCount,
        int maxColSpan,
        long invalidSpanAttributeCount,
        long lineBreakCount,
        long anchorCount,
        long anchorWithHrefCount,
        long imageCount,
        long styleCount,
        long scriptCount,
        long commentCount,
        long parserErrorCount,
        String firstParserError
) {

    public HtmlStructureProfile {
        fileName = requireText(fileName, "fileName");
        rootElementName = requireText(rootElementName, "rootElementName");
        title = normalizeOptional(title);
        decodedCharset = requireText(decodedCharset, "decodedCharset");
        declaredCharset = normalizeOptional(declaredCharset);
        tagCounts = immutableCounts(tagCounts, "tagCounts");
        classCounts = immutableCounts(classCounts, "classCounts");
        firstParserError = normalizeOptional(firstParserError);

        validateNonNegative(fileSizeBytes, "fileSizeBytes");
        validateNonNegative(maxDepth, "maxDepth");
        validateNonNegative(xformsContainerCount, "xformsContainerCount");
        validateNonNegative(xformsTitleCount, "xformsTitleCount");
        validateNonNegative(xformsInputCount, "xformsInputCount");
        validateNonNegative(tableCount, "tableCount");
        validateNonNegative(topLevelTableCount, "topLevelTableCount");
        validateNonNegative(nestedTableCount, "nestedTableCount");
        validateNonNegative(maxTableDepth, "maxTableDepth");
        validateNonNegative(maxRowsPerTable, "maxRowsPerTable");
        validateNonNegative(maxCellsPerRow, "maxCellsPerRow");
        validateNonNegative(rowSpanCellCount, "rowSpanCellCount");
        validateNonNegative(maxRowSpan, "maxRowSpan");
        validateNonNegative(colSpanCellCount, "colSpanCellCount");
        validateNonNegative(maxColSpan, "maxColSpan");
        validateNonNegative(
                invalidSpanAttributeCount,
                "invalidSpanAttributeCount"
        );
        validateNonNegative(lineBreakCount, "lineBreakCount");
        validateNonNegative(anchorCount, "anchorCount");
        validateNonNegative(anchorWithHrefCount, "anchorWithHrefCount");
        validateNonNegative(imageCount, "imageCount");
        validateNonNegative(styleCount, "styleCount");
        validateNonNegative(scriptCount, "scriptCount");
        validateNonNegative(commentCount, "commentCount");
        validateNonNegative(parserErrorCount, "parserErrorCount");

        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth는 1 이상이어야 합니다.");
        }
        if (parserErrorCount == 0 && firstParserError != null) {
            throw new IllegalArgumentException(
                    "parserError가 없으면 firstParserError도 없어야 합니다."
            );
        }
        if (parserErrorCount > 0 && firstParserError == null) {
            throw new IllegalArgumentException(
                    "parserError가 있으면 firstParserError가 필요합니다."
            );
        }
    }

    public long countOf(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return 0;
        }
        return tagCounts.getOrDefault(
                tagName.strip().toUpperCase(Locale.ROOT),
                0L
        );
    }

    private static Map<String, Long> immutableCounts(
            Map<String, Long> values,
            String fieldName
    ) {
        Objects.requireNonNull(values, fieldName + "는 필수입니다.");
        if (values.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null
                        || entry.getKey().isBlank()
                        || entry.getValue() == null
                        || entry.getValue() < 0
        )) {
            throw new IllegalArgumentException(
                    fieldName + "에 올바르지 않은 항목이 있습니다."
            );
        }
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "은 0 이상이어야 합니다."
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\s+", " ").strip();
    }
}
