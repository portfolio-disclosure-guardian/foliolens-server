package com.foliolens.backend.evaluation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.evaluation.response.EvaluationAnswerResponse;
import com.foliolens.backend.global.web.RequestCorrelationFilter;
import com.foliolens.backend.orchestration.OrchestrationAnswerService;
import com.foliolens.backend.question.AnswerQuestionCommand;
import com.foliolens.backend.question.RequestChannel;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EvaluationAnswerController {
    private final OrchestrationAnswerService disclosureAnswerService;

    // Content-Type에 charset을 명시하지 않으면 일부 HTTP 클라이언트가 비ASCII 응답을 UTF-8이 아닌
    // 다른 인코딩(예: ISO-8859-1)으로 잘못 디코딩한다. 평가 질의가 전부 한국어이므로 명시적으로 고정한다.
    @GetMapping(value = "/answer", produces = "application/json;charset=UTF-8")
    public ResponseEntity<EvaluationAnswerResponse> getAnswer(@RequestParam("question_id") @NotBlank String questionId,
            @RequestParam("question") @NotBlank String questionText) {
        var command = new AnswerQuestionCommand(
                questionId,
                questionText,
                RequestChannel.EVALUATION,
                RequestCorrelationFilter.currentRequestId());
        AnswerResult result = disclosureAnswerService.getAnswer(command);
        return ResponseEntity.ok(EvaluationAnswerResponse.from(result));
    }
}
