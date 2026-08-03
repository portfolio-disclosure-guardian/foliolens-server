package com.foliolens.backend.evaluation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foliolens.backend.evaluation.response.EvaluationAnswerResponse;
import com.foliolens.backend.orchestration.DisclosureAnswerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/")
@RequiredArgsConstructor
public class EvaluationAnswerController {
    private final DisclosureAnswerService evaluationAnswerService;

    @GetMapping("/answer")
    public ResponseEntity<EvaluationAnswerResponse> getAnswer(@RequestParam("question_id") String questionId,
            @RequestParam String question) {
        //Ongoing..
        return ResponseEntity.ok(evaluationAnswerService.getAnswer(questionId,question));
    }
}
