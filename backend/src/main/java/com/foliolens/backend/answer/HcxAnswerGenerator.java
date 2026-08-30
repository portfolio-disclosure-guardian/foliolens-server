package com.foliolens.backend.answer;

import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.retrieval.RetrievalResult;

public interface HcxAnswerGenerator {

    String generateAnswer(
            String question,
            AnswerPolicy policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome);
}
