package com.foliolens.backend.disclosure.domain.fact;

/**
 * Fact의 자료형·단위·대상·기간·근거 검증 상태.
 */
public enum FactValidationStatus {
    UNVALIDATED, // 아직 검증하지 않음
    VERIFIED, // 자료형, 단위, 기간, 대상, 근거 검증을 통과함
    REJECTED // 검증에 실패하여 사용할 수 없음
}
