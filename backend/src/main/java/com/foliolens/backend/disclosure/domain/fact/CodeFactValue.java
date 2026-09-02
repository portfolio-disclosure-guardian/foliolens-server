package com.foliolens.backend.disclosure.domain.fact;

/**
 * 승인된 투자구분 등 표준 코드 Fact의 정규화값.
 */
public record CodeFactValue(String value) implements FactValue {

    public CodeFactValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "CODE Fact 값은 비어 있을 수 없습니다."
            );
        }
        value = value.strip();
        if (!value.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new IllegalArgumentException(
                    "CODE Fact 값은 승인된 대문자 스네이크 코드여야 합니다."
            );
        }
    }

    @Override
    public FactValueType valueType() {
        return FactValueType.CODE;
    }
}
