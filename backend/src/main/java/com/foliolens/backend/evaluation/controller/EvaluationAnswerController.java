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

    @GetMapping("/answer")
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
