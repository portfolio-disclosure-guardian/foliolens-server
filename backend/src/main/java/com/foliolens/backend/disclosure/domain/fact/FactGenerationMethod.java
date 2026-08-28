package com.foliolens.backend.disclosure.domain.fact;

/**
 * Fact 값이 생성된 방식.
 */
public enum FactGenerationMethod {
    SOURCE_METADATA, // 공시 메타데이터에서 가져옴
    DIRECT_RAW, // 원문 값을 그대로 가져옴
    TEXT_EXTRACTED, // 문장에서 값을 추출함
    DIRECT_NORMALIZED, // 원문 값을 표준 형태로 변환함
    DERIVED_CLASSIFICATION, // 규칙에 따라 분류함
    DERIVED_CALCULATION, // 다른 Fact로 계산함
    LINKED_RESOLVED, // 정정·후속공시를 연결해 결정함
    SYSTEM_ASSIGNED // 시스템이 관리 목적으로 부여함
}
