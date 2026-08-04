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
                List.of(), //검색 내용을 담는 부분. (검색 파트 미완으로 일단 비워놓음)
                List.of(), //실행 추적 내용을 담는 부분. (실행 추적 파트 미완으로 일단 비워놓음)
                "답변 생성 기능이 아직 연결되지 않았습니다."); //검증완료된 AI의 자연어 답변
    }
}