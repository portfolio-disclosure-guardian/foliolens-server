package com.foliolens.backend.disclosure.domain;

/**
 * 하나의 공시에 포함된 실제 파일의 역할
 */
public enum DisclosureDocumentRole {
    MAIN, // 공시의 기본 본문
    ATTACHMENT, // 일반 첨부문서
    AUDIT_REPORT, // 감사보고서 등 별도로 검색할 가치가 있는 첨부문서
    VIEWER, // PDF 등을 브라우저에서 보여주기 위한 HTML 뷰어 -> 3개 있음
    UNKNOWN // 아직 파일 역할을 판별하지 못함
}
