package com.foliolens.backend.answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

// ROLE_A_SPEC.md A5: fake retriever·calculator만으로 완료/부분/답변불가 시나리오가 올바른
// outcome·claims·calculations를 만들어내는지 확인한다.
// "실패" 시나리오(QuestionRunStatus.FAILED)는 시스템 오류 축이라 AnswerResult와 무관해 다루지 않는다.
class AnswerScenarioTest {

    private final AnswerPolicy policy = GoldFacility001Fixture.policy();
    private final FakeDisclosureCalculator calculator = new FakeDisclosureCalculator();
    private final FakeHcxAnswerGenerator answerGenerator = new FakeHcxAnswerGenerator();
    private final AnswerOutcomeJudge judge = new AnswerOutcomeJudge();
    private final CalculationCommand command = new CalculationCommand(
            CalculationOperation.RATIO, new ComparisonBasis(true, true, true, true));

    @Test
    void 모든_필수_fact와_계산이_일치하면_완료다() {
        RetrievalResult retrieval = FakeDisclosureRetriever.missingFacts("facility.target")
                .retrieve(GoldFacility001Fixture.questionPlan());
        CalculationResult calculation = calculator.calculate(command, retrieval.facts());

        assertEquals(List.of("facility.target"), retrieval.missingFactKeys());
        assertEquals(CalculationVerdict.MATCH, calculation.verdict());
        assertEquals("9.90", calculation.displayValue());
        AnswerOutcome outcome = judge.deriveOutcome(
                policy, retrieval.missingFactKeys(), calculation.verdict());
        assertEquals(AnswerOutcome.COMPLETED, outcome);

        List<AnswerClaim> claims = judge.buildClaims(retrieval, calculation);
        assertEquals(retrieval.facts().size(), claims.stream().filter(c -> c.type() == AnswerClaimType.FACT).count());
        assertEquals(1, claims.stream().filter(c -> c.type() == AnswerClaimType.CALCULATION).count());
        assertEquals(policy.goldenCases().getFirst().expectedAnswer(),
                answerGenerator.generateAnswer(policy.goldenCases().getFirst().question(), policy, retrieval, calculation, outcome));
    }

    @Test
    void 필수_fact가_있어도_계산할_수_없으면_부분이다() {
        assertEquals(AnswerOutcome.PARTIAL,
                judge.deriveOutcome(policy, List.of(), CalculationVerdict.NOT_CALCULABLE));
    }

    @Test
    void 필수_fact가_일부_빠지면_부분이다() {
        RetrievalResult retrieval = FakeDisclosureRetriever.missingFacts("facility.purpose")
                .retrieve(GoldFacility001Fixture.questionPlan());
        CalculationResult calculation = calculator.calculate(command, retrieval.facts());

        assertEquals(List.of("facility.purpose"), retrieval.missingFactKeys());
        assertEquals(CalculationVerdict.MATCH, calculation.verdict()); // 비율 계산 자체는 여전히 가능
        AnswerOutcome outcome = judge.deriveOutcome(
                policy, retrieval.missingFactKeys(), calculation.verdict());
        assertEquals(AnswerOutcome.PARTIAL, outcome);

        List<AnswerClaim> claims = judge.buildClaims(retrieval, calculation);
        assertTrue(claims.stream().noneMatch(c -> c.factIds().contains("FACT-facility.purpose")));
        assertTrue(answerGenerator.generateAnswer(
                policy.goldenCases().getFirst().question(), policy, retrieval, calculation, outcome).contains("일부 필수 항목"));
    }

    @Test
    void 문서를_찾지_못하면_답변불가다() {
        RetrievalResult retrieval = FakeDisclosureRetriever.noDocuments()
                .retrieve(GoldFacility001Fixture.questionPlan());
        CalculationResult calculation = calculator.calculate(command, retrieval.facts());

        assertTrue(retrieval.facts().isEmpty());
        assertEquals(CalculationVerdict.NOT_CALCULABLE, calculation.verdict());
        AnswerOutcome outcome = judge.deriveOutcome(
                policy, retrieval.missingFactKeys(), calculation.verdict());
        assertEquals(AnswerOutcome.UNANSWERABLE, outcome);

        List<AnswerClaim> claims = judge.buildClaims(retrieval, calculation);
        assertTrue(claims.isEmpty());
        assertTrue(answerGenerator.generateAnswer(
                policy.goldenCases().getFirst().question(), policy, retrieval, calculation, outcome).contains("확인할 수 없습니다"));
    }

}
