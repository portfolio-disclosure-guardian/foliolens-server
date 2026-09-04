package com.foliolens.backend.orchestration;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import com.foliolens.backend.policy.FactNecessity;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.policy.GoldenCaseApprovalStatus;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.global.web.RequestCorrelationFilter;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.plan.HcxPlanGenerator;
import com.foliolens.backend.question.plan.QuestionPlanConverter;
import com.foliolens.backend.question.plan.ToolType;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;
import com.foliolens.backend.question.plan.confirmation.PlanStep;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.plan.confirmation.ResolvedCompanyRef;
import com.foliolens.backend.question.plan.toolinput.LookupFactsInput;
import com.foliolens.backend.question.plan.toolinput.SearchDisclosuresInput;
import com.foliolens.backend.question.service.QuestionRunService;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrchestrationAnswerService {
    private static final ComparisonBasis GOLD_COMPARISON_BASIS = new ComparisonBasis(true, true, true, true);
    private static final int MAX_ANSWER_GENERATION_ATTEMPTS = 2;

    private final QuestionRunService questionRunService;
    private final DisclosureRetriever disclosureRetriever;
    private final DisclosureCalculator disclosureCalculator;
    private final AnswerOutcomeJudge answerOutcomeJudge;
    private final AnswerReferenceValidator answerReferenceValidator;
    private final AnswerSafetyValidator answerSafetyValidator;
    private final HcxPlanGenerator hcxPlanGenerator;
    private final QuestionPlanConverter questionPlanConverter;
    private final HcxAnswerGenerator hcxAnswerGenerator;
    private final List<AnswerPolicy> answerPolicies;
    private final boolean requireApprovedGoldenCase;
    private final long answerDeadlineMillis;
    private final String datasetVersion;
    private final String modelVersion;

    public OrchestrationAnswerService(
            QuestionRunService questionRunService,
            DisclosureRetriever disclosureRetriever,
            DisclosureCalculator disclosureCalculator,
            AnswerOutcomeJudge answerOutcomeJudge,
            AnswerReferenceValidator answerReferenceValidator,
            AnswerSafetyValidator answerSafetyValidator,
            HcxPlanGenerator hcxPlanGenerator,
            QuestionPlanConverter questionPlanConverter,
            HcxAnswerGenerator hcxAnswerGenerator,
            List<AnswerPolicy> answerPolicies,
            @Value("${foliolens.question.answer.require-approved-golden-case:false}")
            boolean requireApprovedGoldenCase,
            @Value("${foliolens.question.answer.deadline-ms:30000}")
            long answerDeadlineMillis,
            @Value("${foliolens.dataset.version:unknown}")
            String datasetVersion,
            @Value("${hcx.api.model:unknown}")
            String modelVersion) {
        this.questionRunService = questionRunService;
        this.disclosureRetriever = disclosureRetriever;
        this.disclosureCalculator = disclosureCalculator;
        this.answerOutcomeJudge = answerOutcomeJudge;
        this.answerReferenceValidator = answerReferenceValidator;
        this.answerSafetyValidator = answerSafetyValidator;
        this.hcxPlanGenerator = hcxPlanGenerator;
        this.questionPlanConverter = questionPlanConverter;
        this.hcxAnswerGenerator = hcxAnswerGenerator;
        this.answerPolicies = answerPolicies;
        this.requireApprovedGoldenCase = requireApprovedGoldenCase;
        if (answerDeadlineMillis <= 0) {
            throw new IllegalArgumentException("answer deadline은 1ms 이상이어야 합니다.");
        }
        this.answerDeadlineMillis = answerDeadlineMillis;
        this.datasetVersion = datasetVersion;
        this.modelVersion = modelVersion;
    }

    public AnswerResult getAnswer(AnswerQuestionCommand command) {
        long startedNanos = System.nanoTime();
        QuestionRun run = questionRunService.createQuestionRun(
                command.requestId(),
                command.externalQuestionId(),
                command.question(),
                command.channel());
        questionRunService.startQuestionRun(run);
        try {
            AnswerResult result = generateWithinDeadline(command, run);
            questionRunService.completeQuestionRun(run, result.renderedAnswer());
            log.info(
                    "question_run_completed requestId={} externalQuestionId={} runId={} "
                            + "durationMs={} outcome={}",
                    command.requestId(),
                    safeLogIdentifier(command.externalQuestionId()),
                    run.getId(),
                    elapsedMillis(startedNanos),
                    result.outcome());
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
            log.warn(
                    "question_run_failed requestId={} externalQuestionId={} runId={} "
                            + "durationMs={} errorCode={}",
                    command.requestId(),
                    safeLogIdentifier(command.externalQuestionId()),
                    run.getId(),
                    elapsedMillis(startedNanos),
                    errorCode.getCode());
            throw exception;
        }
    }

    private AnswerResult generateWithinDeadline(
            AnswerQuestionCommand command,
            QuestionRun run) {
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(answerDeadlineMillis);
        FutureTask<AnswerResult> task = new FutureTask<>(
                () -> RequestCorrelationFilter.withRequestId(
                        command.requestId(),
                        () -> generateAnswer(command, run, deadlineNanos)));
        Thread.ofVirtual()
                .name("answer-run-" + run.getId())
                .start(task);

        try {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                task.cancel(true);
                throw deadlineExceeded(null);
            }
            return task.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw deadlineExceeded(exception);
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw deadlineExceeded(exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException(
                    ErrorCode.COMMON_500_1,
                    "질문 처리 중 알 수 없는 오류가 발생했습니다.",
                    cause);
        }
    }

    private AnswerResult generateAnswer(
            AnswerQuestionCommand command,
            QuestionRun run,
            long deadlineNanos) {
        ensureBeforeDeadline(deadlineNanos);
        long planningStartedNanos = System.nanoTime();
        QuestionPlanCandidate planCandidate = hcxPlanGenerator.generatePlan(command.question());
        QuestionPlan plan = questionPlanConverter.candidateToConfirmation(planCandidate);
        questionRunService.recordQuestionPlan(run, plan);
        log.info(
                "question_stage_completed requestId={} externalQuestionId={} runId={} stage=PLANNING "
                        + "durationMs={} datasetVersion={} planSchemaVersion={} modelVersion={}",
                command.requestId(),
                safeLogIdentifier(command.externalQuestionId()),
                run.getId(),
                elapsedMillis(planningStartedNanos),
                datasetVersion,
                plan.schemaVersion(),
                modelVersion);
        ensureBeforeDeadline(deadlineNanos);

        PolicyMatch match = matchPolicy(plan, command.question()).orElse(null);
        if (match == null || (requireApprovedGoldenCase
                && match.goldenCase().approvalStatus() != GoldenCaseApprovalStatus.APPROVED)) {
            return placeholder(run);
        }
        AnswerPolicy policy = match.policy();
        GoldenCase goldenCase = match.goldenCase();
        plan = ensureRequiredFactKeysLookedUp(plan, policy);

        long retrievalStartedNanos = System.nanoTime();
        RetrievalResult retrieval = answerReferenceValidator.verifiedOnly(
                disclosureRetriever.retrieve(plan),
                policy);
        log.info(
                "question_stage_completed requestId={} externalQuestionId={} runId={} stage=RETRIEVAL "
                        + "durationMs={} datasetVersion={} retrievalVersion={} documentCount={} evidenceCount={}",
                command.requestId(),
                safeLogIdentifier(command.externalQuestionId()),
                run.getId(),
                elapsedMillis(retrievalStartedNanos),
                datasetVersion,
                retrieval.retrievalVersion(),
                retrieval.documents().size(),
                retrieval.evidences().size());
        ensureBeforeDeadline(deadlineNanos);

        long calculationStartedNanos = System.nanoTime();
        CalculationResult calculation = disclosureCalculator.calculate(
                new CalculationCommand(policy.calculation().operation(), GOLD_COMPARISON_BASIS),
                retrieval.facts());
        log.info(
                "question_stage_completed requestId={} externalQuestionId={} runId={} stage=CALCULATION "
                        + "durationMs={} policyVersion={} operation={} verdict={}",
                command.requestId(),
                safeLogIdentifier(command.externalQuestionId()),
                run.getId(),
                elapsedMillis(calculationStartedNanos),
                policy.policyVersion(),
                calculation.operation(),
                calculation.verdict());
        ensureBeforeDeadline(deadlineNanos);
        AnswerOutcome outcome = answerOutcomeJudge.deriveOutcome(
                policy,
                retrieval.missingFactKeys(),
                calculation.verdict());
        List<AnswerClaim> claims = answerOutcomeJudge.buildClaims(retrieval, calculation);
        var usedDocuments = answerReferenceValidator.validate(retrieval, List.of(calculation), claims);
        ensureBeforeDeadline(deadlineNanos);
        String renderedAnswer = generateValidatedAnswer(
                command,
                run,
                policy,
                goldenCase,
                retrieval,
                calculation,
                outcome,
                deadlineNanos);

        return new AnswerResult(
                run.getId(),
                run.getExternalQuestionId(),
                run.getQuestionText(),
                outcome,
                claims,
                List.of(calculation),
                usedDocuments,
                List.of(
                        new ThinkTraceEntry(ExecutionStep.PLANNING, "HCX가 생성한 계획을 검증해 사용했습니다."),
                        new ThinkTraceEntry(ExecutionStep.RETRIEVAL, "검증된 계획으로 근거와 fact를 조회했습니다."),
                        new ThinkTraceEntry(ExecutionStep.CALCULATION, "자기자본 대비 투자금액 비율을 재계산했습니다."),
                        new ThinkTraceEntry(ExecutionStep.VALIDATION, "필수 fact와 계산 판정을 확인했습니다.")),
                renderedAnswer);
    }

    private String generateValidatedAnswer(
            AnswerQuestionCommand command,
            QuestionRun run,
            AnswerPolicy policy,
            GoldenCase goldenCase,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome,
            long deadlineNanos) {
        for (int attempt = 1; attempt <= MAX_ANSWER_GENERATION_ATTEMPTS; attempt++) {
            ensureBeforeDeadline(deadlineNanos);
            long generationStartedNanos = System.nanoTime();
            String renderedAnswer = hcxAnswerGenerator.generateAnswer(
                    command.question(),
                    policy,
                    retrieval,
                    calculation,
                    outcome);
            log.info(
                    "question_stage_completed requestId={} externalQuestionId={} runId={} "
                            + "stage=ANSWER_GENERATION durationMs={} modelVersion={} attempt={}",
                    command.requestId(),
                    safeLogIdentifier(command.externalQuestionId()),
                    run.getId(),
                    elapsedMillis(generationStartedNanos),
                    modelVersion,
                    attempt);
            ensureBeforeDeadline(deadlineNanos);
            long validationStartedNanos = System.nanoTime();
            try {
                answerSafetyValidator.validate(
                        renderedAnswer,
                        policy,
                        goldenCase,
                        retrieval,
                        calculation);
                log.info(
                        "question_stage_completed requestId={} externalQuestionId={} runId={} "
                                + "stage=VALIDATION durationMs={} policyVersion={} attempt={} status=SUCCESS",
                        command.requestId(),
                        safeLogIdentifier(command.externalQuestionId()),
                        run.getId(),
                        elapsedMillis(validationStartedNanos),
                        policy.policyVersion(),
                        attempt);
                return renderedAnswer;
            } catch (BusinessException exception) {
                log.warn(
                        "question_stage_completed requestId={} externalQuestionId={} runId={} "
                                + "stage=VALIDATION durationMs={} policyVersion={} attempt={} "
                                + "status=FAILED errorCode={}",
                        command.requestId(),
                        safeLogIdentifier(command.externalQuestionId()),
                        run.getId(),
                        elapsedMillis(validationStartedNanos),
                        policy.policyVersion(),
                        attempt,
                        exception.getErrorCode().getCode());
                if (exception.getErrorCode() != ErrorCode.AGENT_502_1
                        || attempt == MAX_ANSWER_GENERATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("답변 생성 시도 한도를 벗어났습니다.");
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String safeLogIdentifier(String value) {
        return value == null
                ? "null"
                : value.replace('\r', '_').replace('\n', '_').replace('\t', '_');
    }

    private void ensureBeforeDeadline(long deadlineNanos) {
        if (Thread.currentThread().isInterrupted()
                || System.nanoTime() - deadlineNanos >= 0) {
            throw deadlineExceeded(null);
        }
    }

    private BusinessException deadlineExceeded(Throwable cause) {
        return new BusinessException(
                ErrorCode.AGENT_504_1,
                "전체 질문 처리 deadline을 초과했습니다.",
                cause);
    }

    // HCX가 계산 입력 fact만 요청하고 비교 대상 공시 기재값(예: facility.equity_ratio)을
    // 빠뜨리는 경우가 있어, 역할 C가 승인한 policy.facts()의 REQUIRED factKey는
    // LOOKUP_FACTS step에 항상 포함되도록 Spring이 직접 보강한다. HCX 응답에 기대지 않는다.
    private QuestionPlan ensureRequiredFactKeysLookedUp(QuestionPlan plan, AnswerPolicy policy) {
        List<String> requiredFactKeys = policy.facts().stream()
                .filter(fact -> fact.necessity() == FactNecessity.REQUIRED)
                .map(fact -> fact.factKey())
                .toList();
        if (requiredFactKeys.isEmpty()) {
            return plan;
        }

        List<PlanStep> augmentedSteps = plan.steps().stream()
                .map(step -> augmentLookupFactsStep(step, requiredFactKeys))
                .toList();
        return new QuestionPlan(
                plan.schemaVersion(),
                plan.companies(),
                plan.time(),
                augmentedSteps,
                plan.warnings());
    }

    private PlanStep augmentLookupFactsStep(PlanStep step, List<String> requiredFactKeys) {
        if (step.toolType() != ToolType.LOOKUP_FACTS
                || !(step.input() instanceof LookupFactsInput lookupFactsInput)) {
            return step;
        }

        LinkedHashSet<String> mergedFactKeys = new LinkedHashSet<>(lookupFactsInput.factKeys());
        mergedFactKeys.addAll(requiredFactKeys);
        if (mergedFactKeys.size() == lookupFactsInput.factKeys().size()) {
            return step;
        }

        return new PlanStep(
                step.stepId(),
                step.toolType(),
                new LookupFactsInput(lookupFactsInput.disclosureIdsFrom(), List.copyOf(mergedFactKeys)),
                step.dependsOn());
    }

    private Optional<PolicyMatch> matchPolicy(QuestionPlan plan, String question) {
        Set<String> requestedSubtypes = plan.steps().stream()
                .filter(step -> step.toolType() == ToolType.SEARCH_DISCLOSURES)
                .map(PlanStep::input)
                .filter(SearchDisclosuresInput.class::isInstance)
                .map(SearchDisclosuresInput.class::cast)
                .flatMap(input -> input.subtypes().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<AnswerPolicy> matches = answerPolicies.stream()
                .filter(policy -> requestedSubtypes.isEmpty()
                        ? policy.goldenCases().stream().anyMatch(goldenCase -> goldenCase.question().equals(question))
                        : requestedSubtypes.contains(policy.disclosureSubtype()))
                .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("검증된 계획에 일치하는 답변 정책이 여러 개입니다.");
        }

        AnswerPolicy policy = matches.getFirst();
        if (policy.goldenCases().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PolicyMatch(policy, selectGoldenCase(policy, plan, question)));
    }

    // 골든 케이스가 여러 회사로 늘어나도 특정 회사 전용 케이스가 다른 회사 질문을
    // 막지 않도록 질문 정확 일치 → 계획에 해소된 회사명 일치 → 첫 케이스 순으로 고른다.
    // criticalErrors 외에는 goldenCase 값을 실제 답변 생성·계산에 쓰지 않으므로
    // (AnswerSafetyValidator 참고) 회사명이 일치하지 않아도 첫 케이스로 안전하게 대체한다.
    private GoldenCase selectGoldenCase(AnswerPolicy policy, QuestionPlan plan, String question) {
        return policy.goldenCases().stream()
                .filter(goldenCase -> goldenCase.question().equals(question))
                .findFirst()
                .or(() -> {
                    Set<String> resolvedCompanyNames = plan.companies().stream()
                            .map(ResolvedCompanyRef::companyName)
                            .collect(java.util.stream.Collectors.toSet());
                    return policy.goldenCases().stream()
                            .filter(goldenCase -> resolvedCompanyNames.contains(goldenCase.companyName()))
                            .findFirst();
                })
                .orElseGet(() -> policy.goldenCases().getFirst());
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
