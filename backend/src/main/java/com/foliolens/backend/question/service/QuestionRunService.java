package com.foliolens.backend.question.service;

import org.springframework.stereotype.Service;

import com.foliolens.backend.question.entity.QuestionRun;
import com.foliolens.backend.question.repository.QuestionRunRepository;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.RequestChannel;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class QuestionRunService {
    private final QuestionRunRepository questionRunRepository;
    private final ObjectMapper objectMapper;

    public QuestionRun createQuestionRun(
            String requestId,
            String questionId,
            String question,
            RequestChannel channel) {
        QuestionRun questionRun = questionRunRepository.save(
                QuestionRun.builder()
                        .requestId(requestId)
                        .externalQuestionId(questionId)
                        .questionText(question)
                        .channel(channel)
                        .build());
        return questionRun;
    }

    public QuestionRun startQuestionRun(QuestionRun questionRun) {
        questionRun.start();
        return questionRunRepository.save(questionRun);
    }

    public QuestionRun recordQuestionPlan(
            QuestionRun questionRun,
            QuestionPlan questionPlan) {
        questionRun.recordQueryPlan(objectMapper.valueToTree(questionPlan));
        return questionRunRepository.save(questionRun);
    }

    public QuestionRun completeQuestionRun(QuestionRun questionRun, String answerText) {
        questionRun.complete(answerText);
        return questionRunRepository.save(questionRun);
    }

    public QuestionRun failQuestionRun(QuestionRun questionRun, ErrorCode errorCode) {
        questionRun.fail(errorCode);
        return questionRunRepository.save(questionRun);
    }

    public QuestionRun getQuestionRunByExternalQuestionId(String questionId) {
        return questionRunRepository.findByExternalQuestionId(questionId)
                .orElseThrow(() -> new RuntimeException("QuestionRun not found for questionId: " + questionId));
    }
}
