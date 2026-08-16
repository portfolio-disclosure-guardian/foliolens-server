package com.foliolens.backend.disclosure.infrastructure.parsing;

/**
 * 파싱된 본문 블럭 타입
 */
public enum ParsedDisclosureBlockType {

    HEADING,    // 섹션의 대표 제목 외 추가 제목
    PARAGRAPH,  // 일반 문단
    TABLE,       // 표
    IMAGE,
    PAGE_BREAK
}
