package com.foliolens.backend.answer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;

class AnswerSafetyValidatorTest {

    private final AnswerSafetyValidator validator = new AnswerSafetyValidator();
    private final AnswerPolicy policy = GoldFacility001Fixture.policy();
    private final GoldenCase goldenCase = policy.goldenCases().getFirst();

    @Test
    void 금지_표현을_그대로_포함한_답변은_거부한다() {
        assertUnsafe("답변에 " + policy.forbiddenExpressions().getFirst() + " 표현이 있습니다.");
    }

    @Test
    void 치명적_오류를_그대로_포함한_답변은_거부한다() {
        assertUnsafe("답변에 " + goldenCase.criticalErrors().getFirst() + " 문제가 있습니다.");
    }

    @Test
    void 비어_있는_답변은_거부한다() {
        assertUnsafe(" ");
    }

    @Test
    void 금지_문구가_없는_답변은_통과한다() {
        assertThatCode(() -> validator.validate(goldenCase.expectedAnswer(), policy, goldenCase))
                .doesNotThrowAnyException();
    }

    private void assertUnsafe(String answer) {
        assertThatThrownBy(() -> validator.validate(answer, policy, goldenCase))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AGENT_502_1);
    }
}
