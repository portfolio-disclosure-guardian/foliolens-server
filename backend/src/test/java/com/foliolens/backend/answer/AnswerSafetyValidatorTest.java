package com.foliolens.backend.answer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

class AnswerSafetyValidatorTest {

    private final AnswerSafetyValidator validator = new AnswerSafetyValidator();
    private final AnswerPolicy policy = GoldFacility001Fixture.policy();
    private final GoldenCase goldenCase = policy.goldenCases().getFirst();
    private final RetrievalResult retrieval = FakeDisclosureRetriever.complete()
            .retrieve(GoldFacility001Fixture.questionPlan());
    private final CalculationResult calculation = new FakeDisclosureCalculator().calculate(
            new CalculationCommand(
                    CalculationOperation.RATIO,
                    new ComparisonBasis(true, true, true, true)),
            retrieval.facts());

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
        assertThatCode(() -> validate(goldenCase.expectedAnswer()))
                .doesNotThrowAnyException();
    }

    @Test
    void 모델이_투자금액을_바꾸면_거부한다() {
        assertUnsafe(goldenCase.expectedAnswer().replace("5조 2,962억 원", "5조 2,963억 원"));
    }

    @Test
    void 모델이_날짜를_바꾸면_거부한다() {
        assertUnsafe("투자 결정일은 2024년 4월 25일입니다.");
    }

    @Test
    void 모델이_비율을_바꾸면_거부한다() {
        assertUnsafe(goldenCase.expectedAnswer().replace("9.90%", "9.91%"));
    }

    private void assertUnsafe(String answer) {
        assertThatThrownBy(() -> validate(answer))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AGENT_502_1);
    }

    private void validate(String answer) {
        validator.validate(answer, policy, goldenCase, retrieval, calculation);
    }
}
