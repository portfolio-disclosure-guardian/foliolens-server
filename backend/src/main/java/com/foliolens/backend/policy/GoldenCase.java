package com.foliolens.backend.policy;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.calculation.CalculationVerdict;

import java.util.Map;

// GOLD-FACILITY-001 3~7절의 기대값. 실제 검색·계산 구현을 이 값과 대조하는 회귀 테스트 기준선.
public record GoldenCase(
        String goldenCaseId,
        String question,
        String companyName,
        String receiptNo,
        Map<String, String> expectedNormalizedFacts, // factKey -> 정규화값 문자열
        String expectedRawResult,
        String expectedDisplayValue,
        CalculationVerdict expectedVerdict,
        AnswerOutcome expectedOutcome,
        String expectedAnswer) {
}
