package com.foliolens.backend.answer.hcx;

import java.net.SocketTimeoutException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.global.web.RequestCorrelationFilter;

// 실호출로 검증한 실제 계약: POST /{appType}/v3/chat-completions/{model} (appType은 testapp/serviceapp),
// Bearer 헤더 하나, content는 타입 배열. 서비스 키로 전환하면 appType을 serviceapp으로 바꾼다.
// 계획·답변 두 HCX 호출이 이 envelope을 공유하므로 여기 한 곳에서만 관리한다.
@Component
@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "true")
public class ClovaChatClient {

    private static final String REQUEST_ID_HEADER = "X-NCP-CLOVASTUDIO-REQUEST-ID";
    private static final String SUCCESS_STATUS_CODE = "20000";
    // CLOVA Studio 문서 예시값: 0은 top-k 샘플링 비활성화를 뜻한다. 설정으로 노출할 필요가 생기면 그때 옮긴다.
    private static final int TOP_K_DISABLED = 0;

    private final RestClient restClient;
    private final HcxApiProperties properties;

    public ClovaChatClient(HcxApiProperties properties, RestClient hcxRestClient) {
        this.properties = properties;
        this.restClient = hcxRestClient;
    }

    public String chat(String systemContent, String userContent) {
        ChatRequest request = new ChatRequest(
                List.of(
                        new ChatMessage("system", List.of(new ContentPart("text", systemContent))),
                        new ChatMessage("user", List.of(new ContentPart("text", userContent)))),
                properties.topP(),
                TOP_K_DISABLED,
                properties.maxTokens(),
                properties.temperature());

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
        } catch (RestClientException e) {
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new BusinessException(
                        ErrorCode.AGENT_504_1,
                        "HCX 호출이 시간 내에 끝나지 않았습니다.",
                        e);
            }
            throw new BusinessException(
                    ErrorCode.AGENT_502_1,
                    "HCX 호출을 처리하지 못했습니다.",
                    e);
        }

        if (response == null || response.status() == null
                || !SUCCESS_STATUS_CODE.equals(response.status().code())) {
            String statusCode = response == null || response.status() == null
                    ? "unknown" : response.status().code();
            throw new BusinessException(ErrorCode.AGENT_502_1, "HCX가 실패 상태를 반환했습니다: " + statusCode);
        }
        if (response.result() == null || response.result().message() == null) {
            throw new BusinessException(ErrorCode.AGENT_502_1, "HCX 응답에 내용이 없습니다.");
        }
        return response.result().message().content();
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (causeType.isInstance(cause)) {
                return true;
            }
        }
        return false;
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
