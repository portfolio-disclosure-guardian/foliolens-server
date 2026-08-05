package com.foliolens.backend.answer;

import java.util.List;
import java.util.UUID;

import com.foliolens.backend.retrieval.RetrievedDocument;

// 검색·계산·답변 생성·검증이 끝난 뒤 공통 답변 엔진(DisclosureAnswerService이 반환하는 내부 최종 결과
public record AnswerResult(
        UUID runId,
        String externalQuestionId,
        String originalQuestion,
        AnswerStatus status,
        List<AnswerClaim> claims,
        // List<CalculationResult> calculations,
        // List<Limitation> limitations,
        ExecutionVersions versions,
        List<RetrievedDocument> usedDocuments, // 최종 답변에 실제 사용한 공시만 포함
        List<String> safeExecutionSummary, // 검색·계산·검증의 공개 가능한 실행 요약
        String renderedAnswer // 검증이 끝난 최종 HCX의 자연어 답변
) {
}
