package com.foliolens.backend.answer.hcx;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.calculation.CalculationResult;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.global.web.RequestCorrelationFilter;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.CalculationPolicy;
import com.foliolens.backend.policy.FactPolicy;
import com.foliolens.backend.retrieval.RetrievalResult;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.ObjectMapper;

// 실호출로 검증한 실제 계약: POST /{appType}/v3/chat-completions/{model} (appType은 testapp/serviceapp),
// Bearer 헤더 하나, content는 타입 배열. 서비스 키로 전환하면 appType을 serviceapp으로 바꾼다.
@Component
@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "true")
public class ClovaStudioHcxAnswerGenerator implements HcxAnswerGenerator {

    private static final String REQUEST_ID_HEADER = "X-NCP-CLOVASTUDIO-REQUEST-ID";
    private static final String SUCCESS_STATUS_CODE = "20000";
    // CLOVA Studio 문서 예시값: 0은 top-k 샘플링 비활성화를 뜻한다. 설정으로 노출할 필요가 생기면 그때 옮긴다.
    private static final int TOP_K_DISABLED = 0;
    private static final String SYSTEM_PROMPT_PREFIX =
            "다음은 검증된 공시 근거와 계산 결과입니다. 이 안의 사실과 수치만 사용해 한국어로 답변하세요. "
                    + "제공되지 않은 사실을 새로 만들거나 금액·날짜·비율을 다시 계산하지 마세요.\n\n";

    private final RestClient restClient;
    private final HcxApiProperties properties;
    private final ObjectMapper objectMapper;

    public ClovaStudioHcxAnswerGenerator(HcxApiProperties properties, RestClient hcxRestClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = hcxRestClient;
    }

    @Override
    public String generateAnswer(
            String question,
            AnswerPolicy policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome) {
        ChatRequest request = buildRequest(question, policy, retrieval, calculation, outcome);
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/{appType}/v3/chat-completions/{model}", properties.appType(), properties.model())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(REQUEST_ID_HEADER, RequestCorrelationFilter.currentRequestId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException(
                    ErrorCode.AGENT_502_1, "HCX 호출이 실패했습니다: HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AGENT_504_1, "HCX 호출이 시간 내에 끝나지 않았습니다.", e);
        }

        if (response == null || response.status() == null
                || !SUCCESS_STATUS_CODE.equals(response.status().code())) {
            String statusCode = response == null || response.status() == null
                    ? "unknown" : response.status().code();
            throw new BusinessException(ErrorCode.AGENT_502_1, "HCX가 실패 상태를 반환했습니다: " + statusCode);
        }
        if (response.result() == null || response.result().message() == null) {
            throw new BusinessException(ErrorCode.AGENT_502_1, "HCX 응답에 답변 내용이 없습니다.");
        }
        return response.result().message().content();
    }

    private ChatRequest buildRequest(
            String question,
            AnswerPolicy policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome) {
        String systemContent = SYSTEM_PROMPT_PREFIX
                + objectMapper.writeValueAsString(new PromptContext(
                        PolicyPromptView.from(policy), retrieval, calculation, outcome));
        return new ChatRequest(
                List.of(
                        new ChatMessage("system", List.of(new ContentPart("text", systemContent))),
                        new ChatMessage("user", List.of(new ContentPart("text", question)))),
                properties.topP(),
                TOP_K_DISABLED,
                properties.maxTokens(),
                properties.temperature());
    }

    // goldenCase에는 평가 정답(expectedAnswer)이 들어 있으므로 프롬프트에 그대로 직렬화하지 않는다.
    private record PolicyPromptView(
            String disclosureSubtype,
            List<FactPolicy> facts,
            CalculationPolicy calculation,
            List<String> allowedExpressions,
            List<String> forbiddenExpressions) {
        static PolicyPromptView from(AnswerPolicy policy) {
            return new PolicyPromptView(
                    policy.disclosureSubtype(),
                    policy.facts(),
                    policy.calculation(),
                    policy.allowedExpressions(),
                    policy.forbiddenExpressions());
        }
    }

    private record PromptContext(
            PolicyPromptView policy,
            RetrievalResult retrieval,
            CalculationResult calculation,
            AnswerOutcome outcome) {
    }

    private record ChatRequest(List<ChatMessage> messages, double topP, int topK, int maxTokens, double temperature) {
    }

    private record ChatMessage(String role, List<ContentPart> content) {
    }

    private record ContentPart(String type, String text) {
    }

    // 실호출 응답에는 여기 없는 필드(created, seed 등)가 더 있을 수 있어 알 수 없는 필드는 무시한다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(Status status, Result result) {
    }

    private record Status(String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Result(ResultMessage message, String finishReason, Usage usage) {
    }

    private record ResultMessage(String role, String content) {
    }

    private record Usage(int promptTokens, int completionTokens, int totalTokens) {
    }
}
