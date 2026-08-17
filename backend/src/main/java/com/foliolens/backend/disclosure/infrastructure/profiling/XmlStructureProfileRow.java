package com.foliolens.backend.disclosure.infrastructure.profiling;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * XML 구조 배치 조사 결과의 CSV 한 행.
 */
public record XmlStructureProfileRow(
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
        int maxDepth,
        int distinctTagCount,
        long totalElementCount,
        String tagCountsSummary,
        long section1Count,
        long section2Count,
        long section3Count,
        int maxSectionLevel,
        long section4PlusCount,
        String sectionLevelCountsSummary,
        long titleCount,
        long paragraphCount,
        long paragraphInsideTitleCount,
        long tableCount,
        long tableRowCount,
        long tableHeaderCount,
        long tableCellCount,
        long nestedTableCount,
        int maxTableDepth,
        long paragraphInsideTableCount,
        long titleInsideTableCount,
        long lineBreakTagCount,
        long xmlCommentCount,
        String imageCandidateTagCountsSummary,
        String noteCandidateTagCountsSummary,
        long repairedAmpersandCount,
        long repairedLessThanCount,
        long repairedAttributeQuoteCount,
        long elapsedMillis,
        XmlStructureProfileStatus status,
        String errorType,
        Integer errorLine,
        Integer errorColumn,
        String errorMessage
) {

    public XmlStructureProfileRow {
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
        tagCountsSummary = normalizeNullable(tagCountsSummary);
        sectionLevelCountsSummary = normalizeNullable(
                sectionLevelCountsSummary
        );
        imageCandidateTagCountsSummary = normalizeNullable(
                imageCandidateTagCountsSummary
        );
        noteCandidateTagCountsSummary = normalizeNullable(
                noteCandidateTagCountsSummary
        );
        status = Objects.requireNonNull(status, "status는 필수입니다.");
        errorType = normalizeNullable(errorType);
        errorMessage = normalizeNullable(errorMessage);

        validateNonNegative(fileSizeBytes, "fileSizeBytes");
        validateNonNegative(maxDepth, "maxDepth");
        validateNonNegative(distinctTagCount, "distinctTagCount");
        validateNonNegative(totalElementCount, "totalElementCount");
        validateNonNegative(section1Count, "section1Count");
        validateNonNegative(section2Count, "section2Count");
        validateNonNegative(section3Count, "section3Count");
        validateNonNegative(maxSectionLevel, "maxSectionLevel");
        validateNonNegative(section4PlusCount, "section4PlusCount");
        validateNonNegative(titleCount, "titleCount");
        validateNonNegative(paragraphCount, "paragraphCount");
        validateNonNegative(
                paragraphInsideTitleCount,
                "paragraphInsideTitleCount"
        );
        validateNonNegative(tableCount, "tableCount");
        validateNonNegative(tableRowCount, "tableRowCount");
        validateNonNegative(tableHeaderCount, "tableHeaderCount");
        validateNonNegative(tableCellCount, "tableCellCount");
        validateNonNegative(nestedTableCount, "nestedTableCount");
        validateNonNegative(maxTableDepth, "maxTableDepth");
        validateNonNegative(
                paragraphInsideTableCount,
                "paragraphInsideTableCount"
        );
        validateNonNegative(
                titleInsideTableCount,
                "titleInsideTableCount"
        );
        validateNonNegative(lineBreakTagCount, "lineBreakTagCount");
        validateNonNegative(xmlCommentCount, "xmlCommentCount");
        validateNonNegative(
                repairedAmpersandCount,
                "repairedAmpersandCount"
        );
        validateNonNegative(
                repairedLessThanCount,
                "repairedLessThanCount"
        );
        validateNonNegative(
                repairedAttributeQuoteCount,
                "repairedAttributeQuoteCount"
        );
        validateNonNegative(elapsedMillis, "elapsedMillis");

        if (status == XmlStructureProfileStatus.SUCCESS) {
            if (rootElementName == null) {
                throw new IllegalArgumentException(
                        "성공한 조사 결과에는 rootElementName이 필요합니다."
                );
            }

            if (maxDepth < 1) {
                throw new IllegalArgumentException(
                        "성공한 조사 결과의 maxDepth는 1 이상이어야 합니다."
                );
            }

            if (tagCountsSummary == null) {
                throw new IllegalArgumentException(
                        "성공한 조사 결과에는 tagCountsSummary가 필요합니다."
                );
            }

            if (errorMessage != null) {
                throw new IllegalArgumentException(
                        "성공한 조사 결과에는 errorMessage를 기록할 수 없습니다."
                );
            }
        }

        if (
                status == XmlStructureProfileStatus.FAILED
                        && errorMessage == null
        ) {
            throw new IllegalArgumentException(
                    "실패한 조사 결과에는 errorMessage가 필요합니다."
            );
        }
    }

    public static XmlStructureProfileRow success(
            DisclosureDocument disclosureDocument,
            XmlStructureProfile profile,
            long elapsedMillis
    ) {
        DisclosureDocument document = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        XmlStructureProfile result = Objects.requireNonNull(
                profile,
                "profile은 필수입니다."
        );
        Disclosure disclosure = requireDisclosure(document);
        XmlAdditionalStructureProfile additional =
                result.additionalStructure();

        long totalElementCount = result.tagCounts()
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        return new XmlStructureProfileRow(
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
                result.documentName(),
                result.maxDepth(),
                result.tagCounts().size(),
                totalElementCount,
                summarizeCounts(result.tagCounts()),
                result.countOf("SECTION-1"),
                result.countOf("SECTION-2"),
                result.countOf("SECTION-3"),
                additional.maxSectionLevel(),
                additional.sectionCountAbove(3),
                summarizeCounts(additional.sectionLevelCounts()),
                result.countOf("TITLE"),
                result.countOf("P"),
                additional.paragraphInsideTitleCount(),
                result.countOf("TABLE"),
                result.countOf("TR"),
                result.countOf("TH"),
                result.countOf("TD"),
                additional.nestedTableCount(),
                additional.maxTableDepth(),
                additional.paragraphInsideTableCount(),
                additional.titleInsideTableCount(),
                additional.lineBreakTagCount(),
                additional.xmlCommentCount(),
                summarizeCounts(additional.imageCandidateTagCounts()),
                summarizeCounts(additional.noteCandidateTagCounts()),
                result.repairedAmpersandCount(),
                result.repairedLessThanCount(),
                result.repairedAttributeQuoteCount(),
                elapsedMillis,
                XmlStructureProfileStatus.SUCCESS,
                null,
                null,
                null,
                null
        );
    }

    public static XmlStructureProfileRow failed(
            DisclosureDocument disclosureDocument,
            long elapsedMillis,
            String errorType,
            Integer errorLine,
            Integer errorColumn,
            String errorMessage
    ) {
        DisclosureDocument document = Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );
        Disclosure disclosure = requireDisclosure(document);

        return new XmlStructureProfileRow(
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
                0,
                0,
                0,
                null,
                0,
                0,
                0,
                0,
                0,
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
                null,
                null,
                0,
                0,
                0,
                elapsedMillis,
                XmlStructureProfileStatus.FAILED,
                errorType,
                errorLine,
                errorColumn,
                requireText(errorMessage, "errorMessage")
        );
    }

    private static UUID requireDocumentId(
            DisclosureDocument document
    ) {
        return Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 DisclosureDocument로 조사 결과를 만들 수 없습니다."
        );
    }

    private static Disclosure requireDisclosure(
            DisclosureDocument document
    ) {
        return Objects.requireNonNull(
                document.getDisclosure(),
                "DisclosureDocument의 disclosure는 필수입니다."
        );
    }

    private static String summarizeCounts(Map<?, Long> counts) {
        Objects.requireNonNull(counts, "counts는 필수입니다.");

        return counts
                .entrySet()
                .stream()
                .sorted(
                        Comparator.comparing(entry ->
                                String.valueOf(entry.getKey())
                        )
                )
                .map(entry ->
                        entry.getKey()
                                + "="
                                + entry.getValue()
                )
                .collect(Collectors.joining(";"));
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "는 필수입니다."
            );
        }

        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static void validateNonNegative(
            long value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "은 0 이상이어야 합니다."
            );
        }
    }
}
