package com.foliolens.backend.orchestration;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.foliolens.backend.answer.AnswerClaim;
import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerOutcomeJudge;
import com.foliolens.backend.answer.AnswerReferenceValidator;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.AnswerSafetyValidator;
import com.foliolens.backend.answer.ExecutionStep;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.answer.ThinkTraceEntry;
import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.DisclosureCalculator;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.policy.GoldenCaseApprovalStatus;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.service.QuestionRunService;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalResult;

@Service
public class OrchestrationAnswerService {
    private static final ComparisonBasis GOLD_COMPARISON_BASIS = new ComparisonBasis(true, true, true, true);

    private final QuestionRunService questionRunService;
    private final DisclosureRetriever disclosureRetriever;
    private final DisclosureCalculator disclosureCalculator;
    private final AnswerOutcomeJudge answerOutcomeJudge;
    private final AnswerReferenceValidator answerReferenceValidator;
    private final AnswerSafetyValidator answerSafetyValidator;
    private final HcxAnswerGenerator hcxAnswerGenerator;
    private final List<AnswerPolicy> answerPolicies;
    private final boolean requireApprovedGoldenCase;

    public OrchestrationAnswerService(
            QuestionRunService questionRunService,
            DisclosureRetriever disclosureRetriever,
            DisclosureCalculator disclosureCalculator,
            AnswerOutcomeJudge answerOutcomeJudge,
            AnswerReferenceValidator answerReferenceValidator,
            AnswerSafetyValidator answerSafetyValidator,
            HcxAnswerGenerator hcxAnswerGenerator,
            List<AnswerPolicy> answerPolicies,
            @Value("${foliolens.question.answer.require-approved-golden-case:false}")
            boolean requireApprovedGoldenCase) {
        this.questionRunService = questionRunService;
        this.disclosureRetriever = disclosureRetriever;
        this.disclosureCalculator = disclosureCalculator;
        this.answerOutcomeJudge = answerOutcomeJudge;
        this.answerReferenceValidator = answerReferenceValidator;
        this.answerSafetyValidator = answerSafetyValidator;
        this.hcxAnswerGenerator = hcxAnswerGenerator;
        this.answerPolicies = answerPolicies;
        this.requireApprovedGoldenCase = requireApprovedGoldenCase;
    }

    public AnswerResult getAnswer(AnswerQuestionCommand command) {
        QuestionRun run = questionRunService.createQuestionRun(
                command.requestId(),
                command.externalQuestionId(),
                command.question(),
                command.channel());
        questionRunService.startQuestionRun(run);
        try {
            AnswerResult result = generateAnswer(command, run);
            questionRunService.completeQuestionRun(run, result.renderedAnswer());
            return result;
        } catch (RuntimeException exception) {
            ErrorCode errorCode = exception instanceof BusinessException businessException
                    ? businessException.getErrorCode()
                    : ErrorCode.COMMON_500_1;
            try {
                questionRunService.failQuestionRun(run, errorCode);
            } catch (RuntimeException persistenceFailure) {
                exception.addSuppressed(persistenceFailure);
            }
            throw exception;
        }
    }

    private AnswerResult generateAnswer(AnswerQuestionCommand command, QuestionRun run) {
        PolicyMatch match = matchPolicy(command.externalQuestionId()).orElse(null);
        if (match == null || (requireApprovedGoldenCase
                && match.goldenCase().approvalStatus() != GoldenCaseApprovalStatus.APPROVED)) {
            return placeholder(run);
        }
        AnswerPolicy policy = match.policy();
        GoldenCase goldenCase = match.goldenCase();

        RetrievalResult retrieval = disclosureRetriever.retrieve(GoldFacility001Fixture.questionPlan());
        CalculationResult calculation = disclosureCalculator.calculate(
                new CalculationCommand(policy.calculation().operation(), GOLD_COMPARISON_BASIS),
                retrieval.facts());
        AnswerOutcome outcome = answerOutcomeJudge.deriveOutcome(
                policy,
                retrieval.missingFactKeys(),
                calculation.verdict());
        List<AnswerClaim> claims = answerOutcomeJudge.buildClaims(retrieval, calculation);
        var usedDocuments = answerReferenceValidator.validate(retrieval, List.of(calculation), claims);
        String renderedAnswer = hcxAnswerGenerator.generateAnswer(
                command.question(),
                policy,
                retrieval,
                calculation,
                outcome);
        answerSafetyValidator.validate(renderedAnswer, policy, goldenCase);

        return new AnswerResult(
                run.getId(),
                run.getExternalQuestionId(),
                run.getQuestionText(),
                outcome,
                claims,
                List.of(calculation),
                usedDocuments,
                List.of(
                        new ThinkTraceEntry(ExecutionStep.PLANNING, "GOLD-FACILITY-001 고정 계획을 사용했습니다."),
                        new ThinkTraceEntry(ExecutionStep.RETRIEVAL, "고정 공시 fixture에서 근거와 fact를 조회했습니다."),
                        new ThinkTraceEntry(ExecutionStep.CALCULATION, "자기자본 대비 투자금액 비율을 재계산했습니다."),
                        new ThinkTraceEntry(ExecutionStep.VALIDATION, "필수 fact와 계산 판정을 확인했습니다.")),
                renderedAnswer);
    }

    private Optional<PolicyMatch> matchPolicy(String externalQuestionId) {
        for (AnswerPolicy policy : answerPolicies) {
            for (GoldenCase goldenCase : policy.goldenCases()) {
                if (goldenCase.goldenCaseId().equals(externalQuestionId)) {
                    return Optional.of(new PolicyMatch(policy, goldenCase));
                }
            }
        }
        return Optional.empty();
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

    private record PolicyMatch(AnswerPolicy policy, GoldenCase goldenCase) {
    }
}
