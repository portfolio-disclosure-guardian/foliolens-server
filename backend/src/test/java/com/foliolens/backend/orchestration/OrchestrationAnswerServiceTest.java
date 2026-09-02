package com.foliolens.backend.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerOutcomeJudge;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.AnswerSafetyValidator;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.evaluation.controller.EvaluationAnswerController;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.FinanceDomainAnswerPolicies;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.RequestChannel;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.service.QuestionRunService;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

class OrchestrationAnswerServiceTest {

    private final GoldenCase goldenCase = GoldFacility001Fixture.policy().goldenCases().getFirst();
    private final UUID runId = UUID.randomUUID();
    private final String requestId = "request-001";

    private QuestionRunService questionRunService;
    private HcxAnswerGenerator hcxAnswerGenerator;
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

        service = newService(false);
    }

    private OrchestrationAnswerService newService(boolean requireApprovedGoldenCase) {
        return new OrchestrationAnswerService(
                questionRunService,
                FakeDisclosureRetriever.complete(),
                new FakeDisclosureCalculator(),
                new AnswerOutcomeJudge(),
                new com.foliolens.backend.answer.AnswerReferenceValidator(),
                new AnswerSafetyValidator(),
                hcxAnswerGenerator,
                List.of(FinanceDomainAnswerPolicies.type08(), GoldFacility001Fixture.policy()),
                requireApprovedGoldenCase);
    }

    @Test
    void 골든_질문은_완료된_AnswerResult를_반환한다() {
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
        verify(questionRunService).startQuestionRun(run);
        verify(questionRunService).completeQuestionRun(run, goldenCase.expectedAnswer());
    }

    @Test
    void 답변_생성_실패는_run을_FAILED로_기록한다() {
        BusinessException failure = new BusinessException(ErrorCode.AGENT_502_1);
        doThrow(failure).when(hcxAnswerGenerator).generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED));

        assertEquals(failure, assertThrows(BusinessException.class, () -> service.getAnswer(command())));
        verify(questionRunService).failQuestionRun(run, ErrorCode.AGENT_502_1);
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
        verify(questionRunService).failQuestionRun(run, ErrorCode.AGENT_502_1);
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
