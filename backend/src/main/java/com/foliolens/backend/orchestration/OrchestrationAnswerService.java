package com.foliolens.backend.orchestration;

import java.util.List;
import org.springframework.stereotype.Service;

import com.foliolens.backend.answer.AnswerClaim;
import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerOutcomeJudge;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.ExecutionStep;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.answer.ThinkTraceEntry;
import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.DisclosureCalculator;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.service.QuestionRunService;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrchestrationAnswerService {
    private static final ComparisonBasis GOLD_COMPARISON_BASIS = new ComparisonBasis(true, true, true, true);

    private final QuestionRunService questionRunService;
    private final DisclosureRetriever disclosureRetriever;
    private final DisclosureCalculator disclosureCalculator;
    private final AnswerOutcomeJudge answerOutcomeJudge;
    private final HcxAnswerGenerator hcxAnswerGenerator;

    public AnswerResult getAnswer(AnswerQuestionCommand command) {
        QuestionRun run = questionRunService.createQuestionRun(command.externalQuestionId(), command.question());
        AnswerPolicy policy = GoldFacility001Fixture.policy();
        if (!policy.goldenCase().goldenCaseId().equals(command.externalQuestionId())) {
            return placeholder(run);
        }

        RetrievalResult retrieval = disclosureRetriever.retrieve(GoldFacility001Fixture.questionPlan());
        CalculationResult calculation = disclosureCalculator.calculate(
                new CalculationCommand(policy.calculation().operation(), GOLD_COMPARISON_BASIS),
                retrieval.facts());
        AnswerOutcome outcome = answerOutcomeJudge.deriveOutcome(
                policy,
                retrieval.missingFactKeys(),
                calculation.verdict());
        List<AnswerClaim> claims = answerOutcomeJudge.buildClaims(retrieval, calculation);
        String renderedAnswer = hcxAnswerGenerator.generateAnswer(
                command.question(),
                policy,
                retrieval,
                calculation,
                outcome);

        return new AnswerResult(
                run.getId(),
                run.getExternalQuestionId(),
                run.getQuestionText(),
                outcome,
                claims,
                List.of(calculation),
                retrieval.documents(),
                List.of(
                        new ThinkTraceEntry(ExecutionStep.PLANNING, "GOLD-FACILITY-001 고정 계획을 사용했습니다."),
                        new ThinkTraceEntry(ExecutionStep.RETRIEVAL, "고정 공시 fixture에서 근거와 fact를 조회했습니다."),
                        new ThinkTraceEntry(ExecutionStep.CALCULATION, "자기자본 대비 투자금액 비율을 재계산했습니다."),
                        new ThinkTraceEntry(ExecutionStep.VALIDATION, "필수 fact와 계산 판정을 확인했습니다.")),
                renderedAnswer);
    }

    private AnswerResult placeholder(QuestionRun run) {
        return new AnswerResult(
                run.getId(),
                run.getExternalQuestionId(),
                run.getQuestionText(),
                AnswerOutcome.UNANSWERABLE,
                List.of(),
                List.of(),
                List.of(),
                List.of(new ThinkTraceEntry(ExecutionStep.PLANNING, "질문 실행을 접수했습니다.")),
                "답변 생성 기능이 아직 연결되지 않았습니다.");
    }
}
