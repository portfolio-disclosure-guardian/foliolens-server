package com.foliolens.backend.answer;

import java.util.List;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

// 모든 입력 근거를 가져야 한다. evidenceIds는 AnswerResult.usedEvidences()의 documentId를 가리킨다.
public record AnswerClaim(
        AnswerClaimType type,
        String text,
        List<String> factIds,
        List<String> evidenceIds,
        CalculationOperation calculationOperation) {
    public AnswerClaim {
        if (type == AnswerClaimType.FACT && factIds.isEmpty() && evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("FACT 주장은 evidence 또는 검증 fact가 있어야 합니다.");
        }
        if (type == AnswerClaimType.CALCULATION && (calculationOperation == null || evidenceIds.isEmpty())) {
            throw new IllegalArgumentException("CALCULATION 주장은 계산 기록과 입력 근거가 있어야 합니다.");
        }
    }
}
