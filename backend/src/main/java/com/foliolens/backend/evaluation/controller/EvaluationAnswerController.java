package com.foliolens.backend.evaluation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foliolens.backend.evaluation.response.EvaluationAnswerResponse;
import com.foliolens.backend.orchestration.DisclosureAnswerService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EvaluationAnswerController {
    private final DisclosureAnswerService evaluationAnswerService;

    @GetMapping("/answer")
    public ResponseEntity<EvaluationAnswerResponse> getAnswer(@RequestParam("question_id") @NotBlank String questionId,
            @RequestParam("question") @NotBlank String questionText) {
        // Ongoing..
        return ResponseEntity.ok(evaluationAnswerService.getAnswer(questionId, questionText));
    }
}
