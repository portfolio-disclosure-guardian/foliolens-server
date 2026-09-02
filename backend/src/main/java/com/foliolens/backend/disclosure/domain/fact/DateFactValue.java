package com.foliolens.backend.disclosure.domain.fact;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 결정일·투자 시작일·종료일 Fact의 ISO 날짜 정규화값.
 */
public record DateFactValue(LocalDate value) implements FactValue {

    public DateFactValue {
        value = Objects.requireNonNull(
                value,
                "DATE Fact 값은 필수입니다."
        );
    }

    @Override
    public FactValueType valueType() {
        return FactValueType.DATE;
    }
}
