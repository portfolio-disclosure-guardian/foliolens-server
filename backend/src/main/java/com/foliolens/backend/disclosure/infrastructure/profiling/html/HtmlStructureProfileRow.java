package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTML 구조 배치 조사 결과의 CSV 한 행.
 */
public record HtmlStructureProfileRow(
        UUID disclosureDocumentId,
        String sourceDocId,
        String receiptNo,
        String sourceGroup,
        String rawSubtype,
        String reportName,
        boolean correction,
        String fileName,
        String documentRole,
        String contentFormat,
        long fileSizeBytes,
        String relativePath,
        String rootElementName,
        String documentName,
        String htmlTitle,
        String decodedCharset,
        String declaredCharset,
        int maxDepth,
        int distinctTagCount,
        long totalElementCount,
        String tagCountsSummary,
        String classCountsSummary,
        long xformsContainerCount,
        long xformsTitleCount,
        long xformsInputCount,
        long tableCount,
        long topLevelTableCount,
        long nestedTableCount,
        int maxTableDepth,
        long tableRowCount,
        long tableHeaderCount,
        long tableCellCount,
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
        String firstParserError,
        long elapsedMillis,
        HtmlStructureProfileStatus status,
        String errorType,
        String errorMessage
) {

    public HtmlStructureProfileRow {
        disclosureDocumentId = Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );
        sourceDocId = requireText(sourceDocId, "sourceDocId");
        receiptNo = requireText(receiptNo, "receiptNo");
        sourceGroup = requireText(sourceGroup, "sourceGroup");
        rawSubtype = normalizeNullable(rawSubtype);
        reportName = requireText(reportName, "reportName");
        fileName = requireText(fileName, "fileName");
        documentRole = requireText(documentRole, "documentRole");
        contentFormat = requireText(contentFormat, "contentFormat");
        relativePath = requireText(relativePath, "relativePath");
        rootElementName = normalizeNullable(rootElementName);
        documentName = normalizeNullable(documentName);
        htmlTitle = normalizeNullable(htmlTitle);
        decodedCharset = normalizeNullable(decodedCharset);
        declaredCharset = normalizeNullable(declaredCharset);
        tagCountsSummary = normalizeNullable(tagCountsSummary);
        classCountsSummary = normalizeNullable(classCountsSummary);
        firstParserError = normalizeNullable(firstParserError);
        status = Objects.requireNonNull(status, "status는 필수입니다.");
        errorType = normalizeNullable(errorType);
        errorMessage = normalizeNullable(errorMessage);

        validateNonNegative(fileSizeBytes, "fileSizeBytes");
        validateNonNegative(maxDepth, "maxDepth");
        validateNonNegative(distinctTagCount, "distinctTagCount");
        validateNonNegative(totalElementCount, "totalElementCount");
        validateNonNegative(xformsContainerCount, "xformsContainerCount");
        validateNonNegative(xformsTitleCount, "xformsTitleCount");
        validateNonNegative(xformsInputCount, "xformsInputCount");
        validateNonNegative(tableCount, "tableCount");
        validateNonNegative(topLevelTableCount, "topLevelTableCount");
        validateNonNegative(nestedTableCount, "nestedTableCount");
        validateNonNegative(maxTableDepth, "maxTableDepth");
        validateNonNegative(tableRowCount, "tableRowCount");
        validateNonNegative(tableHeaderCount, "tableHeaderCount");
        validateNonNegative(tableCellCount, "tableCellCount");
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
        validateNonNegative(elapsedMillis, "elapsedMillis");

        if (status == HtmlStructureProfileStatus.SUCCESS) {
            if (rootElementName == null || decodedCharset == null) {
                throw new IllegalArgumentException(
                        "성공한 결과에는 rootElementName과 decodedCharset이 필요합니다."
                );
            }
            if (maxDepth < 1 || tagCountsSummary == null) {
                throw new IllegalArgumentException(
                        "성공한 결과에는 태그 구조 정보가 필요합니다."
                );
            }
            if (errorMessage != null) {
                throw new IllegalArgumentException(
                        "성공한 결과에는 errorMessage를 기록할 수 없습니다."
                );
            }
        }
        if (status == HtmlStructureProfileStatus.FAILED
                && errorMessage == null) {
            throw new IllegalArgumentException(
                    "실패한 결과에는 errorMessage가 필요합니다."
            );
        }
    }

    public static HtmlStructureProfileRow success(
            DisclosureDocument disclosureDocument,
            HtmlStructureProfile profile,
            long elapsedMillis
    ) {
        DisclosureDocument document = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        HtmlStructureProfile result = Objects.requireNonNull(
                profile,
                "profile은 필수입니다."
        );
        Disclosure disclosure = requireDisclosure(document);
        long totalElementCount = result.tagCounts().values().stream()
                .mapToLong(Long::longValue)
                .sum();

        return new HtmlStructureProfileRow(
                requireDocumentId(document),
                disclosure.getSourceDocId(),
                disclosure.getReceiptNo(),
                disclosure.getSourceGroup().getValue(),
                disclosure.getRawSubtype(),
                disclosure.getReportName(),
                disclosure.isCorrection(),
                document.getFileName(),
                document.getDocumentRole().name(),
                document.getContentFormat().name(),
                result.fileSizeBytes(),
                document.getRelativePath(),
                result.rootElementName(),
                document.getDocumentName(),
                result.title(),
                result.decodedCharset(),
                result.declaredCharset(),
                result.maxDepth(),
                result.tagCounts().size(),
                totalElementCount,
                summarizeCounts(result.tagCounts()),
                summarizeCounts(result.classCounts()),
                result.xformsContainerCount(),
                result.xformsTitleCount(),
                result.xformsInputCount(),
                result.tableCount(),
                result.topLevelTableCount(),
                result.nestedTableCount(),
                result.maxTableDepth(),
                result.countOf("TR"),
                result.countOf("TH"),
                result.countOf("TD"),
                result.maxRowsPerTable(),
                result.maxCellsPerRow(),
                result.rowSpanCellCount(),
                result.maxRowSpan(),
                result.colSpanCellCount(),
                result.maxColSpan(),
                result.invalidSpanAttributeCount(),
                result.lineBreakCount(),
                result.anchorCount(),
                result.anchorWithHrefCount(),
                result.imageCount(),
                result.styleCount(),
                result.scriptCount(),
                result.commentCount(),
                result.parserErrorCount(),
                result.firstParserError(),
                elapsedMillis,
                HtmlStructureProfileStatus.SUCCESS,
                null,
                null
        );
    }

    public static HtmlStructureProfileRow failed(
            DisclosureDocument disclosureDocument,
            long elapsedMillis,
            String errorType,
            String errorMessage
    ) {
        DisclosureDocument document = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        Disclosure disclosure = requireDisclosure(document);

        return new HtmlStructureProfileRow(
                requireDocumentId(document),
                disclosure.getSourceDocId(),
                disclosure.getReceiptNo(),
                disclosure.getSourceGroup().getValue(),
                disclosure.getRawSubtype(),
                disclosure.getReportName(),
                disclosure.isCorrection(),
                document.getFileName(),
                document.getDocumentRole().name(),
                document.getContentFormat().name(),
                document.getFileSizeBytes(),
                document.getRelativePath(),
                null,
                document.getDocumentName(),
                null,
                null,
                null,
                0,
                0,
                0,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                elapsedMillis,
                HtmlStructureProfileStatus.FAILED,
                normalizeNullable(errorType),
                requireText(errorMessage, "errorMessage")
        );
    }

    private static UUID requireDocumentId(DisclosureDocument document) {
        return Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 DisclosureDocument는 조사할 수 없습니다."
        );
    }

    private static Disclosure requireDisclosure(DisclosureDocument document) {
        return Objects.requireNonNull(
                document.getDisclosure(),
                "DisclosureDocument의 disclosure는 필수입니다."
        );
    }

    private static String summarizeCounts(Map<String, Long> counts) {
        Objects.requireNonNull(counts, "counts는 필수입니다.");
        return counts.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        return value.strip();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\s+", " ").strip();
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "은 0 이상이어야 합니다."
            );
        }
    }
}
