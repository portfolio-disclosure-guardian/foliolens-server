package com.foliolens.backend.disclosure.infrastructure.parsing.pdf;

import java.util.List;

/** 저장·검색 시에도 유지하는 최소 추출의 한계. 품질 휴리스틱은 정확성 인증이 아니다. */
public record PdfTextExtractionReport(
        String mode, int pageCount, List<Integer> noTextPages, List<Integer> suspiciousPages,
        boolean tablesReconstructed, boolean ocrPerformed, String limitation
) {
    public static final String MODE = "PDF_TEXT_ONLY";
    public static final String LIMITATION =
            "PDF 페이지별 텍스트만 추출했습니다. 표의 행·열·수치 관계와 이미지 내용은 검증하지 않았습니다. "
            + "수치 Fact·계산의 확정 근거로 바로 사용하지 말고 원본 페이지를 확인해야 합니다.";

    public PdfTextExtractionReport {
        if (!MODE.equals(mode) || pageCount < 1 || tablesReconstructed || ocrPerformed
                || !LIMITATION.equals(limitation)) {
            throw new IllegalArgumentException("PDF 최소 추출 메타데이터가 올바르지 않습니다.");
        }
        noTextPages = List.copyOf(noTextPages);
        suspiciousPages = List.copyOf(suspiciousPages);
        for (int page : java.util.stream.Stream.concat(noTextPages.stream(), suspiciousPages.stream()).toList()) {
            if (page < 1 || page > pageCount) throw new IllegalArgumentException("PDF 페이지 범위가 잘못됐습니다.");
        }
    }

    public static PdfTextExtractionReport of(int pages, List<Integer> empty, List<Integer> suspicious) {
        return new PdfTextExtractionReport(MODE, pages, empty, suspicious, false, false, LIMITATION);
    }
}
