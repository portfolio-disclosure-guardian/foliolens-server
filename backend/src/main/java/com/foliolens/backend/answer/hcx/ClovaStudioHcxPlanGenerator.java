package com.foliolens.backend.answer.hcx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.plan.HcxPlanGenerator;
import com.foliolens.backend.question.plan.candidate.QuestionPlanCandidate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "true")
public class ClovaStudioHcxPlanGenerator implements HcxPlanGenerator {

    // ToolType·toolinput 레코드(SearchDisclosuresInput 등)와 정확히 일치해야 QuestionPlanConverter가 파싱할 수 있다.
    private static final String SYSTEM_PROMPT = """
            질문을 읽고 아래 JSON 스키마와 정확히 일치하는 JSON 객체 하나만 출력하세요.
            코드블록, 설명, 인사말 없이 JSON만 출력합니다. 질문에 없는 기업·기간·공시 유형·계산을 추가하지 마세요.

            {
              "schemaVersion": 1,
              "companiesMention": ["질문에 언급된 기업명 문자열"],
              "time": {
                "receiptPeriod": {"from": "YYYY-MM-DD", "to": "YYYY-MM-DD"},
                "reportPeriod": {"from": "YYYY-MM-DD", "to": "YYYY-MM-DD"},
                "asOf": "YYYY-MM-DD"
              },
              "interestCodes": [],
              "steps": [
                {
                  "stepId": "step1",
                  "toolType": "SEARCH_DISCLOSURES",
                  "input": {"categories": ["PERIODIC|MATERIAL|EXCHANGE|OWNERSHIP 중 해당하는 값"], "subtypes": [], "titleTerms": [], "limit": 5},
                  "dependsOn": []
                },
                {
                  "stepId": "step2",
                  "toolType": "LOOKUP_FACTS",
                  "input": {"disclosureIdsFrom": "step1", "factKeys": ["필요한 fact key"]},
                  "dependsOn": ["step1"]
                },
                {
                  "stepId": "step3",
                  "toolType": "CALCULATE",
                  "input": {"factsFrom": "step2", "operation": "DIFFERENCE|CHANGE_RATE|RATIO|SUM|AVERAGE|DATE_DURATION|UNIT_CONVERSION|SHARE_DILUTION 중 하나", "inputBindings": []},
                  "dependsOn": ["step2"]
                }
              ],
              "ambiguities": []
            }

            SEARCH_EVIDENCE와 RESOLVE_DISCLOSURE_HISTORY의 input은 빈 객체 {}입니다.
            계산이 필요 없는 질문이면 CALCULATE step을 넣지 마세요. steps는 필요한 만큼만 사용하세요.
            """;

    private final ClovaChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ClovaStudioHcxPlanGenerator(ClovaChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public QuestionPlanCandidate generatePlan(String question) {
        String content = chatClient.chat(SYSTEM_PROMPT, question);
        try {
            return objectMapper.readValue(extractJsonObject(content), QuestionPlanCandidate.class);
        } catch (JacksonException e) {
            throw new BusinessException(
                    ErrorCode.AGENT_502_1, "HCX 계획 응답이 JSON 형식이 아닙니다: " + e.getMessage(), e);
        }
    }

    // 지시에도 불구하고 모델이 ```json 코드블록으로 감싸는 경우를 대비해 첫 '{'부터 마지막 '}'까지만 취한다.
    private static String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new BusinessException(ErrorCode.AGENT_502_1, "HCX 계획 응답에서 JSON 객체를 찾을 수 없습니다.");
        }
        return content.substring(start, end + 1);
    }
}
