package com.foliolens.backend.answer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.retrieval.RetrievalResult;

@Component
@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "false", matchIfMissing = true)
public final class FakeHcxAnswerGenerator implements HcxAnswerGenerator {

    @Override
    public String generateAnswer(
            String question,
            AnswerPolicy policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> policy.goldenCases().getFirst().expectedAnswer();
            case PARTIAL -> "대회 제공 공시 원문에서 일부 필수 항목을 확인할 수 없습니다.";
            case UNANSWERABLE -> "대회 제공 공시 원문에서 해당 항목을 확인할 수 없습니다.";
        };
    }
}
