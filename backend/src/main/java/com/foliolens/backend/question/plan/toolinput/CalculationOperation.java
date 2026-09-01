package com.foliolens.backend.question.plan.toolinput;

//TODO: 아직은 비중에 대해서만 있고 더 늘려야함
public enum CalculationOperation {
    DIFFERENCE, //비교값 - 기준값
    CHANGE_RATE, //(비교값 - 기준값) / 기준값 × 100
    RATIO, //부분값 / 전체값 × 100
    SUM, //동일 기준 값 합계
    AVERAGE, //동일 기준 값 산술 평균
    DATE_DURATION, //종료일 - 시작일
    UNIT_CONVERSION, //표시 단위 변환
    SHARE_DILUTION //정의된 주식 수 기준 잠재 희석률
}
