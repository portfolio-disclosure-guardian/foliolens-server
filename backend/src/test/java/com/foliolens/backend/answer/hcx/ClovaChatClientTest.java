package com.foliolens.backend.answer.hcx;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;

class ClovaChatClientTest {

    private static final String BASE_URL = "https://clovastudio.stream.ntruss.com";
    private static final String API_KEY = "test-key";
    private static final String MODEL = "HCX-005";
    private static final String APP_TYPE = "testapp";

    @Test
    void 성공_응답에서_답변_문자열을_추출한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/" + APP_TYPE + "/v3/chat-completions/" + MODEL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-NCP-CLOVASTUDIO-REQUEST-ID", notNullValue()))
                .andRespond(withSuccess("""
                        {
                          "status": { "code": "20000", "message": "OK" },
                          "result": {
                            "message": { "role": "assistant", "content": "테스트 답변" },
                            "finishReason": "stop",
                            "created": 1788288762,
                            "seed": 2121657598,
                            "usage": { "promptTokens": 1, "completionTokens": 1, "totalTokens": 2 }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ClovaChatClient client = new ClovaChatClient(properties(), builder.build());

        String answer = client.chat("system", "user");

        assertEquals("테스트 답변", answer);
        server.verify();
    }

    @Test
    void 실패_status_코드는_BusinessException으로_변환된다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/" + APP_TYPE + "/v3/chat-completions/" + MODEL))
                .andRespond(withSuccess("""
                        {
                          "status": { "code": "40000", "message": "Bad Request" },
                          "result": null
                        }
                        """, MediaType.APPLICATION_JSON));

        ClovaChatClient client = new ClovaChatClient(properties(), builder.build());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client.chat("system", "user"));
        assertEquals(ErrorCode.AGENT_502_1, exception.getErrorCode());
    }

    @Test
    void HTTP_오류_응답은_BusinessException으로_변환된다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/" + APP_TYPE + "/v3/chat-completions/" + MODEL))
                .andRespond(withServerError());

        ClovaChatClient client = new ClovaChatClient(properties(), builder.build());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client.chat("system", "user"));
        assertEquals(ErrorCode.AGENT_502_1, exception.getErrorCode());
    }

    private HcxApiProperties properties() {
        return new HcxApiProperties(true, BASE_URL, API_KEY, MODEL, APP_TYPE, 3000, 30000, 1024, 0.5, 0.8);
    }
}
