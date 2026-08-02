package com.foliolens.backend.orchestration;

import org.springframework.stereotype.Service;

import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.question.repository.QuestionRepository;
import com.foliolens.backend.retrieval.DisclosureRetriever;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DisclosureAnswerService {
    public final DisclosureRetriever disclosureRetriever;
    public final QuestionRepository questionRepository;
    public AnswerResult getAnswer(String questionId){
        // 질문 계획 → 검색 → 계산 → 답변 조립
        return new AnswerResult(questionId,question);
    }
}