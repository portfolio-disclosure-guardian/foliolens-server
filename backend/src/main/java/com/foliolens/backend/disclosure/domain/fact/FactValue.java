package com.foliolens.backend.disclosure.domain.fact;

/**
 * 문자열 Object 하나로 타입을 잃지 않도록 하는 Fact 정규화값 계약.
 */
public sealed interface FactValue permits TextFactValue,
        DecimalFactValue,
        DateFactValue,
        CodeFactValue {

    FactValueType valueType();
}
