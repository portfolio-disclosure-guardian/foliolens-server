package com.foliolens.backend.question.service;

import org.springframework.stereotype.Service;

import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.repository.QuestionRunRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionRunService {
    private final QuestionRunRepository questionRunRepository;

    public QuestionRun createQuestionRun(String questionId, String question) {
        QuestionRun questionRun = questionRunRepository.save(
                QuestionRun.builder()
                        .externalQuestionId(questionId)
                        .questionText(question)
                        .build());
        return questionRun;
    }
}
