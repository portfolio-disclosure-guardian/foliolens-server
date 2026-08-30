package com.foliolens.backend.policy;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

import java.math.RoundingMode;

// GOLD-FACILITY-001 5절: 계산 ID는 operation(RATIO 등)보다 상위의, 공식+정책 버전을 묶는 이름이다.
public record CalculationPolicy(
        String calculationId, // 예: "FACILITY_EQUITY_RATIO_CHECK"
        CalculationOperation operation,
        String numeratorFactKey,
        String denominatorFactKey,
        String disclosedValueFactKey, // 재계산값과 비교할 공시 기재값 fact. 비교 대상이 없으면 null
        RoundingMode roundingMode,
        int displayScale) {
}
