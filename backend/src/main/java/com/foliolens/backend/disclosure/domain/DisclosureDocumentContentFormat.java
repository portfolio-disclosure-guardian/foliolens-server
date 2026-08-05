package com.foliolens.backend.disclosure.domain;

/**
 * 파일 내용을 확인해서 판별한 실제 문서 형식
 */
public enum DisclosureDocumentContentFormat {

    DART_XML, // DART 전용 문서 구조 XML
    HTML, // HTML 문서
    PDF, // PDF 문서
    UNKNOWN // 아직 형식을 판별하지 못함
}
