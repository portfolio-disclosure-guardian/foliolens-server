package com.foliolens.backend.evaluation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.orchestration.DisclosureAnswerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/")
@RequiredArgsConstructor
public class EvaluationController {
    private final DisclosureAnswerService disclosureAnswerService;

    @GetMapping("/answer")
    public ResponseEntity<AnswerResult> getAnswer(@RequestParam("question_id") String questionId,
            @RequestParam String question) {
        //Ongoing..
        return ResponseEntity.ok(disclosureAnswerService.getAnswer(questionId,question));
    }
}
