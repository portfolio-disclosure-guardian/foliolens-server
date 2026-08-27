package com.foliolens.backend.answer;

// think_trace에 노출 가능한 실행 단계. 원시 chain-of-thought가 아닌 공개 가능한 단계 이름만 표현한다.
public enum ExecutionStep {
    PLANNING,
    RETRIEVAL,
    EXTRACTION,
    CALCULATION,
    VALIDATION
}
