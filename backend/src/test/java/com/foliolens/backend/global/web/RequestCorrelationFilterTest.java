package com.foliolens.backend.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.FutureTask;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void 안전한_request_ID는_MDC와_응답_header에_전파한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "evaluation-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(RequestCorrelationFilter.currentRequestId()).isEqualTo("evaluation-001"));

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME)).isEqualTo("evaluation-001");
    }

    @Test
    void 제어문자가_포함된_request_ID는_새_ID로_교체한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "unsafe request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME))
                .isNotBlank()
                .isNotEqualTo("unsafe request");
    }

    @Test
    void 별도_실행_thread에도_request_ID를_전파할_수_있다() throws Exception {
        FutureTask<String> task = new FutureTask<>(() ->
                RequestCorrelationFilter.withRequestId(
                        "evaluation-002",
                        RequestCorrelationFilter::currentRequestId));
        Thread thread = Thread.ofVirtual().start(task);

        thread.join();
        assertThat(task.get()).isEqualTo("evaluation-002");
    }
}
