package com.foliolens.backend.disclosure.domain;

/**
 * 개별 원문 파일의 파싱 상태
 */
public enum DisclosureDocumentParseStatus {

    PENDING, // 파일 정보만 등록되고 아직 파싱하지 않음
    COMPLETED, // 정상적으로 파싱 완료
    PARTIAL, // 일부 내용만 파싱 성공
    FAILED // 사용할 수 있는 형태로 파싱하지 못함
}
