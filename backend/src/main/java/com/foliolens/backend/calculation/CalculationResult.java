package com.foliolens.backend.calculation;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

import java.util.List;

// GOLD-FACILITY-001 5절: 원계산값과 표시값, 공시값과의 판정을 함께 보존한다.
// 반올림 방식은 CalculationPolicy(AnswerPolicy 영역)가 정하고, 여기서는 그 결과만 담는다.
public record CalculationResult(
        CalculationOperation operation,
        List<String> inputFactIds,
        CalculationVerdict verdict,
        Double rawResult,
        String displayValue,
        String disclosedValue, // 판정 비교 대상이 된 공시 기재값. 비교 대상이 없으면 null
        String unit,
        String verdictReason) {
}
