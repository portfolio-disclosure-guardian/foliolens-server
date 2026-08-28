package com.foliolens.backend.disclosure.domain.fact;

/**
 * 원문 Fact 값의 정규화 처리 결과.
 * 예:
 *  삼조원 → 3조 원
 */
public enum FactNormalizationStatus {
    MAPPED, // 표준값으로 정상 변환됨
    UNMAPPED, // 대응되는 표준값을 찾지 못함
    AMBIGUOUS, // 두 개 이상의 표준값 후보가 있음
    REVIEW_REQUIRED, // 사람의 확인이 필요함
    NOT_APPLICABLE, // 정규화가 필요 없는 값
    MISSING // 정규화할 원본 값이 없음
}
