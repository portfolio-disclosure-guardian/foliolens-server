package com.foliolens.backend.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.AnswerOutcomeJudge;
import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.evaluation.controller.EvaluationAnswerController;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.RequestChannel;
import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.service.QuestionRunService;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

class OrchestrationAnswerServiceTest {

    private final GoldenCase goldenCase = GoldFacility001Fixture.policy().goldenCase();
    private final UUID runId = UUID.randomUUID();

    private QuestionRunService questionRunService;
    private HcxAnswerGenerator hcxAnswerGenerator;
    private OrchestrationAnswerService service;

    @BeforeEach
    void setUp() {
        questionRunService = mock(QuestionRunService.class);
        QuestionRun run = mock(QuestionRun.class);
        when(run.getId()).thenReturn(runId);
        when(run.getExternalQuestionId()).thenReturn(goldenCase.goldenCaseId());
        when(run.getQuestionText()).thenReturn(goldenCase.question());
        when(questionRunService.createQuestionRun(goldenCase.goldenCaseId(), goldenCase.question()))
                .thenReturn(run);
        hcxAnswerGenerator = mock(HcxAnswerGenerator.class);
        when(hcxAnswerGenerator.generateAnswer(
                eq(goldenCase.question()), any(), any(), any(), eq(AnswerOutcome.COMPLETED)))
                .thenReturn(goldenCase.expectedAnswer());

        service = new OrchestrationAnswerService(
                questionRunService,
                FakeDisclosureRetriever.complete(),
                new FakeDisclosureCalculator(),
                new AnswerOutcomeJudge(),
                hcxAnswerGenerator);
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
                RequestChannel.EVALUATION);
    }
}
