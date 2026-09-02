package com.foliolens.backend.evaluation.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foliolens.backend.evaluation.controller.EvaluationAnswerController;
import com.foliolens.backend.orchestration.OrchestrationAnswerService;

@ExtendWith(OutputCaptureExtension.class)
class EvaluationExceptionHandlerTest {

    @Test
    void 예상하지_못한_예외의_메시지와_stack_trace를_노출하지_않는다(CapturedOutput output) throws Exception {
        OrchestrationAnswerService service = mock(OrchestrationAnswerService.class);
        when(service.getAnswer(any()))
                .thenThrow(new RuntimeException("HCX_CLIENT_SECRET=do-not-log"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new EvaluationAnswerController(service))
                .setControllerAdvice(new EvaluationExceptionHandler())
                .build();

        mockMvc.perform(get("/answer")
                        .param("question_id", "Q-001")
                        .param("question", "질문"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));

        assertThat(output)
                .doesNotContain("do-not-log")
                .doesNotContain("RuntimeException:")
                .contains("type=RuntimeException");
    }
}
