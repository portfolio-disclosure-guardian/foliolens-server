package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

public record XmlParsingValidationMetrics(
        int sectionCount, // 모든 단계의 섹션 개수
        int maxSectionLevel, // 가장 깊은 SECTION-N

        int totalBlockCount, // 섹션과 문서 앞부분의 전체 블록 개수
        int headingCount, // 추가 제목 블록 개수
        int paragraphCount, // 문단 블록 개수
        int pageBreakCount, // 페이지 구분 블록 개수

        int tableCount, // 최상위 표와 중첩 표를 모두 포함한 개수
        int nestedTableCount, // 다른 표의 셀 안에 들어간 표 개수
        int tableRowCount, // 전체 표 행 개수
        int tableCellCount, // 전체 표 셀 개수

        int imageCount, // 일반 이미지와 표 셀 이미지 개수
        long textCharacterCount // 구조화된 텍스트 길이
) {
}
