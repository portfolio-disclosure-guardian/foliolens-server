package com.foliolens.backend.disclosure.infrastructure.parsing.pdf;

/** PDF 파일의 물리적 페이지(1부터 시작). 인쇄된 쪽번호나 XML 행 번호가 아니다. */
public record PdfPageLocation(int pageNumber, boolean textExtractionSuspect) {
    public PdfPageLocation {
        if (pageNumber < 1) throw new IllegalArgumentException("PDF 페이지는 1 이상이어야 합니다.");
    }
}
