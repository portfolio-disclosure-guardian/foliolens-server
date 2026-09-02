package com.foliolens.backend.disclosure.domain.fact;

/**
 * 투자목적·투자대상 등 서술형 Fact의 정규화값.
 */
public record TextFactValue(String value) implements FactValue {

    public TextFactValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "TEXT Fact 값은 비어 있을 수 없습니다."
            );
        }
        value = value.strip();
    }

    @Override
    public FactValueType valueType() {
        return FactValueType.TEXT;
    }
}
