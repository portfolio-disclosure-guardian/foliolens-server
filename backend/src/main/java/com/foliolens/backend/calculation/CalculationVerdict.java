package com.foliolens.backend.calculation;

public enum CalculationVerdict {
    MATCH, // 반올림한 재계산값과 공시값이 같음
    MISMATCH, // 반올림 후에도 공시값과 다름
    NOT_CALCULABLE, // 입력 누락, 분모 0, 단위 불명확 등으로 계산 불가
    NOT_COMPARABLE, // 산술은 가능하나 음수 자기자본 등 정상 비교에 부적합
    APPROXIMATE // 입력이 근사값이라 근사 검증만 가능
}
