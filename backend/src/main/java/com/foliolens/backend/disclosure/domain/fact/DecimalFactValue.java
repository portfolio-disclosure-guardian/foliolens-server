package com.foliolens.backend.disclosure.domain.fact;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 금액·비율·환율처럼 십진 정밀도가 필요한 Fact의 정규화값.
 */
public record DecimalFactValue(BigDecimal value) implements FactValue {

    public DecimalFactValue {
        value = Objects.requireNonNull(
                value,
                "DECIMAL Fact 값은 필수입니다."
        );
    }

    @Override
    public FactValueType valueType() {
        return FactValueType.DECIMAL;
    }
}
