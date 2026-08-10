package com.foliolens.backend.disclosure.infrastructure.profiling;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;

import java.util.Objects;
import java.util.UUID;

/**
 * XML 구조 배치 조사 결과 파일의 한 행.
 *
 * 공시 및 원문 파일 식별 정보와 구조 조사 요약을 한 객체에 모은다.
 * 조사에 실패한 파일도 FAILED 행으로 남겨 배치 전체 결과에서 누락되지 않게 한다.
 */
public record XmlStructureProfileRow(
        UUID disclosureDocumentId, // DB에 저장된 공시 원문 파일의 ID
        String sourceDocId, // 데이터셋에서 공시를 식별하는 ID
        String receiptNo, // DART 공시 접수번호
        String sourceGroup, // periodic, major 등 공시 원본 그룹
        String rawSubtype, // 데이터셋에 기록된 세부 공시 유형
        String reportName, // 공시 보고서명
        boolean correction, // 정정공시 여부
        String fileName, // 조사한 원문 파일명
        String documentRole, // 본문, 첨부, 감사보고서 등 파일 역할
        String contentFormat, // DART_XML, HTML 등 실제 콘텐츠 형식
        long fileSizeBytes, // 조사 시점의 실제 파일 크기(byte)
        String relativePath, // 원문 데이터 루트 기준 파일 상대 경로
        String rootElementName, // XML의 최상위 루트 태그명
        String documentName, // DOCUMENT-NAME 태그에서 읽은 문서명
        int maxDepth, // XML 요소가 중첩된 최대 깊이
        int distinctTagCount, // 서로 다른 태그 종류의 개수
        long totalElementCount, // XML에 등장한 전체 요소 개수
        long section1Count, // SECTION-1 태그 개수
        long section2Count, // SECTION-2 태그 개수
        long section3Count, // SECTION-3 태그 개수
        long titleCount, // TITLE 태그 개수
        long paragraphCount, // 일반 문단을 나타내는 P 태그 개수
        long tableCount, // TABLE 태그 개수
        long tableRowCount, // 표의 행을 나타내는 TR 태그 개수
        long tableHeaderCount, // 표의 제목 셀을 나타내는 TH 태그 개수
        long tableCellCount, // 표의 일반 셀을 나타내는 TD 태그 개수
        long repairedAmpersandCount, // 파싱 전에 보정한 독립된 & 문자 개수
        long repairedLessThanCount, // 파싱 전에 보정한 독립된 < 문자 개수
        long elapsedMillis, // 파일 하나를 조사하는 데 걸린 시간(ms)
        XmlStructureProfileStatus status, // 구조 조사 성공 또는 실패 상태
        String errorType, // 최하위 원인 예외 클래스명
        Integer errorLine, // XML 오류 발생 행
        Integer errorColumn, // XML 오류 발생 열
        String errorMessage // 최하위 원인 예외 메시지
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
        status = Objects.requireNonNull(status, "status는 필수입니다.");
        errorMessage = normalizeNullable(errorMessage);

        validateNonNegative(fileSizeBytes, "fileSizeBytes");
        validateNonNegative(maxDepth, "maxDepth");
        validateNonNegative(distinctTagCount, "distinctTagCount");
        validateNonNegative(totalElementCount, "totalElementCount");
        validateNonNegative(section1Count, "section1Count");
        validateNonNegative(section2Count, "section2Count");
        validateNonNegative(section3Count, "section3Count");
        validateNonNegative(titleCount, "titleCount");
        validateNonNegative(paragraphCount, "paragraphCount");
        validateNonNegative(tableCount, "tableCount");
        validateNonNegative(tableRowCount, "tableRowCount");
        validateNonNegative(tableHeaderCount, "tableHeaderCount");
        validateNonNegative(tableCellCount, "tableCellCount");
        validateNonNegative(repairedAmpersandCount, "repairedAmpersandCount");
        validateNonNegative(repairedLessThanCount, "repairedLessThanCount");
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
                result.countOf("SECTION-1"),
                result.countOf("SECTION-2"),
                result.countOf("SECTION-3"),
                result.countOf("TITLE"),
                result.countOf("P"),
                result.countOf("TABLE"),
                result.countOf("TR"),
                result.countOf("TH"),
                result.countOf("TD"),
                result.repairedAmpersandCount(),
                result.repairedLessThanCount(),
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
                elapsedMillis,
                XmlStructureProfileStatus.FAILED,
                errorType,
                errorLine,
                errorColumn,
                requireText(errorMessage, "errorMessage")

        );
    }

    private static UUID requireDocumentId(DisclosureDocument document) {
        return Objects.requireNonNull(
                document.getId(),
                "저장되지 않은 DisclosureDocument는 조사 결과 행을 만들 수 없습니다."
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }

        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0 이상이어야 합니다."
            );
        }
    }
}
