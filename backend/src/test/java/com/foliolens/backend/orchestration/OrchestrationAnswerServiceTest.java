package com.foliolens.backend.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerOutcomeJudge;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.AnswerSafetyValidator;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.repository.CompanyRepository;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.evaluation.controller.EvaluationAnswerController;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.FinanceDomainAnswerPolicies;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.RequestChannel;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.plan.HcxPlanGenerator;
import com.foliolens.backend.question.plan.QuestionPlanConverter;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.service.QuestionRunService;
import com.foliolens.backend.retrieval.DisclosureRetriever;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.RetrievedDocument;
import com.foliolens.backend.retrieval.RetrievedEvidence;
import com.foliolens.backend.retrieval.RetrievedFact;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class OrchestrationAnswerServiceTest {

    private final GoldenCase goldenCase = GoldFacility001Fixture.policy().goldenCases().getFirst();
    private final UUID runId = UUID.randomUUID();
    private final String requestId = "request-001";

    private QuestionRunService questionRunService;
    private HcxAnswerGenerator hcxAnswerGenerator;
    private HcxPlanGenerator hcxPlanGenerator;
    private QuestionPlanConverter questionPlanConverter;
    private OrchestrationAnswerService service;
    private QuestionRun run;

    @BeforeEach
    void setUp() {
        questionRunService = mock(QuestionRunService.class);
        run = mock(QuestionRun.class);
        when(run.getId()).thenReturn(runId);
        when(run.getExternalQuestionId()).thenReturn(goldenCase.goldenCaseId());
        when(run.getQuestionText()).thenReturn(goldenCase.question());
        when(questionRunService.createQuestionRun(
                anyString(),
                eq(goldenCase.goldenCaseId()),
                eq(goldenCase.question()),
                eq(RequestChannel.EVALUATION)))
                .thenReturn(run);
        hcxAnswerGenerator = mock(HcxAnswerGenerator.class);
        when(hcxAnswerGenerator.generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED)))
                .thenReturn(goldenCase.expectedAnswer());

        hcxPlanGenerator = mock(HcxPlanGenerator.class);
        when(hcxPlanGenerator.generatePlan(eq(goldenCase.question())))
                .thenReturn(GoldFacility001Fixture.questionPlanCandidate());
        questionPlanConverter = new QuestionPlanConverter(
                companyRepositoryResolving(goldenCase.companyName()), JsonMapper.builder().build(), 1, 50);

        service = newService(false);
    }

    private static CompanyRepository companyRepositoryResolving(String companyName) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(UUID.randomUUID());
        when(company.getCorpName()).thenReturn(companyName);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        when(companyRepository.findByCorpName(companyName)).thenReturn(List.of(company));
        return companyRepository;
    }

    private OrchestrationAnswerService newService(boolean requireApprovedGoldenCase) {
        return newService(
                requireApprovedGoldenCase,
                FakeDisclosureRetriever.complete(),
                30_000);
    }

    private OrchestrationAnswerService newService(
            boolean requireApprovedGoldenCase,
            DisclosureRetriever disclosureRetriever,
            long deadlineMillis) {
        return new OrchestrationAnswerService(
                questionRunService,
                disclosureRetriever,
                new FakeDisclosureCalculator(),
                new AnswerOutcomeJudge(),
                new com.foliolens.backend.answer.AnswerReferenceValidator(),
                new AnswerSafetyValidator(),
                hcxPlanGenerator,
                questionPlanConverter,
                hcxAnswerGenerator,
                List.of(FinanceDomainAnswerPolicies.type08(), GoldFacility001Fixture.policy()),
                requireApprovedGoldenCase,
                deadlineMillis,
                "contest-test-v1",
                "HCX-TEST");
    }

    @Test
    void 골든_질문은_완료된_AnswerResult를_반환한다(CapturedOutput output) {
        AnswerResult result = service.getAnswer(command());

        assertEquals(runId, result.runId());
        assertEquals(goldenCase.goldenCaseId(), result.externalQuestionId());
        assertEquals(goldenCase.question(), result.originalQuestion());
        assertEquals(AnswerOutcome.COMPLETED, result.outcome());
        assertFalse(result.claims().isEmpty());
        assertEquals(1, result.calculations().size());
        assertEquals(CalculationVerdict.MATCH, result.calculations().getFirst().verdict());
        assertEquals(goldenCase.receiptNo(), result.usedEvidences().getFirst().documentId());
        assertEquals(goldenCase.expectedAnswer(), result.renderedAnswer());
        verify(hcxAnswerGenerator).generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED));
        verify(questionRunService).recordQuestionPlan(eq(run), any(QuestionPlan.class));
        verify(questionRunService).startQuestionRun(run);
        verify(questionRunService).completeQuestionRun(run, goldenCase.expectedAnswer());
        assertThat(output)
                .contains("requestId=" + requestId)
                .contains("externalQuestionId=" + goldenCase.goldenCaseId())
                .contains("runId=" + runId)
                .contains("stage=PLANNING")
                .contains("durationMs=")
                .contains("datasetVersion=contest-test-v1")
                .contains("planSchemaVersion=1")
                .contains("stage=RETRIEVAL")
                .contains("retrievalVersion=fake-1.0")
                .contains("stage=CALCULATION")
                .contains("policyVersion=" + GoldFacility001Fixture.policy().policyVersion())
                .contains("stage=ANSWER_GENERATION")
                .contains("modelVersion=HCX-TEST")
                .contains("stage=VALIDATION")
                .contains("question_run_completed")
                .doesNotContain(goldenCase.question())
                .doesNotContain(goldenCase.expectedAnswer());
    }

    @Test
    void 답변_생성_실패는_run과_안전한_추적로그에_오류코드를_기록한다(CapturedOutput output) {
        BusinessException failure = new BusinessException(ErrorCode.AGENT_502_1);
        doThrow(failure).when(hcxAnswerGenerator).generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED));

        assertEquals(failure, assertThrows(BusinessException.class, () -> service.getAnswer(command())));
        verify(questionRunService).failQuestionRun(run, ErrorCode.AGENT_502_1);
        assertThat(output)
                .contains("question_run_failed")
                .contains("requestId=" + requestId)
                .contains("externalQuestionId=" + goldenCase.goldenCaseId())
                .contains("runId=" + runId)
                .contains("errorCode=AGENT_502_1")
                .doesNotContain(goldenCase.question());
    }

    @Test
    void 승인_강제를_켜면_검토중인_골든_케이스는_placeholder로_처리한다() {
        service = newService(true);

        AnswerResult result = service.getAnswer(command());

        assertEquals(AnswerOutcome.UNANSWERABLE, result.outcome());
        assertEquals("답변 생성 기능이 아직 연결되지 않았습니다.", result.renderedAnswer());
        verifyNoInteractions(hcxAnswerGenerator);
        verify(questionRunService).completeQuestionRun(run, result.renderedAnswer());
    }

    @Test
    void 안전_검증에_실패한_답변은_run을_FAILED로_기록한다() {
        String forbiddenExpression = GoldFacility001Fixture.policy().forbiddenExpressions().getFirst();
        when(hcxAnswerGenerator.generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED)))
                .thenReturn("검증된 사실 뒤에 " + forbiddenExpression + " 표현이 포함됐습니다.");

        BusinessException failure = assertThrows(BusinessException.class, () -> service.getAnswer(command()));

        assertEquals(ErrorCode.AGENT_502_1, failure.getErrorCode());
        verify(hcxAnswerGenerator, times(2)).generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED));
        verify(questionRunService).failQuestionRun(run, ErrorCode.AGENT_502_1);
    }

    @Test
    void 첫_답변의_안전_검증이_실패하면_한_번만_재생성한다() {
        String forbiddenExpression = GoldFacility001Fixture.policy().forbiddenExpressions().getFirst();
        when(hcxAnswerGenerator.generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED)))
                .thenReturn("검증 실패: " + forbiddenExpression)
                .thenReturn(goldenCase.expectedAnswer());

        AnswerResult result = service.getAnswer(command());

        assertEquals(goldenCase.expectedAnswer(), result.renderedAnswer());
        verify(hcxAnswerGenerator, times(2)).generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED));
        verify(questionRunService).completeQuestionRun(run, goldenCase.expectedAnswer());
    }

    @Test
    void 미검증_후보는_HCX_입력과_평가_응답에_노출하지_않는다() {
        RetrievalResult verified = FakeDisclosureRetriever.complete()
                .retrieve(GoldFacility001Fixture.questionPlan());
        RetrievedDocument candidateDocument = new RetrievedDocument(
                "DOC-CANDIDATE",
                "후보 기업",
                "999999",
                "exchange",
                "미검증 후보 공시",
                verified.documents().getFirst().submittedAt(),
                "후보 구간",
                "미검증 후보 원문",
                0.9);
        RetrievedEvidence source = verified.evidences().getFirst();
        RetrievedEvidence candidateEvidence = new RetrievedEvidence(
                "EVD-CANDIDATE",
                source.disclosureId(),
                candidateDocument.documentId(),
                source.documentRole(),
                "candidate-section",
                source.blockType(),
                "미검증 후보 근거",
                0.9,
                EvidenceStatus.CANDIDATE);
        RetrievedFact sourceFact = verified.facts().getFirst();
        RetrievedFact candidateFact = new RetrievedFact(
                "FACT-CANDIDATE",
                sourceFact.disclosureId(),
                "facility.candidate",
                sourceFact.valueType(),
                "미검증 후보 값",
                "미검증 후보 값",
                sourceFact.unit(),
                sourceFact.periodStart(),
                sourceFact.periodEnd(),
                List.of(candidateEvidence.evidenceId()),
                FactValidationStatus.UNVALIDATED);
        RetrievalResult mixed = new RetrievalResult(
                java.util.stream.Stream.concat(
                        verified.documents().stream(), java.util.stream.Stream.of(candidateDocument)).toList(),
                java.util.stream.Stream.concat(
                        verified.facts().stream(), java.util.stream.Stream.of(candidateFact)).toList(),
                java.util.stream.Stream.concat(
                        verified.evidences().stream(), java.util.stream.Stream.of(candidateEvidence)).toList(),
                verified.history(),
                verified.executedSteps(),
                verified.missingFactKeys(),
                verified.coverage(),
                List.of("미검증 후보 경고"),
                verified.retrievalVersion());
        service = newService(false, plan -> mixed, 30_000);

        AnswerResult result = service.getAnswer(command());

        ArgumentCaptor<RetrievalResult> hcxInput = ArgumentCaptor.forClass(RetrievalResult.class);
        verify(hcxAnswerGenerator).generateAnswer(
                eq(goldenCase.question()), any(), hcxInput.capture(), any(), eq(AnswerOutcome.COMPLETED));
        assertThat(hcxInput.getValue().facts()).doesNotContain(candidateFact);
        assertThat(hcxInput.getValue().evidences()).doesNotContain(candidateEvidence);
        assertThat(hcxInput.getValue().documents()).doesNotContain(candidateDocument);
        assertThat(hcxInput.getValue().warnings()).isEmpty();
        assertThat(result.usedEvidences()).doesNotContain(candidateDocument);
    }

    @Test
    void 전체_deadline이_지나면_재시도하지_않고_run을_FAILED로_기록한다() {
        DisclosureRetriever slowRetriever = plan -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return FakeDisclosureRetriever.complete().retrieve(plan);
        };
        service = newService(false, slowRetriever, 50);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.getAnswer(command()));

        assertEquals(ErrorCode.AGENT_504_1, failure.getErrorCode());
        verifyNoInteractions(hcxAnswerGenerator);
        verify(questionRunService).failQuestionRun(run, ErrorCode.AGENT_504_1);
    }

    @Test
    void GET_answer는_골든_질문의_근거와_답변을_반환한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new EvaluationAnswerController(service))
                .build();

        mockMvc.perform(get("/answer")
                        .param("question_id", goldenCase.goldenCaseId())
                        .param("question", goldenCase.question()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question_id").value(goldenCase.goldenCaseId()))
                .andExpect(jsonPath("$.question").value(goldenCase.question()))
                .andExpect(jsonPath("$.retrieved_context[0].receipt_no").value(goldenCase.receiptNo()))
                .andExpect(jsonPath("$.think_trace").isNotEmpty())
                .andExpect(jsonPath("$.answer").value(goldenCase.expectedAnswer()));
    }

    private AnswerQuestionCommand command() {
        return new AnswerQuestionCommand(
                goldenCase.goldenCaseId(),
                goldenCase.question(),
                RequestChannel.EVALUATION,
                requestId);
    }
}
