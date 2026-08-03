package com.foliolens.backend.orchestration;

import java.util.List;

import org.springframework.stereotype.Service;

import com.foliolens.backend.evaluation.response.EvaluationAnswerResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DisclosureAnswerService {
    // public final DisclosureRetriever disclosureRetriever;
    // public final QuestionRepository questionRepository;

    public EvaluationAnswerResponse getAnswer(String questionId, String question) {
        // 질문 계획 → 검색 → 계산 → 답변 조립
        return new EvaluationAnswerResponse(
                questionId,
                question,
                List.of(),
                List.of(),
                "답변 생성 기능이 아직 연결되지 않았습니다.");
    }
}