package com.foliolens.backend.answer;

import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldenCase;

@Component
public class AnswerSafetyValidator {

    public void validate(String renderedAnswer, AnswerPolicy policy, GoldenCase goldenCase) {
        if (renderedAnswer == null || renderedAnswer.isBlank()) {
            throw new BusinessException(ErrorCode.AGENT_502_1, "검증할 답변이 비어 있습니다.");
        }

        Stream.concat(policy.forbiddenExpressions().stream(), goldenCase.criticalErrors().stream())
                .filter(expression -> expression != null && !expression.isBlank())
                .filter(renderedAnswer::contains)
                .findFirst()
                .ifPresent(matched -> {
                    throw new BusinessException(
                            ErrorCode.AGENT_502_1,
                            "답변에 금지된 표현이 포함되어 있습니다: " + matched);
                });
    }
}
