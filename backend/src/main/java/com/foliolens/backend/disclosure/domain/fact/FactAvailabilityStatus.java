package com.foliolens.backend.disclosure.domain.fact;

/**
 * Fact 값의 존재 여부와 결측 사유.
 * Fact 값을 쓸 수 있는지 없는지
 */
public enum FactAvailabilityStatus {
    AVAILABLE, // 값을 정상적으로 확보함
    NOT_STATED, // 공시에 값이 기재되지 않음
    WITHHELD, // 회사가 공개를 유보함
    NOT_APPLICABLE, // 해당 공시에 적용되지 않는 항목
    AMBIGUOUS, // 여러 해석이 가능해 확정할 수 없음
    PARSE_FAILED // 원문은 있으나 파싱에 실패함
}
