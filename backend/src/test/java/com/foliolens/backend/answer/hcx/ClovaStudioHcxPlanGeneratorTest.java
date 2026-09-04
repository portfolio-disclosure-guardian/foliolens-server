package com.foliolens.backend.answer.hcx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ClovaStudioHcxPlanGeneratorTest {

    private static final String RAW_PLAN_JSON = """
            {
              "schemaVersion": 1,
              "companiesMention": ["SK하이닉스"],
              "time": {
                "receiptPeriod": {"from": "2024-04-01", "to": "2024-04-30"},
                "reportPeriod": {"from": "2024-04-01", "to": "2024-04-30"},
                "asOf": "2024-04-24"
              },
              "interestCodes": [],
              "steps": [],
              "ambiguities": []
            }
            """;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void JSON_응답을_QuestionPlanCandidate로_파싱한다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), anyString())).thenReturn(RAW_PLAN_JSON);
        ClovaStudioHcxPlanGenerator generator = new ClovaStudioHcxPlanGenerator(chatClient, objectMapper);

        QuestionPlanCandidate candidate = generator.generatePlan("SK하이닉스 질문");

        assertEquals(1L, candidate.schemaVersion());
        assertEquals("SK하이닉스", candidate.companiesMention().getFirst());
        assertEquals("2024-04-24", candidate.time().asOf());
    }

    @Test
    void 시스템_프롬프트에_오늘_날짜와_subtypes_안내를_채워_보낸다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), anyString())).thenReturn(RAW_PLAN_JSON);
        ClovaStudioHcxPlanGenerator generator = new ClovaStudioHcxPlanGenerator(chatClient, objectMapper);

        generator.generatePlan("셀트리온이 발표한 신규시설투자 질문");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(chatClient).chat(systemPrompt.capture(), anyString());
        String prompt = systemPrompt.getValue();
        assertTrue(prompt.contains("오늘 날짜는 " + LocalDate.now() + "입니다."),
                "오늘 날짜가 프롬프트에 주입되어야 한다");
        assertTrue(prompt.contains("신규시설투자등"), "subtypes 지원 값 안내가 프롬프트에 있어야 한다");
    }

    @Test
    void 코드블록으로_감싼_JSON도_파싱한다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), anyString())).thenReturn("```json\n" + RAW_PLAN_JSON + "\n```");
        ClovaStudioHcxPlanGenerator generator = new ClovaStudioHcxPlanGenerator(chatClient, objectMapper);

        QuestionPlanCandidate candidate = generator.generatePlan("SK하이닉스 질문");

        assertEquals("SK하이닉스", candidate.companiesMention().getFirst());
    }

    @Test
    void JSON이_아닌_응답은_BusinessException으로_변환된다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), anyString())).thenReturn("이건 JSON이 아닙니다.");
        ClovaStudioHcxPlanGenerator generator = new ClovaStudioHcxPlanGenerator(chatClient, objectMapper);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> generator.generatePlan("질문"));
        assertEquals(ErrorCode.AGENT_502_1, exception.getErrorCode());
    }

    @Test
    void 깨진_JSON은_BusinessException으로_변환된다() {
        ClovaChatClient chatClient = mock(ClovaChatClient.class);
        when(chatClient.chat(anyString(), anyString())).thenReturn("{ \"schemaVersion\": ");
        ClovaStudioHcxPlanGenerator generator = new ClovaStudioHcxPlanGenerator(chatClient, objectMapper);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> generator.generatePlan("질문"));
        assertEquals(ErrorCode.AGENT_502_1, exception.getErrorCode());
    }
}
