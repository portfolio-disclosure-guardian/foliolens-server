package com.foliolens.backend.disclosure.domain.fact;

/**
 * 재무 Fact가 사용하는 회계 작성 기준.
 */
public enum AccountingBasis {
    CONSOLIDATED, // 연결재무제표 기준
    SEPARATE, // 별도재무제표 기준
    OTHER, // 연결·별도 외의 다른 기준
    UNKNOWN // 기준을 확인할 수 없음
}
