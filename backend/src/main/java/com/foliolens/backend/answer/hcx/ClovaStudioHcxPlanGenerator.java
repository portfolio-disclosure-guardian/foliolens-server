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
                  "input": {"categories": ["categories는 아래 4개 값 중에서만 선택"], "subtypes": [], "titleTerms": [], "limit": 5},
                  "dependsOn": []
                },
                {
                  "stepId": "step2",
                  "toolType": "LOOKUP_FACTS",
                  "input": {"disclosureIdsFrom": "step1", "factKeys": ["아래 factKey 목록에 있는 값만"]},
                  "dependsOn": ["step1"]
                },
                {
                  "stepId": "step3",
                  "toolType": "CALCULATE",
                  "input": {"factsFrom": "step2", "operation": "DIFFERENCE|CHANGE_RATE|RATIO|SUM|AVERAGE|DATE_DURATION|UNIT_CONVERSION|SHARE_DILUTION 중 하나", "inputBindings": ["step2 factKeys에 있는 계산 입력 fact key 문자열만, 예: facility.amount, facility.equity_amount"]},
                  "dependsOn": ["step2"]
                }
              ],
              "ambiguities": []
            }

            categories는 반드시 아래 4개 값 중 하나 이상이어야 하며, 이 4개 외의 다른 문자열은
            어떤 경우에도 만들어내지 마세요. 의미가 비슷해 보여도 새 값을 지어내면 안 됩니다.
            - PERIODIC: 사업보고서·반기보고서·분기보고서
            - MATERIAL: 주요사항보고서(예: 투자판단관련주요경영사항, 자산양수도, 합병 등)
            - EXCHANGE: 거래소 공시(예: 신규시설투자등, 단일판매공급계약체결, 자기주식취득 등 -
              "투자", "시설", "계약" 같은 단어가 있어도 주요사항보고서가 아니라 거래소 공시인 경우가 많음)
            - OWNERSHIP: 주식 등의 대량보유상황보고서

            예: "SK하이닉스의 신규시설투자 공시" → categories는 ["MATERIAL"]이 아니라 ["EXCHANGE"]입니다.

            factKeys는 "투자금액", "목적"처럼 질문의 표현을 그대로 쓰지 말고, 아래 factKey
            목록의 문자열을 정확히 그대로 사용하세요. 목록에 없는 값은 만들어내지 마세요.
            현재는 신규시설투자 관련 질문만 지원하며 factKey는 다음과 같습니다:
            - facility.target: 투자대상
            - facility.amount: 투자금액
            - facility.equity_amount: 자기자본
            - facility.equity_ratio: 자기자본대비 비율
            - facility.purpose: 투자목적
            - facility.start_date: 투자기간 시작일
            - facility.end_date: 투자기간 종료일
            - facility.decision_date: 이사회결의일(결정일)
            - facility.type: 투자구분

            inputBindings는 문자열 배열입니다. {"sourceFactKey": ..., "targetFactKey": ...} 같은 객체나
            [["a","b"]] 같은 중첩 배열을 만들지 말고, 계산에 쓰는 factKey 문자열만 나열하세요.
            예: 투자금액을 자기자본으로 나누는 비율 계산이면 "inputBindings": ["facility.amount", "facility.equity_amount"]입니다.

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
