package com.foliolens.backend.answer.hcx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.calculation.CalculationCommand;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.calculation.ComparisonBasis;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldFacility001Fixture;
import com.foliolens.backend.retrieval.RetrievalResult;
import com.foliolens.backend.retrieval.fake.FakeDisclosureRetriever;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ClovaStudioHcxAnswerGeneratorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AnswerPolicy policy = GoldFacility001Fixture.policy();
    private final RetrievalResult retrieval =
            FakeDisclosureRetriever.complete().retrieve(GoldFacility001Fixture.questionPlan());
    private final CalculationResult calculation = new FakeDisclosureCalculator().calculate(
            new CalculationCommand(policy.calculation().operation(), new ComparisonBasis(true, true, true, true)),
            retrieval.facts());

    @Test
    void chatClient_결과를_그대로_답변으로_반환한다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), eq(policy.goldenCases().getFirst().question()))).thenReturn("최종 답변");
        ClovaStudioHcxAnswerGenerator generator = new ClovaStudioHcxAnswerGenerator(chatClient, objectMapper);

        String answer = generator.generateAnswer(
                policy.goldenCases().getFirst().question(), policy, retrieval, calculation, AnswerOutcome.COMPLETED);

        assertEquals("최종 답변", answer);
    }

    @Test
    void system_프롬프트에_goldenCase_정답은_포함하지_않는다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), anyString())).thenReturn("답변");
        ClovaStudioHcxAnswerGenerator generator = new ClovaStudioHcxAnswerGenerator(chatClient, objectMapper);

        generator.generateAnswer(
                policy.goldenCases().getFirst().question(), policy, retrieval, calculation, AnswerOutcome.COMPLETED);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient).chat(systemCaptor.capture(), eq(policy.goldenCases().getFirst().question()));
        String systemContent = systemCaptor.getValue();
        assertTrue(systemContent.contains(policy.disclosureSubtype()));
        assertTrue(systemContent.contains(retrieval.documents().getFirst().documentId()));
        assertFalse(systemContent.contains(policy.goldenCases().getFirst().expectedAnswer()));
    }
}
