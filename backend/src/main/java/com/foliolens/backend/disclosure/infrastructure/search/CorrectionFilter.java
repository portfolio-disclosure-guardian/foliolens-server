package com.foliolens.backend.disclosure.infrastructure.search;

/**
 * 공시 메타데이터 검색에서 정정공시를 포함하는 방식.
 *
 * 최신 유효값 선택은 이 필터가 아니라 정정·후속공시 이력 해결 계층이
 * 담당한다.
 */
public enum CorrectionFilter {
    ALL,
    ORIGINAL_ONLY,
    CORRECTION_ONLY
}
